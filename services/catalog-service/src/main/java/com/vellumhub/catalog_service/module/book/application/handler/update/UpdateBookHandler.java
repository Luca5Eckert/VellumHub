package com.vellumhub.catalog_service.module.book.application.handler.update;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.exception.InvalidBookException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.application.event.BookEventFactory;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UpdateBookHandler {

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final BookChangeWriter bookChangeWriter;
    private final BookEventFactory events;
    private final VellumHubMetrics metrics;

    public UpdateBookHandler(
            BookRepository bookRepository,
            GenreRepository genreRepository,
            BookChangeWriter bookChangeWriter,
            BookEventFactory events,
            VellumHubMetrics metrics) {
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
        this.bookChangeWriter = bookChangeWriter;
        this.events = events;
        this.metrics = metrics;
    }

    /**
     * Update an existing book with the provided information. Only non-null fields in the request will be updated.
     * @param bookId The ID of the book to update.
     * @param request The request containing the new information for the book.
     */
    @Transactional
    public void execute(UUID bookId, UpdateBookRequest request) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(BookNotFoundException::new);

        verifyIfBookAlreadyExists(book, request);

        var before = events.snapshot(book);
        Set<Genre> resolvedGenres = request.genres() == null ? null : ensureGenresAreValid(request.genres());

        book.update(
                request.title(),
                request.description(),
                request.coverUrl(),
                request.releaseYear(),
                request.author(),
                request.isbn(),
                request.pageCount(),
                request.publisher(),
                resolvedGenres
        );

        if (!before.equals(events.snapshot(book))) {
            bookRepository.save(book);
            bookChangeWriter.record(book, BookChangeKind.UPDATED);
        }
        metrics.recordBusinessCounter(VellumHubMetrics.BOOKS_UPDATED, "book_update", "success");
    }

    /**
     * Ensures that all genres provided in the command are valid and exist in the system.
     * @param genres Set of genre names to validate
     * @return Set of Genre entities corresponding to the provided genre names
     */
    private Set<Genre> ensureGenresAreValid(Set<String> genres) {
        return genres.stream()
                .map(this::findGenre)
                .collect(Collectors.toSet());
    }

    /**
     * Finds a Genre entity by its name. If the genre does not exist, throws an exception.
     * @param name The name of the genre to find
     * @return The Genre entity corresponding to the provided name
     */
    private Genre findGenre(String name) {
        return genreRepository.findByName(name)
                .orElseThrow(() -> new InvalidBookException("Genre not found: " + name));
    }


    private void verifyIfBookAlreadyExists(Book book, UpdateBookRequest request) {
        boolean titleChanged = request.title() != null && !request.title().isBlank()
                && !request.title().equals(book.getTitle());
        boolean authorChanged = request.author() != null && !request.author().isBlank()
                && !request.author().equals(book.getAuthor());
        boolean isbnChanged = request.isbn() != null && !request.isbn().isBlank()
                && !request.isbn().equals(book.getIsbn());

        if (!titleChanged && !authorChanged && !isbnChanged) return;

        String effectiveTitle = titleChanged ? request.title() : book.getTitle();
        String effectiveAuthor = authorChanged ? request.author() : book.getAuthor();
        String effectiveIsbn = isbnChanged ? request.isbn() : book.getIsbn();
        if (bookRepository.existByTitleAndAuthorAndIsbn(effectiveTitle, effectiveAuthor, effectiveIsbn)) {
            throw new InvalidBookException("Book with the same title, author and ISBN already exists.");
        }
    }

}

