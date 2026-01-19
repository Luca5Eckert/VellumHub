package com.mrs.recommendation_service.domain.model;

import jakarta.persistence.*;
import lombok. Getter;
import lombok.NoArgsConstructor;
import lombok. Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java. time.Instant;
import java.util.*;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    private UUID userId;

    @Version
    private Long version;

    /**
     * Scores por gênero usando JSONB do PostgreSQL
     * Exemplo: {"Terror": 5.0, "Suspense": 3.0}
     */
    @JdbcTypeCode(SqlTypes. JSON)
    @Column(name = "genre_scores", columnDefinition = "jsonb")
    private Map<String, Double> genreScores = new HashMap<>();

    /**
     * IDs das mídias interagidas usando ARRAY do PostgreSQL
     * Set evita duplicatas automaticamente
     */
    @JdbcTypeCode(SqlTypes. ARRAY)
    @Column(name = "interacted_media_ids", columnDefinition = "uuid[]")
    private Set<UUID> interactedMediaIds = new HashSet<>();

    @Column(name = "total_likes")
    private long totalLikes;

    @Column(name = "total_dislikes")
    private long totalDislikes;

    @Column(name = "total_watches")
    private long totalWatches;

    @Column(name = "total_engagement_score")
    private double totalEngagementScore;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserProfile(UUID userId) {
        this.userId = userId;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    public void processInteraction(MediaFeature media, InteractionType type, double interactionValue) {
        if (media.getGenres() != null) {
            for (String genre : media.getGenres()) {
                this.genreScores.merge(genre, type.getWeightInteraction(), Double::sum);
            }
        }

        this.interactedMediaIds.add(media.getMediaId());

        updateCounters(type);
        updateEngagementScore(type, interactionValue);

        this.lastUpdated = Instant. now();
    }

    private void updateCounters(InteractionType type) {
        switch (type) {
            case LIKE -> this.totalLikes++;
            case DISLIKE -> this.totalDislikes++;
            case WATCH -> this.totalWatches++;
        }
    }

    private void updateEngagementScore(InteractionType type, double interactionValue) {
        double weight = type.getWeightInteraction();
        double scoreIncrement = weight * (1 + interactionValue);
        this.totalEngagementScore += scoreIncrement;
    }
}