package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import com.mrs.catalog_service.module.book_request.domain.command.CreateBookRequestCommand;
import com.mrs.catalog_service.module.book_request.domain.exception.BookAlreadyExistInCatalogException;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestAlreadyExistException;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateBookRequestUseCase {

    private final BookRequestRepository bookRequestRepository;
    private final BookRepository bookRepository;

    public CreateBookRequestUseCase(BookRequestRepository bookRequestRepository, BookRepository bookRepository) {
        this.bookRequestRepository = bookRequestRepository;
        this.bookRepository = bookRepository;
    }

    public BookRequest execute(CreateBookRequestCommand command){
        verifyBookExists(command.title(), command.author());

        BookRequest bookRequest = BookRequest.builder()
                .title(command.title())
                .author(command.author())
                .description(command.description())
                .coverUrl(command.coverUrl())
                .releaseYear(command.releaseYear())
                .isbn(command.isbn())
                .pageCount(command.pageCount())
                .publisher(command.publisher())
                .genres(command.genres())
                .build();

        bookRequestRepository.save(bookRequest);

        return bookRequest;
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
