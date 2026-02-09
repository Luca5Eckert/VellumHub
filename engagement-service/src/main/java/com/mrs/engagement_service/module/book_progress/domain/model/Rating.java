package com.mrs.engagement_service.module.book_progress.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Setter
@Getter
@AllArgsConstructor
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID mediaId;

    @Min(0)
    @Max(5)
    @Column(nullable = false)
    private int stars;

    @Column(length = 1000)
    private String review;

    private LocalDateTime timestamp;

    public Rating() {
    }

    public Rating(UUID userId, UUID mediaId, int stars, String review, LocalDateTime timestamp) {
        this.userId = userId;
        this.mediaId = mediaId;
        this.stars = stars;
        this.review = review;
        this.timestamp = timestamp;
    }
}
