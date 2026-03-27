package com.vellumhub.catalog_service.module.book_request.domain.use_case;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import com.vellumhub.catalog_service.module.book.domain.port.GenreRepository;
import com.vellumhub.catalog_service.module.book_request.domain.BookRequest;
import com.vellumhub.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookAlreadyExistInCatalogException;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestAlreadyExistException;
import com.vellumhub.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import com.vellumhub.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CreateBookRequestUseCase {

    private final BookRequestRepository bookRequestRepository;
    private final GenreRepository genreRepository;
    private final BookRepository bookRepository;

    public CreateBookRequestUseCase(BookRequestRepository bookRequestRepository, GenreRepository genreRepository, BookRepository bookRepository) {
        this.bookRequestRepository = bookRequestRepository;
        this.genreRepository = genreRepository;
        this.bookRepository = bookRepository;
    }

    public BookRequest execute(CreateBookRequestCommand command){
        verifyBookExists(command.title(), command.author());

        var genres = ensureGenresAreValid(command.genres());

        BookRequest bookRequest = BookRequest.builder()
                .title(command.title())
                .author(command.author())
                .description(command.description())
                .coverUrl(command.coverUrl())
                .releaseYear(command.releaseYear())
                .isbn(command.isbn())
                .pageCount(command.pageCount())
                .publisher(command.publisher())
                .genres(genres)
                .build();

        bookRequestRepository.save(bookRequest);

        return bookRequest;
    }

    /**
     * Ensures that all genres provided in the command are valid and exist in the system.
     * @param genres Set of genre names to validate
     * @return Set of Genre entities corresponding to the provided genre names
     */
    private Set<Genre> ensureGenresAreValid(List<String> genres) {
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

    private void verifyBookExists(String title, String author) {
        if(bookRepository.existByTitleAndAuthor(title, author)){
            throw new BookAlreadyExistInCatalogException();
        }
        if(bookRequestRepository.existByTitleAndAuthor(title, author)){
            throw new BookRequestAlreadyExistException();
        }
    }

}
