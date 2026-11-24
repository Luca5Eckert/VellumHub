package com.vellumhub.catalog_service.module.book_progress.domain.port;


import com.vellumhub.catalog_service.module.book_progress.domain.model.ReadingStatus;
import com.vellumhub.catalog_service.module.book_progress.domain.model.BookProgress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookProgressRepository {
    Optional<BookProgress> findActiveByUserIdAndBookId(UUID userId, UUID bookId);

    Optional<BookProgress> findOwnedReading(UUID userId, UUID bookId, UUID readingId);

    List<BookProgress> findHistory(UUID userId, UUID bookId);

    void delete(BookProgress reading);

    BookProgress save(BookProgress bookProgress);

    Optional<BookProgress> findById(UUID bookProgressId);

    Optional<BookProgress> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    void deleteByUserIdAndBookId(UUID userId, UUID bookId);

    List<BookProgress> findAllByUserIdAndReadingStatus(UUID userId, ReadingStatus status);

    boolean existsByUserIdAndBookIdAndIsActive(UUID userId, UUID bookId);
}

