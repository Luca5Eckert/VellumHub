package com.mrs.catalog_service.module.book_list.presentation.mapper;

import com.mrs.catalog_service.module.book.domain.model.Book;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import com.mrs.catalog_service.module.book_list.presentation.dto.response.BookListResponse;
import com.mrs.catalog_service.module.book_list.presentation.dto.response.BookResponse;
import org.springframework.stereotype.Component;

@Component
public class BookListMapper {

    public BookListResponse toResponse(BookList bookList){
        return new BookListResponse(
                bookList.getId(),
                bookList.getBooks().stream().map(this::toBookResponse).toList(),
                bookList.getUserOwner()
        );
    }

    public BookResponse toBookResponse(Book book){
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getDescription(),
                book.getReleaseYear(),
                book.getCoverUrl(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPageCount(),
                book.getPublisher(),
                book.getGenres(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }

}
