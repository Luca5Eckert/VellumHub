package com.vellumhub.catalog_service.module.book.infrastructure.provider.google.adapter;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookExternalProvider;
import com.vellumhub.catalog_service.module.book.infrastructure.provider.google.dto.GoogleBooksResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Optional;

@Slf4j
@Component
public class GoogleBooksAdapter implements BookExternalProvider {

    private final RestClient restClient;

    public GoogleBooksAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${app.integration.google-books.url:https://www.googleapis.com/books/v1}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    @Override
    public Optional<Book> fetchByIsbn(String isbn) {
        log.info("Fetching book from Google Books API for ISBN: {}", isbn);

        try {
            GoogleBooksResponse response = restClient.get()
                    .uri("/volumes?q=isbn:{isbn}", isbn)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, res) -> {
                        log.error("Google Books API error. Status: {}", res.getStatusCode());
                        throw new RuntimeException("Failed to communicate with Google Books API");
                    })
                    .body(GoogleBooksResponse.class);

            if (response == null || response.isEmpty()) {
                log.info("No book found in Google Books API for ISBN: {}", isbn);
                return Optional.empty();
            }

            return mapToDomain(response, isbn);

        } catch (Exception e) {
            log.error("Unexpected error fetching book by ISBN: {}", isbn, e);
            throw new RuntimeException("External catalog service is currently unavailable", e);
        }
    }

    private Optional<Book> mapToDomain(GoogleBooksResponse response, String requestedIsbn) {
        var volumeInfo = response.items().getFirst().volumeInfo();

        if(volumeInfo == null) return Optional.empty();

        String author = volumeInfo.authors() != null && !volumeInfo.authors().isEmpty()
                ? String.join(", ", volumeInfo.authors())
                : "Unknown Author";

        String coverUrl = volumeInfo.imageLinks() != null
                ? volumeInfo.imageLinks().thumbnail()
                : null;

        Book book = Book.create(
                volumeInfo.title() != null ? volumeInfo.title() : "Title Unavailable",
                volumeInfo.description(),
                extractYear(volumeInfo.publishedDate()),
                author,
                requestedIsbn,
                volumeInfo.pageCount() != null ? volumeInfo.pageCount() : 0,
                volumeInfo.publisher() != null ? volumeInfo.publisher() : "Unknown Publisher",
                coverUrl,
                Collections.emptySet()
        );

        return Optional.of(book);
    }

    private int extractYear(String publishedDate) {
        if (publishedDate == null || publishedDate.isBlank()) {
            return 0;
        }

        try {
            if (publishedDate.length() > 4) {
                return LocalDate.parse(publishedDate).getYear();
            }
            return Integer.parseInt(publishedDate);
        } catch (DateTimeParseException | NumberFormatException e) {
            log.warn("Unknown date format returned by Google Books: {}", publishedDate);
            return 0;
        }
    }
}