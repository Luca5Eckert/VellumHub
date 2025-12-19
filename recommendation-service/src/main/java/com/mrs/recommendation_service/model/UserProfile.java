package com.mrs.recommendation_service.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time. Instant;
import java.util.*;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    private UUID userId;

    /**
     * Scores por gênero acumulados das interações
     * Exemplo: {"Terror": 5. 0, "Suspense": 3.0, "Comédia": -1.0}
     */
    @JdbcTypeCode(SqlTypes. JSON)
    @Column(name = "genre_scores", columnDefinition = "jsonb")
    private Map<String, Double> genreScores = new HashMap<>();

    /**
     * IDs das mídias que o usuário já interagiu (evita recomendar novamente)
     */
    @JdbcTypeCode(SqlTypes. ARRAY)
    @Column(name = "interacted_media_ids", columnDefinition = "uuid[]")
    private UUID[] interactedMediaIds = new UUID[0];

    /**
     * Score total de engajamento do usuário
     */
    @Column(name = "total_engagement_score")
    private double totalEngagementScore = 0.0;

    /**
     * Contadores de interações por tipo
     */
    @Column(name = "total_likes")
    private int totalLikes = 0;

    @Column(name = "total_dislikes")
    private int totalDislikes = 0;

    @Column(name = "total_watches")
    private int totalWatches = 0;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserProfile(UUID userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

}