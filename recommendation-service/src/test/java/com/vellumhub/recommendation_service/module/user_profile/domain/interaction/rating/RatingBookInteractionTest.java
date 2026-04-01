package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.rating;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatingBookInteractionTest {

    private static final float[] EMBEDDING = new float[384];

    private RatingBookInteraction ratingBookInteraction;
    private BookFeature bookFeature;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        ratingBookInteraction = new RatingBookInteraction();
        bookId = UUID.randomUUID();
        bookFeature = BookFeature.create(bookId, EMBEDDING, 1.0);
    }

    @Test
    void toAdjustment_shouldReturnCorrectBookId() {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 0, 5, true);

        assertThat(result.bookId()).isEqualTo(bookId);
    }

    @Test
    void toAdjustment_shouldReturnCorrectEmbedding() {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 0, 5, true);

        assertThat(result.embedding()).isEqualTo(EMBEDDING);
    }

    @Test
    void toAdjustment_whenNewRating_shouldApplyFullNewCategoryWeight() {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 0, 5, true);

        assertThat(result.adjustment()).isEqualTo(RatingCategory.PROMOTER.getWeight());
    }

    @Test
    void toAdjustment_whenNewRatingIsDetractor_shouldApplyNegativeWeight() {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 0, 1, true);

        assertThat(result.adjustment()).isEqualTo(RatingCategory.DETRACTOR.getWeight());
    }

    @Test
    void toAdjustment_whenNewRatingIsNeutral_shouldApplyNeutralWeight() {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 0, 3, true);

        assertThat(result.adjustment()).isEqualTo(RatingCategory.NEUTRAL.getWeight());
    }

    @Test
    void toAdjustment_whenCategoryChangesFromDetractorToPromoter_shouldReturnDifference() {
        int expected = RatingCategory.PROMOTER.getWeight() - RatingCategory.DETRACTOR.getWeight();

        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 1, 5, false);

        assertThat(result.adjustment()).isEqualTo(expected);
    }

    @Test
    void toAdjustment_whenCategoryChangesFromPromoterToDetractor_shouldReturnNegativeDifference() {
        int expected = RatingCategory.DETRACTOR.getWeight() - RatingCategory.PROMOTER.getWeight();

        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 5, 1, false);

        assertThat(result.adjustment()).isEqualTo(expected);
    }

    @Test
    void toAdjustment_whenCategoryChangesFromNeutralToPromoter_shouldReturnDifference() {
        int expected = RatingCategory.PROMOTER.getWeight() - RatingCategory.NEUTRAL.getWeight();

        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 3, 5, false);

        assertThat(result.adjustment()).isEqualTo(expected);
    }

    @Test
    void toAdjustment_whenCategoryDoesNotChange_shouldReturnZeroAdjustment() {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, 4, 5, false);

        assertThat(result.adjustment()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "4, 5",
            "5, 4",
            "1, 2",
            "2, 1",
            "3, 3"
    })
    void toAdjustment_whenRatingChangesWithinSameCategory_shouldReturnZeroAdjustment(int oldStars, int newStars) {
        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, oldStars, newStars, false);

        assertThat(result.adjustment()).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "5, 1, PROMOTER, DETRACTOR",
            "5, 3, PROMOTER, NEUTRAL",
            "3, 5, NEUTRAL,  PROMOTER",
            "1, 5, DETRACTOR, PROMOTER",
            "1, 3, DETRACTOR, NEUTRAL"
    })
    void toAdjustment_whenCategoryChanges_shouldReturnCorrectDifference(
            int oldStars, int newStars, String oldCategory, String newCategory
    ) {
        int expected = RatingCategory.valueOf(newCategory).getWeight() - RatingCategory.valueOf(oldCategory).getWeight();

        ProfileAdjustment result = ratingBookInteraction.toAdjustment(bookFeature, oldStars, newStars, false);

        assertThat(result.adjustment()).isEqualTo(expected);
    }
}