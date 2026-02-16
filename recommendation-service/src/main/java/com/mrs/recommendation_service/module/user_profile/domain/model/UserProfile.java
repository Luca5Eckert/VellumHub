package com.mrs.recommendation_service.module.user_profile.domain.model;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor
public class UserProfile {

    @Id
    private UUID userId;

    @Column(columnDefinition = "vector(5)")
    private float[] profileVector = new float[10];

    @Column(name = "interacted_media_ids", columnDefinition = "uuid[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
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

    public void processInteraction(BookFeature book, InteractionType type, double interactionValue) {
        float[] mediaVector = book.getEmbedding();
        double weight = type.getWeightInteraction();

        for (int i = 0; i < profileVector.length; i++) {
            this.profileVector[i] += (float) (mediaVector[i] * weight * (1 + interactionValue));
        }

        this.interactedMediaIds.add(book.getBookId());
        this.lastUpdated = Instant.now();
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