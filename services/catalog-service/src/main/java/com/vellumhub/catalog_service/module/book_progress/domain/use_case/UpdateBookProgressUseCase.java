package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.readingprogress.UpdateBookProgressEvent;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

          // No state transition means no revision increment and no duplicate integration fact.
          if (currentPage == command.currentPage()) {
               return null;
          }
          bookProgress.update(bookProgress.getReadingStatus(), command.currentPage());

          BookProgress persisted = bookProgressRepository.save(bookProgress);
          metrics.recordBusinessCounter(VellumHubMetrics.READING_PROGRESS_UPDATED, "reading_progress_update", "success");

          return new UpdateBookProgressEvent(
                UUID.randomUUID(),
                Instant.now().truncatedTo(ChronoUnit.MICROS),
                  persisted.getId(),
                  command.userId(),
                  command.bookId(),
                  bookProgress.getReadingStatus().name(),
                  currentPage,
                  persisted.getCurrentPage(),
                  persisted.getVersion() == null ? 1L : persisted.getVersion()
          );
     }

}

