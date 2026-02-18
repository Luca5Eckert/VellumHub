package com.mrs.catalog_service.module.book_progress.domain.model;

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

    @Column(
            nullable = false,
            name = "book_id"
    )
    private UUID bookId;

    @Column(
            nullable = false,
            name = "user_id"
    )
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            name = "reading_status"
    )
    private ReadingStatus readingStatus;

    @Column(
            nullable = true,
            name = "current_page"
    )
    private int currentPage;

    public BookProgress(UUID bookId, UUID userId) {
        this.bookId = bookId;
        this.userId = userId;
    }

    public void update(int currentPage) {
        this.setCurrentPage(currentPage);
    }
}
