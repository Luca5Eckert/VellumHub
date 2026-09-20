package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.reaction;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @ParameterizedTest
    @CsvSource(
            value = {
                    "null,VERY_POSITIVE,3.0",
                    "null,POSITIVE,1.5",
                    "null,NEGATIVE,-0.5",
                    "POSITIVE,VERY_POSITIVE,1.5",
                    "VERY_POSITIVE,POSITIVE,-1.5",
                    "POSITIVE,NEGATIVE,-2.0",
                    "NEGATIVE,VERY_POSITIVE,3.5",
                    "POSITIVE,POSITIVE,0.0",
                    "NEGATIVE,NEGATIVE,0.0"
            },
            nullValues = "null"
    )
    void toAdjustmentShouldApplyOnlyReactionTransitionDelta(
            String oldReactionType,
            String newReactionType,
            float expectedAdjustment
    ) {
        ProfileAdjustment result = reactionBookInteraction.toAdjustment(
                bookFeature,
                oldReactionType,
                newReactionType
        );

        assertThat(result.bookId()).isEqualTo(bookId);
        assertThat(result.embedding()).isEqualTo(EMBEDDING);
        assertThat(result.adjustment()).isEqualTo(expectedAdjustment);
    }

    @Test
    void toAdjustmentShouldRejectInvalidNewReactionType() {
        assertThatThrownBy(() -> reactionBookInteraction.toAdjustment(
                bookFeature,
                Reaction.POSITIVE.name(),
                "INVALID"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toAdjustmentShouldRejectInvalidPreviousReactionType() {
        assertThatThrownBy(() -> reactionBookInteraction.toAdjustment(
                bookFeature,
                "INVALID",
                Reaction.POSITIVE.name()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
