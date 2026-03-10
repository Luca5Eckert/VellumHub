package com.mrs.catalog_service.module.book_list.application.use_case;

import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import com.mrs.catalog_service.module.book_list.application.command.CreateBookListCommand;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateBookListUseCase {

    private final BookListRepository bookListRepository;
    private final BookRepository bookRepository;

    public CreateBookListUseCase(BookListRepository bookListRepository, BookRepository bookRepository) {
        this.bookListRepository = bookListRepository;
        this.bookRepository = bookRepository;
    }

    public BookList execute(CreateBookListCommand command){
        var books = bookRepository.findAllById(command.books());

        if(command.books().size() != books.size()) throw new BookListDomainException("Not found all books");

        BookList bookList = BookList.builder()
                .books(books)
                .userOwner(command.userId())
                .build();

        return bookListRepository.save(bookList);
    }

}
