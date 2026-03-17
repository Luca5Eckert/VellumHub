package com.mrs.recommendation_service.module.user_profile.domain.model;

import com.mrs.recommendation_service.module.user_profile.domain.command.UpdateUserProfileWithRatingCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

    @Column(columnDefinition = "vector(15)")
    private float[] profileVector = new float[15];

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
     * Adjusts the user's engagement score based on the rating change and updates the profile vector accordingly.
     * @param command The command containing details about the rating change, including the old and new star ratings, and whether it's a new rating.
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

    /**
     * Applies a vector adjustment to the user's profile vector based on the book embedding and the rating adjustment.
     * @param bookEmbedding The embedding vector of the book that the user interacted with.
     * @param adjustment The weight adjustment derived from the rating change, which indicates how much to adjust the profile vector in the direction of the book embedding.
     */
    public void applyVectorAdjustment(float[] bookEmbedding, int adjustment) {
        float learningRate = 0.1f;
        double sumOfSquares = 0.0;

        for (int i = 0; i < this.profileVector.length; i++) {
            this.profileVector[i] += (bookEmbedding[i] * adjustment * learningRate);
            sumOfSquares += (this.profileVector[i] * this.profileVector[i]);
        }

        this.normalizeProfileVector(sumOfSquares);
    }


    /**
     * Normalizes the user's profile vector to ensure it has a magnitude of 1.
     */
    private void normalizeProfileVector(double sumOfSquares) {
        float magnitude = (float) Math.sqrt(sumOfSquares);

        if (magnitude > 0) {
            for (int i = 0; i < this.profileVector.length; i++) {
                this.profileVector[i] /= magnitude;
            }
        }
    }

}