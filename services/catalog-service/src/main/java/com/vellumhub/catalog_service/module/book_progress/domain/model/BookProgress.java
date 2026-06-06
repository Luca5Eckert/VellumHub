package com.vellumhub.catalog_service.module.book_progress.domain.model;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "book_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "reading_status")
    private ReadingStatus readingStatus;

    @Column(name = "current_page", nullable = false)
    private Integer currentPage;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    private BookProgress(UUID userId, Book book, int currentPage,
                         ReadingStatus readingStatus, OffsetDateTime startedAt, OffsetDateTime endAt) {
        this.userId = userId;
        this.book = book;
        this.currentPage = currentPage;
        this.readingStatus = readingStatus;
        this.startedAt = startedAt;
        this.endAt = endAt;
    }

    public static BookProgress create(UUID userId, Book book, int initialPage,
                                      ReadingStatus readingStatus, OffsetDateTime startedAt, OffsetDateTime endAt) {

        if (endAt != null) readingStatus = ReadingStatus.COMPLETED;
        if (readingStatus == ReadingStatus.COMPLETED) initialPage = book.getPageCount();
        if (readingStatus == ReadingStatus.WANT_TO_READ) startedAt = null;
        if (readingStatus == ReadingStatus.READING && startedAt == null) startedAt = OffsetDateTime.now();

        var bookProgress = new BookProgress(userId, book, initialPage, readingStatus, startedAt, endAt);
        bookProgress.ensureStateOfActive();
        return bookProgress;
    }

    public void update(ReadingStatus newStatus, int newPage) {
        if (newPage < 0) {
            throw new BookProgressDomainException("Current page cannot be negative");
        }
        if (newPage >= book.getPageCount()) {
            this.readingStatus = ReadingStatus.COMPLETED;
            this.currentPage = book.getPageCount();

            ensureStateOfActive();

            return;
        }
        this.currentPage = newPage;
        this.readingStatus = newStatus;

        ensureStateOfActive();
    }

    private void ensureStateOfActive() {
        this.isActive = readingStatus != ReadingStatus.COMPLETED;
    }

}