package com.vellumhub.engagement_service.module.book_snapshot.domain.port;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;

import java.util.UUID;

public interface BookSnapshotRepository {
    void save(BookSnapshot bookSnapshot);

    void deleteByBookId(UUID bookId);

    boolean existsById(UUID bookId);
}
