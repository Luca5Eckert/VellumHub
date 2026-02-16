package com.mrs.recommendation_service.module.user_profile.domain.model;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileWithRatingCommand;
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
@Getter
@NoArgsConstructor
public class UserProfile {

    @Id
    private UUID userId;

    @Column(columnDefinition = "vector(5)")
    private float[] profileVector = new float[5];

    @Column(name = "interacted_book_ids", columnDefinition = "uuid[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private Set<UUID> interactedBookIds = new HashSet<>();

    @Column(name = "total_engagement_score")
    private double totalEngagementScore;

    @Column(name = "last_updated", nullable = false)
    private Instant lastUpdated;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserProfile(UUID userId) {
        this.userId = userId;
        this.totalEngagementScore = 0.0;
        this.createdAt = Instant.now();
        this.lastUpdated = Instant.now();
    }

    /**
     * Aplica a lógica de transição de faixas de avaliação (estrelas).
     */
    public void updateScoreByRating(UpdateUserProfileWithRatingCommand command) {
        if (!command.hasCategoryChanged()) {
            return;
        }

        int adjustment = command.getWeightAdjustment();

        this.totalEngagementScore += adjustment;
        this.lastUpdated = Instant.now();

        this.interactedBookIds.add(command.mediaId());
    }


    public void processInteraction(BookFeature book, InteractionType type, double interactionValue) {
        float[] mediaVector = book.getEmbedding();
        double weight = type.getWeightInteraction();

        int length = Math.min(profileVector.length, mediaVector.length);
        for (int i = 0; i < length; i++) {
            this.profileVector[i] += (float) (mediaVector[i] * weight * (1 + interactionValue));
        }

        this.totalEngagementScore += (weight * (1 + interactionValue));
        this.interactedBookIds.add(book.getBookId());
        this.lastUpdated = Instant.now();
    }

}