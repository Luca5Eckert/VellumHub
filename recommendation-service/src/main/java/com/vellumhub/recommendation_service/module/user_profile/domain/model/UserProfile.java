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
     * Processes a book rating by determining the engagement score adjustment based on the rating change, updating the user's profile vector using vector learning, and recording the interaction with the book. The method first checks if the rating change results in a category change (e.g., from "Neutral" to "Liked") to determine if an update is necessary. If an update is needed, it calculates the weight adjustment based on the new and old ratings, updates the engagement score, applies vector learning to adjust the profile vector towards or away from the book's embedding, and finally updates the last updated timestamp.
     *
     * @param newStars The new star rating given by the user for the book, which will be used to determine the engagement score adjustment and vector learning direction.
     * @param oldStars The previous star rating for the book, which is used to calculate the difference in engagement score and determine if a category change has occurred.
     * @param isNewRating A boolean flag indicating whether this is a new rating (true) or an update to an existing rating (false), which affects how the method determines if an update is necessary.
     * @param bookEmbedding The embedding vector of the book being rated, which is used in the vector learning process to adjust the user's profile vector based on the rating change.
     * @param bookId The unique identifier of the book being rated, which is added to the set of interacted book IDs to track user interactions for future recommendations.
     */
    public void processBookRating(int newStars, int oldStars, boolean isNewRating, float[] bookEmbedding, UUID bookId) {
        if (!hasCategoryChanged(newStars, oldStars, isNewRating)) {
            return;
        }

        int adjustmentWeight = getWeightAdjustment(newStars, oldStars, isNewRating);

        this.updateEngagementScore(adjustmentWeight, bookId);
        this.applyVectorLearning(bookEmbedding, adjustmentWeight);

        this.lastUpdated = Instant.now();
    }

    private int getWeightAdjustment(int newStars, int oldStars, boolean isNewRating) {
        int newWeight = RatingCategory.fromStars(newStars).getWeight();
        int oldWeight = isNewRating ? 0 : RatingCategory.fromStars(oldStars).getWeight();

        return newWeight - oldWeight;
    }

    private boolean hasCategoryChanged(int newStars, int oldStars, boolean isNewRating){
        if (isNewRating) return true;
        return RatingCategory.fromStars(oldStars) != RatingCategory.fromStars(newStars);
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