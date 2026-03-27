package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.command.CreateBookCommand;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book.domain.port.GenreRepository;
import com.mrs.catalog_service.module.book.domain.event.CreateBookEvent;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Publishes a CreateBookEvent to notify other parts of the system about the creation of a new book.
     * @param book The book that was created, used to populate the event data
     */
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