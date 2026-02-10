package com.mrs.engagement_service.module.book_progress.domain.use_case;

import com.mrs.engagement_service.module.book_progress.domain.command.UpdateBookProgressCommand;
import com.mrs.engagement_service.module.book_progress.domain.exception.BookIsNotBeingReadException;
import com.mrs.engagement_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.mrs.engagement_service.module.book_progress.domain.model.ReadingStatus;
import com.mrs.engagement_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import org.springframework.stereotype.Component;

@Component
public class UpdateBookProgressUseCase {

     private final BookProgressRepository bookProgressRepository;

     public UpdateBookProgressUseCase(BookProgressRepository bookProgressRepository) {
          this.bookProgressRepository = bookProgressRepository;
     }

     public BookProgress execute(UpdateBookProgressCommand command){
          BookProgress bookProgress = bookProgressRepository.findByUserIdAndBookId(command.userId(), command.bookId())
                  .orElseThrow(BookProgressNotFoundException::new);

          if(!bookProgress.getReadingStatus().equals(ReadingStatus.READING)){
               throw new BookIsNotBeingReadException();
          }

          bookProgress.update(command.currentPage());

          return bookProgressRepository.save(bookProgress);
     }

}
