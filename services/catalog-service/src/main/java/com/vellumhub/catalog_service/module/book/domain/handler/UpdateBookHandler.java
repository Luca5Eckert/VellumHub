package com.vellumhub.catalog_service.module.book.domain.handler;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.vellumhub.catalog_service.module.book.domain.event.UpdateBookEvent;
import com.vellumhub.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.vellumhub.catalog_service.module.book.domain.exception.InvalidBookException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookEventProducer;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class UpdateBookHandler {

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final BookEventProducer<String, UpdateBookEvent> bookEventProducer;

    public UpdateBookHandler(
            BookRepository bookRepository,
            GenreRepository genreRepository,
            BookEventProducer<String, UpdateBookEvent> bookEventProducer) {
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
        this.bookEventProducer = bookEventProducer;
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

        Set<Genre> resolvedGenres = ensureGenresAreValid(request.genres());

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

        bookRepository.save(book);

        UpdateBookEvent updateBookEvent = new UpdateBookEvent(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getAuthor(),
                book.getGenres().stream().map(Genre::getName).toList()
        );

        bookEventProducer.send("updated-book", book.getId().toString(), updateBookEvent);
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
                .orElseThrow(() -> new BookRequestDomainException("Genre not found: " + name));
    }


    private void verifyIfBookAlreadyExists(Book book, UpdateBookRequest request) {
        boolean titleChanged = request.title() != null && !request.title().equals(book.getTitle());
        boolean isbnChanged = request.isbn() != null && !request.isbn().equals(book.getIsbn());

        if (!titleChanged && !isbnChanged) return;

        if (bookRepository.existByTitleAndAuthorAndIsbn(request.title(), request.author(), request.isbn())) {
            throw new InvalidBookException("Book with the same title, author and ISBN already exists.");
        }
    }

}