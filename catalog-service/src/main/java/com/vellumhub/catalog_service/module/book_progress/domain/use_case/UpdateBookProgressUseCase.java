package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.event.UpdateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

@Component
public class UpdateBookProgressUseCase {

     private final BookProgressRepository bookProgressRepository;

     public UpdateBookProgressUseCase(BookProgressRepository bookProgressRepository) {
          this.bookProgressRepository = bookProgressRepository;
     }

     public UpdateBookProgressEvent execute(UpdateBookProgressCommand command){
          BookProgress bookProgress = bookProgressRepository.findByUserIdAndBookId(command.userId(), command.bookId())
                  .orElseThrow(BookProgressNotFoundException::new);

          int currentPage = bookProgress.getCurrentPage();

          if(!bookProgress.getReadingStatus().equals(ReadingStatus.READING)){
               throw new BookIsNotBeingReadException();
          }

          bookProgress.defineProgress(bookProgress.getReadingStatus(), command.currentPage());

          bookProgressRepository.save(bookProgress);

          return new UpdateBookProgressEvent(
                  command.userId(),
                  command.bookId(),
                  bookProgress.getReadingStatus().name(),
                  currentPage,
                  bookProgress.getCurrentPage()
          );
     }

}
