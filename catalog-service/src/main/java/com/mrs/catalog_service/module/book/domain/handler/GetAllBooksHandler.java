package com.mrs.catalog_service.module.book.domain.handler;

import com.mrs.catalog_service.module.book.application.dto.PageBook;
import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class GetAllBooksHandler {

    private final BookRepository bookRepository;

    public GetAllBooksHandler(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public Page<Book> execute(PageBook pageMedia){
        PageRequest pageRequest = PageRequest.of(
                pageMedia.pageNumber(),
                pageMedia.pageSize()
        );

        return bookRepository.findAll(pageRequest);
    }

}
