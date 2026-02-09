package com.mrs.engagement_service.module.book_progress.domain.port;


import com.mrs.engagement_service.module.rating.domain.model.BookProgress;

import java.util.Optional;
import java.util.UUID;

public interface BookProgressRepository {
    Optional<BookProgress> findByUserIdAndBookId(UUID uuid, UUID uuid1);

    BookProgress save(BookProgress bookProgress);

    Optional<BookProgress> findById(UUID bookProgressId);

    boolean existsByUserIdAndBookId(UUID userId, UUID bookId);

    void deleteByUserIdAndBookId(UUID uuid, UUID uuid1);
}
