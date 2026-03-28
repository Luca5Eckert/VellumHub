package com.vellumhub.recommendation_service.module.user_profile.domain.model;

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

    @Column(columnDefinition = "vector(384)")
    @JdbcTypeCode(SqlTypes.VECTOR)
    private float[] profileVector = new float[384];

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
     * Applies the provided profile adjustment to the user's profile by updating the engagement score and applying vector learning based on the book embedding and adjustment weight. The method updates the total engagement score using the weight adjustment and records the interaction with the specified book ID. It then applies vector learning to adjust the user's profile vector based on the book embedding and adjustment weight, followed by normalizing the profile vector to maintain a magnitude of 1.0 for accurate similarity calculations in future recommendations. Finally, it updates the last updated timestamp to reflect the time of the profile update.
     *
     * @param profileAdjustment The profile adjustment containing the necessary information to update the user's profile, including the weight adjustment for engagement score, the book ID of the interacted book, and the embedding vector for vector learning. The method uses this information to update the user's profile accordingly.
     */
    public void applyUpdate(ProfileAdjustment profileAdjustment) {
        this.updateEngagementScore(profileAdjustment.adjustment(), profileAdjustment.bookId());
        this.applyVectorLearning(profileAdjustment.embedding(), profileAdjustment.adjustment());

        this.lastUpdated = Instant.now();
    }


    /**
     * Updates the user's total engagement score based on the provided weight adjustment and records the interaction with the specified book ID. The method adds the weight adjustment to the total engagement score and adds the book ID to the set of interacted book IDs, which helps track user interactions for future recommendations.
     *
     * @param weightAdjustment The calculated adjustment to the engagement score based on the user's rating change, which is added to the total engagement score to reflect the user's level of engagement with the book.
     * @param bookId The unique identifier of the book that the user interacted with, which is added to the set of interacted book IDs to keep track of the user's interactions for future recommendation algorithms.
     */
    private void updateEngagementScore(float weightAdjustment, UUID bookId) {
        this.totalEngagementScore += weightAdjustment;
        this.interactedBookIds.add(bookId);
    }

    private void applyVectorLearning(float[] bookEmbedding, float adjustmentWeight) {
        if (bookEmbedding == null || bookEmbedding.length != this.profileVector.length) {
            throw new IllegalArgumentException("Book embedding dimension must match the profile vector dimension.");
        }

        float learningRate = 0.1f;
        double sumOfSquares = 0.0;

        for (int i = 0; i < this.profileVector.length; i++) {
            this.profileVector[i] += (bookEmbedding[i] * adjustmentWeight * learningRate);
            sumOfSquares += (this.profileVector[i] * this.profileVector[i]);
        }

        this.normalizeVector(sumOfSquares);
    }

    /**
     * Applies L2 Normalization to ensure the vector maintains a magnitude of 1.0.
     * This is required for accurate Cosine Similarity searches in the database.
     */
    private void normalizeVector(double sumOfSquares) {
        float magnitude = (float) Math.sqrt(sumOfSquares);

        if (magnitude > 0) {
            for (int i = 0; i < this.profileVector.length; i++) {
                this.profileVector[i] /= magnitude;
            }
        }
    }

}