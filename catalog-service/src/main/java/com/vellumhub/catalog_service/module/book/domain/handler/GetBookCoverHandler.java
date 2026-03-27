package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.exception.BookDomainException;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookCoverStorage;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Handler responsible for retrieving book cover images by book ID.
 * Follows Single Responsibility Principle - only handles cover retrieval logic.
 */
@Component
public class GetBookCoverHandler {

    private static final String COVER_URL_PREFIX = "/files/books/";

    private final BookRepository bookRepository;
    private final BookCoverStorage bookCoverStorage;

    public GetBookCoverHandler(BookRepository bookRepository, BookCoverStorage bookCoverStorage) {
        this.bookRepository = bookRepository;
        this.bookCoverStorage = bookCoverStorage;
    }

    /**
     * Retrieves the cover image for the specified book.
     *
     * @param bookId the ID of the book
     * @return the cover image as a Resource
     * @throws BookNotFoundException if the book does not exist
     * @throws BookDomainException if the book has no cover or the cover file is not found
     */
    public Resource execute(UUID bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId.toString()));

        String coverUrl = book.getCoverUrl();
        if (coverUrl == null || coverUrl.isBlank()) {
            throw new BookDomainException("Book does not have a cover image");
        }

        String filename = extractFilename(coverUrl);

        return bookCoverStorage.load(filename)
                .orElseThrow(() -> new BookDomainException("Cover file not found"));
    }

    /**
     * Extracts the filename from the cover URL.
     * The URL format is expected to be: /files/books/{filename}
     */
    private String extractFilename(String coverUrl) {
        if (coverUrl.startsWith(COVER_URL_PREFIX)) {
            return coverUrl.substring(COVER_URL_PREFIX.length());
        }
        return coverUrl;
    }
}
