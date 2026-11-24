package com.vellumhub.catalog_service.module.book_progress.domain.model;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "book_progress")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

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

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "end_at")
    private OffsetDateTime endAt;

    @Column(name = "target_end_date")
    private LocalDate targetEndDate;

    @Column(name = "last_read_at")
    private OffsetDateTime lastReadAt;

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
            this.endAt = OffsetDateTime.now();

            ensureStateOfActive();

            return;
        }
        this.currentPage = newPage;
        this.readingStatus = newStatus;

        ensureStateOfActive();
    }

    public boolean recordProgress(Integer page, Integer percentage, OffsetDateTime readAt, OffsetDateTime now) {
        if (readingStatus != ReadingStatus.READING || (page == null) == (percentage == null)) {
            throw new BookProgressDomainException("An ongoing reading needs either a page or a percentage");
        }
        int total = book.getPageCount();
        if (percentage != null && (percentage < 0 || percentage > 100)) {
            throw new BookProgressDomainException("Percentage must be between 0 and 100");
        }
        int next = page == null ? (int) Math.round(percentage * (double) total / 100) : page;
        if (next < 0 || next > total) {
            throw new BookProgressDomainException("Page must be between 0 and the book page count");
        }
        var date = readAt == null ? now : readAt;
        if (date.isAfter(now) || (startedAt != null && date.isBefore(startedAt))) {
            throw new BookProgressDomainException("Reading time must be between the start and now");
        }
        if (next == currentPage && (readAt == null || Objects.equals(lastReadAt, date))) {
            return false;
        }
        currentPage = next;
        lastReadAt = date;
        if (next == total) {
            readingStatus = ReadingStatus.COMPLETED;
            endAt = date;
        }
        ensureStateOfActive();
        return true;
    }

    public boolean transitionTo(ReadingStatus next, OffsetDateTime now) {
        if (readingStatus == next) {
            return false;
        }
        boolean allowed = switch (readingStatus) {
            case WANT_TO_READ -> next == ReadingStatus.READING;
            case READING -> next == ReadingStatus.PAUSED || next == ReadingStatus.ABANDONED
                    || next == ReadingStatus.COMPLETED;
            case PAUSED -> next == ReadingStatus.READING || next == ReadingStatus.ABANDONED;
            case COMPLETED, ABANDONED -> false;
        };
        if (!allowed) {
            throw new BookProgressDomainException("Invalid reading status transition");
        }
        readingStatus = next;
        if (next == ReadingStatus.READING && startedAt == null) {
            startedAt = now;
        }
        if (next == ReadingStatus.ABANDONED || next == ReadingStatus.COMPLETED) {
            endAt = now;
        }
        if (next == ReadingStatus.COMPLETED) {
            currentPage = book.getPageCount();
            lastReadAt = now;
        }
        ensureStateOfActive();
        return true;
    }

    public boolean changeTarget(LocalDate target, LocalDate today) {
        if (target != null && target.isBefore(today)) {
            throw new BookProgressDomainException("Target date cannot be before today");
        }
        if (Objects.equals(targetEndDate, target)) {
            return false;
        }
        targetEndDate = target;
        return true;
    }

    private void ensureStateOfActive() {
        this.isActive = readingStatus != ReadingStatus.COMPLETED && readingStatus != ReadingStatus.ABANDONED;
    }

}
