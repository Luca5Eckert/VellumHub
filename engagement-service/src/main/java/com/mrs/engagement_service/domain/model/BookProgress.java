package com.mrs.engagement_service.domain.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "book_statuses")
@Getter
@Setter
@AllArgsConstructor
public class BookProgress {

    @Column(
            nullable = false,
            name = "book_id"
    )
    private final UUID bookId;

    @Enumerated(EnumType.STRING)
    @Column(
            nullable = false,
            name = "reading_status"
    )
    private final ReadingStatus readingStatus;

    @Column(
            nullable = true,
            name = "current_page"
    )
    private final int currentPage;

}
