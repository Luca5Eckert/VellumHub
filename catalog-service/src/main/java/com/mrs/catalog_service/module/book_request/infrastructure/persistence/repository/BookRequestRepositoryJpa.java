package com.mrs.catalog_service.module.book_request.infrastructure.persistence.repository;

import com.mrs.catalog_service.module.book_request.domain.BookRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookRequestRepositoryJpa extends JpaRepository<BookRequest, Long> {

    boolean existsByTitleAndAuthor(String title, String author);

}
