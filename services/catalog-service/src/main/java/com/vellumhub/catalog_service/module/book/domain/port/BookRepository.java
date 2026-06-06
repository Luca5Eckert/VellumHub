package com.vellumhub.catalog_service.module.book.domain.port;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.*;

public interface BookRepository {
    void save(Book book);

    boolean existsById(UUID bookId);

    Page<Book> findAll(PageRequest pageRequest);

    Optional<Book> findById(UUID bookId);

    void deleteById(UUID bookId);

    List<Book> findAllById(Collection<UUID> uuids);

    boolean existByTitleAndAuthor(String title, String author);

    boolean existByTitleAndAuthorAndIsbn(String title, String author, String isbn);

    boolean existsByIsbn(String isbn);
}
