package com.mrs.catalog_service.module.book_progress.infrastructure.persistence.repository;

import com.mrs.catalog_service.module.book_progress.domain.model.BookProgress;
import com.mrs.catalog_service.module.book_progress.domain.model.ReadingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookProgressRepositoryJpa extends JpaRepository<BookProgress, Long> {
    Optional<BookProgress> findByUserIdAndBookId(UUID userId, UUID bookId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    void deleteByUserIdAndBookId(UUID userId, UUID bookId);

    List<BookProgress> findAllByUserIdAndReadingStatus(UUID userId, ReadingStatus status);
}
