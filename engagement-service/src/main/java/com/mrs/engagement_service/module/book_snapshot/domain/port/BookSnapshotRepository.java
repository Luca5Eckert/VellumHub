package com.mrs.engagement_service.module.book_snapshot.domain.port;

import com.mrs.engagement_service.module.book_snapshot.domain.model.BookSnapshot;

public interface BookSnapshotRepository {
    void save(BookSnapshot bookSnapshot);
}
