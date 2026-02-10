package com.mrs.engagement_service.module.book_progress.infrastructure.persistence.repository;

import com.mrs.engagement_service.module.book_progress.domain.port.BookProgressRepository;
import com.mrs.engagement_service.module.rating.domain.model.BookProgress;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class BookProgressRepositoryAdapter implements BookProgressRepository {

    private final BookProgressRepositoryJpa bookProgressRepositoryJpa;

    public BookProgressRepositoryAdapter(BookProgressRepositoryJpa bookProgressRepositoryJpa) {
        this.bookProgressRepositoryJpa = bookProgressRepositoryJpa;
    }

    @Override
    public Optional<BookProgress> findByUserIdAndBookId(UUID userId, UUID bookId) {
        return bookProgressRepositoryJpa.findByUserIdAndBookId(userId, bookId);
    }

    @Override
    public BookProgress save(BookProgress bookProgress) {
        return bookProgressRepositoryJpa.save(bookProgress);
    }


    @Override
    public Optional<BookProgress> findById(Long bookProgressId) {
        return bookProgressRepositoryJpa.findById(bookProgressId);
    }

    @Override
    public boolean existsByUserIdAndBookId(UUID userId, UUID bookId) {
        return bookProgressRepositoryJpa.existsByUserIdAndBookId(userId, bookId);
    }

    @Override
    public void deleteByUserIdAndBookId(UUID userId, UUID bookId) {
        bookProgressRepositoryJpa.deleteByUserIdAndBookId(userId, bookId);
    }
}
