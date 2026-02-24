package com.mrs.catalog_service.module.book.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "books")
@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@DynamicUpdate
@EntityListeners(AuditingEntityListener.class)
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private int releaseYear;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String isbn;

    @Column(name = "page_count")
    private int pageCount;

    @Column(nullable = false)
    private String publisher;

    @Version
    private long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant deletedAt;

    @ElementCollection
    @CollectionTable(
            name = "tb_book_genre",
            joinColumns = @JoinColumn(name = "book_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "genre_name")
    @Builder.Default
    private List<Genre> genres = new ArrayList<>();

    /**
     * Domain method to update the entity.
     * Ensures all business rules are validated before state change.
     */
    public void update(
            String title,
            String description,
            String coverUrl,
            Integer releaseYear,
            String author,
            String isbn,
            Integer pageCount,
            String publisher,
            List<Genre> genres
    ) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }

        if (description != null && !description.isBlank()) {
            this.description = description;
        }

        if (coverUrl != null && !coverUrl.isBlank()) {
            this.coverUrl = coverUrl;
        }

        if (releaseYear != null && releaseYear > 0) {
            this.releaseYear = releaseYear;
        }

        if (author != null && !author.isBlank()) {
            this.author = author;
        }

        if (isbn != null && !isbn.isBlank()) {
            this.isbn = isbn;
        }

        if (pageCount != null && pageCount > 0) {
            this.pageCount = pageCount;
        }

        if (publisher != null && !publisher.isBlank()) {
            this.publisher = publisher;
        }

        if (genres != null && !genres.isEmpty()) {
            this.genres = new ArrayList<>(genres);
        }
    }

    /**
     * Domain method to update only the cover URL.
     */
    public void updateCoverUrl(String coverUrl) {
        if (coverUrl != null && !coverUrl.isBlank()) {
            this.coverUrl = coverUrl;
        }
    }

}