package com.mrs.catalog_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "medias")
@Getter
@Setter
// Improvement: Avoids circular references and performance issues by using only ID for equality
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
// Improvement: Automatically handles createdAt and updatedAt
@EntityListeners(AuditingEntityListener.class)
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // Fixed: IDENTITY does not work well with UUIDs
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000) // Improvement: Define explicit length for descriptions
    private String description;

    @Column(nullable = false)
    private int releaseYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;

    private String coverUrl;

    @Version
    private long version;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt; // Renamed to standard 'createdAt'

    @LastModifiedDate
    private Instant updatedAt; // Renamed to standard 'updatedAt'

    private Instant deletedAt;

    @ElementCollection
    @CollectionTable(
            name = "tb_media_genre",
            joinColumns = @JoinColumn(name = "media_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "genre_name")
    @Builder.Default // Ensures the builder initializes this as an empty list if null
    private List<Genre> genres = new ArrayList<>();

    /**
     * Domain method to update the entity.
     * This keeps the business logic inside the domain (Rich Domain Model).
     */
    public void update(
            String title,
            String description,
            String coverUrl,
            Integer releaseYear,
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

        if (releaseYear != null) {
            if (releaseYear <= 0) {
                throw new IllegalArgumentException("Release year must be positive");
            }
            this.releaseYear = releaseYear;
        }

        if (genres != null) {
            if (genres.isEmpty()) {
                throw new IllegalArgumentException("Media must have at least one genre");
            }
            this.genres = new ArrayList<>(genres);
        }
    }
}