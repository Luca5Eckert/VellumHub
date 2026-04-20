package com.vellumhub.catalog_service.module.book_progress.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_progress.domain.event.CreateBookProgressEvent;
import com.vellumhub.catalog_service.module.book_progress.domain.command.DefineBookStatusCommand;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
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
        Book book = bookRepository.findById(command.bookId())
                .orElseThrow(() -> new BookNotFoundException("Book with id " + command.bookId() + " not found"));

        if(bookProgressRepository.existsByUserIdAndBookIdAndReadingStatus(command.userId(), command.bookId(), ReadingStatus.READING)) {
            throw new BookProgressDomainException("User already has a book marked as CURRENT");
        }

        var bookProgress = BookProgress.create(
                command.userId(),
                book,
                command.initialPage(),
                command.readingStatus()
        );

        bookProgressRepository.save(bookProgress);

        return new CreateBookProgressEvent(
                command.userId(),
                command.bookId(),
                bookProgress.getReadingStatus().name(),
                bookProgress.getCurrentPage()
        );
    }

}
