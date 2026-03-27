package com.vellumhub.recommendation_service.module.book_feature.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
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
    @Column(name = "embedding", columnDefinition = "vector(384)")
    private float[] embedding;

    @Column(name = "popularity_score")
    private double popularityScore;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated = Instant.now();

    private BookFeature(UUID bookId, float[] embedding, double popularityScore) {
        this.bookId = bookId;
        this.embedding = embedding;
        this.popularityScore = popularityScore;
        this.lastUpdated = Instant.now();
    }

    public static BookFeature create(UUID bookId, float[] embedding, double popularityScore) {
        if (embedding == null || embedding.length < 384) {
            throw new IllegalArgumentException("Embedding vector cannot be null and must have at least 384 dimensions");
        }
        return new BookFeature(bookId, embedding, popularityScore);
    }

    public void updateEmbedding(float[] newEmbedding) {
        if (embedding == null || embedding.length < 384) {
            throw new IllegalArgumentException("Embedding vector cannot be null and must have at least 384 dimensions");
        }
        this.embedding = newEmbedding;
        this.lastUpdated = Instant.now();
    }

    public void updatePopularity(double newScore) {
        this.popularityScore = newScore;
        this.lastUpdated = Instant.now();
    }
}