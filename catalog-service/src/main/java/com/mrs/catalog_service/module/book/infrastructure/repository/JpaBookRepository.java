package com.mrs.catalog_service.module.book.infrastructure.repository;

import com.mrs.catalog_service.module.book.domain.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JpaBookRepository extends JpaRepository<Book, UUID> {
    boolean existsByTitleAndAuthor(String title, String author);

    boolean existByTitleAndAuthorAndIsbn(String title, String author, String isbn);
}
