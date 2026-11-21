package com.vellumhub.catalog_service.module.book.application.handler.get;

import com.vellumhub.catalog_service.module.book.application.query.PageBook;
import com.vellumhub.catalog_service.module.book.domain.exception.BookDomainException;
import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book.domain.port.BookRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetAllBooksHandler {

    private final BookRepository bookRepository;

    public GetAllBooksHandler(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> search(String text, PageBook page) {
        String query = text.strip();
        if (query.isEmpty() || query.length() > 200) {
            throw new BookDomainException("Search text must be between 1 and 200 characters");
        }
        return bookRepository.search(query, PageRequest.of(page.pageNumber(), page.pageSize()));
    }

    public Page<Book> execute(PageBook page){
        PageRequest pageRequest = PageRequest.of(
                page.pageNumber(),
                page.pageSize(),
                Sort.by(Sort.Direction.ASC, "createdAt", "id")
        );

        return bookRepository.findAll(pageRequest);
    }

}
