package com.vellumhub.engagement_service.module.book_snapshot.infrastructure.persistence.repository;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SpringBookSnapshotRepositoryAdapter implements BookSnapshotRepository {

    private final JpaBookSnapshotRepository jpaBookSnapshotRepository;

    public SpringBookSnapshotRepositoryAdapter(JpaBookSnapshotRepository jpaBookSnapshotRepository) {
        this.jpaBookSnapshotRepository = jpaBookSnapshotRepository;
    }

    @Override
    public void save(BookSnapshot bookSnapshot) {
        jpaBookSnapshotRepository.save(bookSnapshot);
    }

    @Override
    public void deleteByBookId(UUID bookId) {
        jpaBookSnapshotRepository.deleteByBookId(bookId);
    }

    @Override
    public boolean existsById(UUID bookId) {
        return jpaBookSnapshotRepository.existsById(bookId);
    }

    @Override
    public Optional<BookSnapshot> findById(UUID id) {
        return jpaBookSnapshotRepository.findById(id);
    }
}
