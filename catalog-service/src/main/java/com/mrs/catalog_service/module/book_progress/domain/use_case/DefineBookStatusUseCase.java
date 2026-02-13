package com.mrs.catalog_service.module.book_progress.domain.use_case;

import com.mrs.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.mrs.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

@Component
public class DefineBookStatusUseCase {

    private final BookProgressRepository bookProgressRepository;

    public DefineBookStatusUseCase(BookProgressRepository bookProgressRepository) {
        this.bookProgressRepository = bookProgressRepository;
    }

    public BookProgress execute(DefineBookStatusCommand command) {
        BookProgress bookProgress = bookProgressRepository.findByUserIdAndBookId(command.userId(), command.bookId())
                .orElseGet(() -> new BookProgress(command.bookId(), command.userId()));

        bookProgress.setReadingStatus(command.readingStatus());

        if(command.currentPage() >= 0){
            bookProgress.setCurrentPage(command.currentPage());
        }

        return bookProgressRepository.save(bookProgress);
    }

}
