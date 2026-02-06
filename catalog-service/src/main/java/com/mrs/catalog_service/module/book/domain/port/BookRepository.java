package com.mrs.catalog_service.module.book.domain.port;

import com.mrs.catalog_service.module.book.domain.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookRepository {
    void save(Book book);

    boolean existsById(UUID bookId);

    Page<Book> findAll(PageRequest pageRequest);

    Optional<Book> findById(UUID bookId);

    void deleteById(UUID bookId);

    List<Book> findAllById(List<UUID> uuids);
}
