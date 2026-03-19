package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.command.CreateBookCommand; // <-- Usando Command
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book.domain.port.GenreRepository;
import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CreateBookHandler {

    private final BookRepository bookRepository;
    private final GenreRepository genreRepository;
    private final BookEventProducer<String, CreateBookEvent> bookEventProducer;

    public CreateBookHandler(
            BookRepository bookRepository,
            GenreRepository genreRepository,
            BookEventProducer<String, CreateBookEvent> bookEventProducer) {
        this.bookRepository = bookRepository;
        this.genreRepository = genreRepository;
        this.bookEventProducer = bookEventProducer;
    }

    @Transactional
    public void execute(CreateBookCommand command) {
        if (command == null) throw new InvalidBookException("Command cannot be null");

        verifyUniquenessPolicy(command);

        Set<Genre> resolvedGenres = resolveGenres(command.genres());

        var book = Book.create(command, resolvedGenres);

        bookRepository.save(book);

        publishEvent(book);
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

    private Set<Genre> resolveGenres(Set<String> rawGenres) {
        if (rawGenres == null || rawGenres.isEmpty()) {
            return new HashSet<>();
        }

        return rawGenres.stream()
                .filter(g -> g != null && !g.isBlank())
                .map(this::findOrCreateGenre)
                .collect(Collectors.toSet());
    }

    private Genre findOrCreateGenre(String genreName) {
        String normalizedName = genreName.trim().toUpperCase();

        return genreRepository.findByName(normalizedName)
                .orElseGet(() -> genreRepository.save(new Genre(normalizedName)));
    }

    private void publishEvent(Book book) {
        CreateBookEvent createBookEvent = new CreateBookEvent(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getAuthor(),
                book.getGenres().stream().map(Genre::getName).toList()
        );

        bookEventProducer.send("created-book", createBookEvent.bookId().toString(), createBookEvent);
    }
}