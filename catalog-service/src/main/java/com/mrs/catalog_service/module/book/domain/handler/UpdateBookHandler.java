package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.GenreRepository;
import com.mrs.catalog_service.module.book.presentation.dto.UpdateBookRequest;
import com.mrs.catalog_service.module.book.domain.event.UpdateBookEvent;
import com.mrs.catalog_service.module.book.domain.exception.BookNotFoundException;
import com.mrs.catalog_service.module.book.domain.exception.InvalidBookException;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
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

        Set<Genre> resolvedGenres = null;
        if (request.genres() != null) {
            resolvedGenres = request.genres().stream()
                    .map(this::findOrCreateGenre)
                    .collect(Collectors.toSet());
        }

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
     * Search for an existing genre by name. If it doesn't exist, create a new one and save it to the database.
     *
     * @param genreName The name of the genre to find or create.
     * @return The existing or newly created Genre entity.
     */
    private Genre findOrCreateGenre(String genreName) {
        String normalizedName = genreName.trim().toUpperCase();

        return genreRepository.findByName(normalizedName)
                .orElseGet(() -> {
                    Genre newGenre = Genre.builder().name(normalizedName).build();
                    return genreRepository.save(newGenre);
                });
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