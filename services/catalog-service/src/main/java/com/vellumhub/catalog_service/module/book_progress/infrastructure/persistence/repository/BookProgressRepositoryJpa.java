package com.vellumhub.catalog_service.module.book_progress.infrastructure.persistence.repository;

import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;
import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookProgressRepositoryJpa extends JpaRepository<BookProgress, UUID> {
    Optional<BookProgress> findByUserIdAndBookIdAndIsActiveTrue(UUID userId, UUID bookId);

    Optional<BookProgress> findByIdAndUserId(UUID id, UUID userId);

    Optional<BookProgress> findByUserIdAndBookIdAndId(UUID userId, UUID bookId, UUID readingId);

    @Query("""
            select progress from BookProgress progress join fetch progress.book book
            where progress.userId = :userId and book.id = :bookId
            order by progress.createdAt desc, progress.id desc
            """)
    List<BookProgress> findHistory(UUID userId, UUID bookId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    void deleteByUserIdAndBookId(UUID userId, UUID bookId);

    List<BookProgress> findAllByUserIdAndReadingStatus(UUID userId, ReadingStatus status);

    boolean existsByUserIdAndBookIdAndIsActiveTrue(UUID userId, UUID bookId);
}

