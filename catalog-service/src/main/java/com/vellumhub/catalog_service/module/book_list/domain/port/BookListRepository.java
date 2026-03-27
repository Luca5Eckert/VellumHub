package com.vellumhub.catalog_service.module.book_list.domain.port;

import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListFilter;
import com.vellumhub.catalog_service.module.book_list.domain.filter.BookListPage;
import com.vellumhub.catalog_service.module.book_list.domain.model.BookList;
import io.swagger.v3.oas.annotations.Parameter;
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
