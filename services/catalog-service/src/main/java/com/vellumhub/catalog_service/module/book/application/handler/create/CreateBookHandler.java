package com.vellumhub.catalog_service.module.book.application.handler.create;

import com.vellumhub.catalog_service.module.book.application.command.CreateBookCommand;
import com.vellumhub.catalog_service.module.book.domain.exception.InvalidBookException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookChangeWriter;
import com.vellumhub.catalog_service.module.book.domain.model.BookChangeKind;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CreateBookHandler {

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final BookChangeWriter bookChangeWriter;
    private final VellumHubMetrics metrics;

    public CreateBookHandler(
            BookRepository bookRepository,
            GenreRepository genreRepository,
            BookChangeWriter bookChangeWriter,
            VellumHubMetrics metrics) {
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
        this.bookChangeWriter = bookChangeWriter;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(CreateBookCommand command) {
        if (command == null) throw new InvalidBookException("Command cannot be null");

        verifyUniquenessPolicy(command);

        Set<Genre> resolvedGenres = ensureGenresAreValid(command.genres());

        var book = Book.create(
                command.title(),
                command.description(),
                command.releaseYear(),
                command.author(),
                command.isbn(),
                command.pageCount(),
                command.publisher(),
                command.coverUrl(),
                resolvedGenres
        );

        bookRepository.save(book);

        bookChangeWriter.record(book, BookChangeKind.CREATED);
        metrics.recordBusinessCounter(VellumHubMetrics.BOOKS_CREATED, "book_creation", "success");
    }

    private void verifyUniquenessPolicy(CreateBookCommand command) {
        if (bookRepository.existByTitleAndAuthorAndIsbn(
                command.title(),
                command.author(),
                command.isbn()
        )) {
            throw new InvalidBookException("Book with the same title, author and ISBN already exists.");
        }
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

}

