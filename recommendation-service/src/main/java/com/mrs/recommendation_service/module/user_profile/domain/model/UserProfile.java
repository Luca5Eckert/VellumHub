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
     * Processes a book rating and adjusts the entire state of the user profile.
     * This method encapsulates the business rules for updating a user's semantic preferences.
     *
     * @param command       The command containing details about the rating change.
     * @param bookEmbedding The dense semantic vector (embedding) of the rated book.
     */
    public void processBookRating(UpdateUserProfileWithRatingCommand command, float[] bookEmbedding) {
        if (!command.hasCategoryChanged()) {
            return;
        }

        int adjustmentWeight = command.getWeightAdjustment();

        this.updateEngagementScore(adjustmentWeight, command.mediaId());

        this.applyVectorLearning(bookEmbedding, adjustmentWeight);

        this.lastUpdated = Instant.now();
    }


    /**
     * Updates the user's total engagement score based on the rating adjustment and records the interacted book ID.
     * @param weightAdjustment The calculated weight adjustment based on the rating change.
     * @param bookId  The ID of the book that the user interacted with, which will be added to the set of interacted books.
     */
    private void updateEngagementScore(int weightAdjustment, UUID bookId) {
        this.totalEngagementScore += weightAdjustment;
        this.interactedBookIds.add(bookId);
    }

    private void applyVectorLearning(float[] bookEmbedding, int adjustmentWeight) {
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