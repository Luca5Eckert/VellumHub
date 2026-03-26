package com.mrs.engagement_service.module.book_snapshot.infrastructure.persistence.repository;

import com.mrs.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaBookSnapshotRepository extends JpaRepository<BookSnapshot, UUID> {
    void deleteByBookId(UUID bookId);
}
