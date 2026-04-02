package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReactionBookInteractionTest {

    private static final float[] EMBEDDING = new float[384];

    private ReactionBookInteraction reactionBookInteraction;
    private BookFeature bookFeature;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        reactionBookInteraction = new ReactionBookInteraction();
        bookId = UUID.randomUUID();
        bookFeature = BookFeature.create(bookId, EMBEDDING, 1.0);
    }

    @Test
    void toAdjustment_shouldReturnCorrectBookId() {
        ProfileAdjustment result = reactionBookInteraction.toAdjustment(bookFeature, Reaction.POSITIVE.name());

        assertThat(result.bookId()).isEqualTo(bookId);
    }

    @Test
    void toAdjustment_shouldReturnCorrectEmbedding() {
        ProfileAdjustment result = reactionBookInteraction.toAdjustment(bookFeature, Reaction.POSITIVE.name());

        assertThat(result.embedding()).isEqualTo(EMBEDDING);
    }

    @Test
    void toAdjustment_whenVeryPositive_shouldReturnHighestAdjustment() {
        ProfileAdjustment result = reactionBookInteraction.toAdjustment(bookFeature, Reaction.VERY_POSITIVE.name());

        assertThat(result.adjustment()).isEqualTo(Reaction.VERY_POSITIVE.adjustmentValue);
    }

    @Test
    void toAdjustment_whenPositive_shouldReturnPositiveAdjustment() {
        ProfileAdjustment result = reactionBookInteraction.toAdjustment(bookFeature, Reaction.POSITIVE.name());

        assertThat(result.adjustment()).isEqualTo(Reaction.POSITIVE.adjustmentValue);
    }

    @Test
    void toAdjustment_whenNegative_shouldReturnNegativeAdjustment() {
        ProfileAdjustment result = reactionBookInteraction.toAdjustment(bookFeature, Reaction.NEGATIVE.name());

        assertThat(result.adjustment()).isEqualTo(Reaction.NEGATIVE.adjustmentValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERY_POSITIVE", "POSITIVE", "NEGATIVE"})
    void toAdjustment_shouldMapEachReactionToItsAdjustmentValue(String reactionType) {
        float expectedAdjustment = Reaction.of(reactionType).adjustmentValue;

        ProfileAdjustment result = reactionBookInteraction.toAdjustment(bookFeature, reactionType);

        assertThat(result.adjustment()).isEqualTo(expectedAdjustment);
    }

    @Test
    void toAdjustment_whenInvalidReactionType_shouldThrowException() {
        assertThatThrownBy(() -> reactionBookInteraction.toAdjustment(bookFeature, "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}