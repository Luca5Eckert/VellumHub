package com.mrs.engagement_service.module.book_progress.domain.use_case;

import com.mrs.engagement_service.module.book_progress.domain.command.DeleteBookProgressCommand;
import com.mrs.engagement_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.mrs.engagement_service.module.book_progress.domain.port.BookProgressRepository;
import org.springframework.stereotype.Component;

@Component
public class DeleteBookProgressUseCase {

    private final BookProgressRepository bookProgressRepository;

    public DeleteBookProgressUseCase(BookProgressRepository bookProgressRepository) {
        this.bookProgressRepository = bookProgressRepository;
    }

    public void execute(DeleteBookProgressCommand command){
        if(!bookProgressRepository.existsByUserIdAndBookId(command.userId(), command.bookId())){
            throw new BookProgressNotFoundException();
        }

        bookProgressRepository.deleteByUserIdAndBookId(command.userId(), command.bookId());
    }

}
