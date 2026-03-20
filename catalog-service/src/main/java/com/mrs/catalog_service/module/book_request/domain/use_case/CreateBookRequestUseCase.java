package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book.domain.port.GenreRepository;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.mrs.catalog_service.module.book_request.domain.exception.BookAlreadyExistInCatalogException;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestAlreadyExistException;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestDomainException;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
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

    private Set<Genre> ensureGenresAreValid(List<String> genres) {
        return genres.stream()
                .filter(genreRepository::existsByName)
                .map(this::findGenre)
                .collect(Collectors.toSet());
    }

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
