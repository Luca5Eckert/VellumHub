package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.dto.BookCoverResponse;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookCoverStorage;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Handler responsible for retrieving multiple book cover images in a single operation.
 * Solves the N+1 problem when fetching covers for recommendations or lists.
 */
@Component
public class GetBookCoversBulkHandler {

    private static final String COVER_URL_PREFIX = "/files/books/";

    private final BookRepository bookRepository;
    private final BookCoverStorage bookCoverStorage;

    public GetBookCoversBulkHandler(BookRepository bookRepository, BookCoverStorage bookCoverStorage) {
        this.bookRepository = bookRepository;
        this.bookCoverStorage = bookCoverStorage;
    }

    /**
     * Retrieves cover images for multiple books in a single operation.
     * Returns Base64 encoded image data for each book.
     *
     * @param bookIds list of book IDs to retrieve covers for
     * @return list of BookCoverResponse with cover data (or empty responses for books without covers)
     */
    public List<BookCoverResponse> execute(List<UUID> bookIds) {
        List<Book> books = bookRepository.findAllById(bookIds);
        List<BookCoverResponse> responses = new ArrayList<>();

        for (Book book : books) {
            BookCoverResponse response = loadCoverForBook(book);
            responses.add(response);
        }

        return responses;
    }

    private BookCoverResponse loadCoverForBook(Book book) {
        String coverUrl = book.getCoverUrl();
        if (coverUrl == null || coverUrl.isBlank()) {
            return BookCoverResponse.empty(book.getId());
        }

        String filename = extractFilename(coverUrl);
        return bookCoverStorage.load(filename)
                .map(resource -> createCoverResponse(book.getId(), resource, filename))
                .orElse(BookCoverResponse.empty(book.getId()));
    }

    private BookCoverResponse createCoverResponse(UUID bookId, Resource resource, String filename) {
        try {
            byte[] bytes = resource.getInputStream().readAllBytes();
            String base64Data = Base64.getEncoder().encodeToString(bytes);
            String contentType = MediaTypeFactory.getMediaType(filename)
                    .map(MediaType::toString)
                    .orElse("application/octet-stream");

            return new BookCoverResponse(bookId, base64Data, contentType);
        } catch (IOException e) {
            return BookCoverResponse.empty(bookId);
        }
    }

    private String extractFilename(String coverUrl) {
        if (coverUrl.startsWith(COVER_URL_PREFIX)) {
            return coverUrl.substring(COVER_URL_PREFIX.length());
        }
        return coverUrl;
    }
}
