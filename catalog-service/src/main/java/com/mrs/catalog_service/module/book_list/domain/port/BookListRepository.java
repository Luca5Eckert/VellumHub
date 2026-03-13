package com.mrs.catalog_service.module.book_list.domain.port;

import com.mrs.catalog_service.module.book_list.domain.filter.BookListFilter;
import com.mrs.catalog_service.module.book_list.domain.filter.BookListPage;
import com.mrs.catalog_service.module.book_list.domain.model.BookList;
import org.springframework.data.domain.Page;

import java.util.Optional;
import java.util.UUID;

public interface BookListRepository {

    Optional<BookList> findById(UUID id);

    BookList save(BookList bookList);

    void deleteById(UUID uuid);

    Optional<BookList> findByIdFull(UUID uuid);

    Page<BookList> findAll(BookListFilter bookListFilter, BookListPage bookListPage);
}
