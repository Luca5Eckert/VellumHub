package com.vellumhub.catalog_service.module.book_progress.domain.model;

import com.vellumhub.catalog_service.module.book.domain.model.Book;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.BookProgressDomainException;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "book_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "reading_status")
    private ReadingStatus readingStatus;

    @Column(name = "current_page")
    private Integer currentPage;

    public BookProgress(Book book, UUID userId) {
        this.book = book;
        this.userId = userId;
        this.readingStatus = ReadingStatus.WANT_TO_READ;
        this.currentPage = 0;
    }

    public static BookProgress create(UUID userId, Book book, int initialPage, ReadingStatus readingStatus) {
        return new BookProgress(null, book, userId, readingStatus, initialPage);
    }

    public void update(ReadingStatus readingStatus, int currentPage) {
        if (currentPage < 0) {
            throw new BookProgressDomainException("Current page cannot be negative");
        }
        if (book.getPageCount() < currentPage) {
            throw new BookProgressDomainException("Current page cannot exceed total page count of the book");
        }

        this.currentPage = currentPage;
        this.readingStatus = readingStatus;
    }
}
