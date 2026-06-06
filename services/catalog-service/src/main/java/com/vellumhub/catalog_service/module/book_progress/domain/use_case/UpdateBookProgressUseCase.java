package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.event.UpdateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;

@Component
public class UpdateBookProgressUseCase {

     private final BookProgressRepository bookProgressRepository;
     private final VellumHubMetrics metrics;

     public UpdateBookProgressUseCase(BookProgressRepository bookProgressRepository, VellumHubMetrics metrics) {
          this.bookProgressRepository = bookProgressRepository;
          this.metrics = metrics;
     }

     public UpdateBookProgressEvent execute(UpdateBookProgressCommand command){
          BookProgress bookProgress = bookProgressRepository.findByUserIdAndBookId(command.userId(), command.bookId())
                  .orElseThrow(BookProgressNotFoundException::new);

          int currentPage = bookProgress.getCurrentPage();

          if(!bookProgress.getReadingStatus().equals(ReadingStatus.READING)){
               throw new BookIsNotBeingReadException();
          }

          bookProgress.update(bookProgress.getReadingStatus(), command.currentPage());

          bookProgressRepository.save(bookProgress);
          metrics.recordBusinessCounter(VellumHubMetrics.READING_PROGRESS_UPDATED, "reading_progress_update", "success");

          return new UpdateBookProgressEvent(
                  bookProgress.getId(),
                  command.userId(),
                  command.bookId(),
                  bookProgress.getReadingStatus().name(),
                  currentPage,
                  bookProgress.getCurrentPage()
          );
     }

}
