package com.mrs.recommendation_service.module.book_feature.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "book_features")
@Getter
@Setter
@NoArgsConstructor
public class BookFeature {

    @Id
    private UUID bookId;

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Column(name = "embedding", columnDefinition = "vector(15)")
    private float[] embedding;

    @Column(name = "popularity_score")
    private double popularityScore;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    public BookFeature(UUID bookId, float[] genresVector) {
        this.bookId = bookId;
        this.embedding = genresVector;
    }

    public static BookFeature of(UUID bookId, List<Genre> genres) {
        return new BookFeature(
                bookId,
                defineVector(genres)
        );
    }

    public void update(List<Genre> genres) {
        this.embedding = defineVector(genres);
        this.lastUpdated = Instant.now();
    }


    public static float[] defineVector(List<Genre> genres) {
        float[] vector = new float[Genre.total()];

        if (genres == null || genres.isEmpty()) {
            return vector;
        }

        for (Genre genre : genres) {
            if (genre.index < vector.length) {
                vector[genre.index] = 1.0f;
            }
        }

        return vector;
    }

}