package com.mrs.engagement_service.module.book_progress.infrastructure.persistence.repository;

import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookProgressRepositoryJpa extends JpaRepository<BookProgress, Long> {
    Optional<BookProgress> findByUserIdAndBookId(UUID userId, UUID bookId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    void deleteByUserIdAndBookId(UUID userId, UUID bookId);
}
