package com.mrs.engagement_service.module.book_snapshot.domain.port;

import com.mrs.engagement_service.module.book_snapshot.domain.model.BookSnapshot;

import java.util.UUID;

public interface BookSnapshotRepository {
    void save(BookSnapshot bookSnapshot);

    boolean existsByBookId(UUID bookId);

    void deleteByBookId(UUID uuid);
}
