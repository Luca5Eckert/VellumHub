package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.event.CreateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.event.UpdateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DefineBookStatusUseCase {

    private final BookProgressRepository bookProgressRepository;
    private final BookRepository bookRepository;

    public DefineBookStatusUseCase(BookProgressRepository bookProgressRepository, BookRepository bookRepository) {
        this.bookProgressRepository = bookProgressRepository;
        this.bookRepository = bookRepository;
    }

    public CreateBookProgressEvent execute(DefineBookStatusCommand command) {
        BookProgress bookProgress = getBookProgress(command.userId(), command.bookId());

        int currentPage = bookProgress.getCurrentPage();

        bookProgress.defineProgress(command.readingStatus(), command.newCurrentPage());

        bookProgressRepository.save(bookProgress);

        return new CreateBookProgressEvent(
                command.userId(),
                command.bookId(),
                bookProgress.getReadingStatus().name(),
                currentPage,
                bookProgress.getCurrentPage()
        );
    }

    private BookProgress getBookProgress(UUID userId, UUID bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException("Book with id " + bookId + " not found"));

        return bookProgressRepository.findByUserIdAndBookId(userId, bookId)
                .orElseGet(() -> new BookProgress(book, userId));
    }

}
