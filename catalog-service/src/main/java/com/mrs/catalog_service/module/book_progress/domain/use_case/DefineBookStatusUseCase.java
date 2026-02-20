package com.mrs.catalog_service.module.book_progress.domain.use_case;

import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.mrs.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import com.mrs.catalog_service.module.book_progress.domain.exception.BookProgressNotFoundException;
import com.mrs.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

@Component
public class DefineBookStatusUseCase {

    private final BookProgressRepository bookProgressRepository;
    private final BookRepository bookRepository;

    public DefineBookStatusUseCase(BookProgressRepository bookProgressRepository, BookRepository bookRepository) {
        this.bookProgressRepository = bookProgressRepository;
        this.bookRepository = bookRepository;
    }

    public BookProgress execute(DefineBookStatusCommand command) {
        BookProgress bookProgress = bookProgressRepository.findByUserIdAndBookId(command.userId(), command.bookId())
                .orElseGet(() -> new BookProgress(command.bookId(), command.userId()));

        Book book = bookRepository.findById(command.bookId())
                        .orElseThrow(BookProgressNotFoundException::new);

        if(command.currentPage() > book.getPageCount()) {
            throw new BookProgressDomainException("Current page cannot be greater than total page count");
        }

        bookProgress.defineProgress(command.readingStatus(), command.currentPage());

        return bookProgressRepository.save(bookProgress);
    }

}
