package com.mrs.recommendation_service.domain.model;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "media_features")
@Getter
@Setter
@NoArgsConstructor
public class MediaFeature {

    @Id
    private UUID mediaId;

    @Column(columnDefinition = "vector(5)")
    private float[] embedding;

    @Column(name = "popularity_score")
    private double popularityScore;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    public MediaFeature(UUID uuid, float[] genresVector) {
        this.mediaId = uuid;
        this.embedding = genresVector;
    }

    public void update(List<Genre> genres) {
        float[] newEmbedding = new float[Genre.total()];

        if (genres != null) {
            for (Genre genre : genres) {
                newEmbedding[genre.index] = 1.0f;
        }

    }

        this.embedding =newEmbedding;
        this.lastUpdated =Instant.now();
}

}