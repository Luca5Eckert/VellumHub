package com.vellumhub.recommendation_service.module.user_profile.domain.interaction.progress;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.user_profile.domain.model.ProfileAdjustment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BookProgressInteractionTest {

    private static final float PAGE_PROGRESS_WEIGHT = 0.0125f;
    private static final float FLOAT_TOLERANCE = 0.0001f;
    private static final float[] EMBEDDING = new float[384];

    private BookProgressInteraction bookProgressInteraction;
    private BookFeature bookFeature;
    private UUID bookId;

    @BeforeEach
    void setUp() {
        bookProgressInteraction = new BookProgressInteraction();
        bookId = UUID.randomUUID();
        bookFeature = BookFeature.create(bookId, EMBEDDING, 1.0);
    }

    @Test
    void toAdjustment_shouldReturnProfileAdjustmentWithCorrectBookId() {
        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, Progress.READING.name(), 0, 10);

        assertThat(result.bookId()).isEqualTo(bookId);
    }

    @Test
    void toAdjustment_shouldReturnProfileAdjustmentWithCorrectEmbedding() {
        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, Progress.READING.name(), 0, 10);

        assertThat(result.embedding()).isEqualTo(EMBEDDING);
    }

    @ParameterizedTest
    @CsvSource({
            "READING,   0,  10",
            "READING,  50, 100",
            "COMPLETED,  0, 300",
    })
    void toAdjustment_shouldCombineTypeAdjustmentAndPageProgress(String progressType, int oldPage, int newPage) {
        float expectedAdjustment = Progress.of(progressType).adjusment + (newPage - oldPage) * PAGE_PROGRESS_WEIGHT;

        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, progressType, oldPage, newPage);

        assertThat(result.adjustment()).isCloseTo(expectedAdjustment, within(FLOAT_TOLERANCE));
    }

    @Test
    void toAdjustment_whenNoPagesRead_shouldOnlyApplyTypeAdjustment() {
        float expectedAdjustment = Progress.of(Progress.READING.name()).adjusment;

        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, Progress.READING.name(), 10, 10);

        assertThat(result.adjustment()).isCloseTo(expectedAdjustment, within(FLOAT_TOLERANCE));
    }

    @Test
    void toAdjustment_whenLargePagesRead_shouldIncreaseAdjustmentProportionally() {
        int newPage = 1000;
        float expectedAdjustment = Progress.of(Progress.READING.name()).adjusment + newPage * PAGE_PROGRESS_WEIGHT;

        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, Progress.READING.name(), 0, newPage);

        assertThat(result.adjustment()).isCloseTo(expectedAdjustment, within(FLOAT_TOLERANCE));
    }

    @Test
    void toAdjustment_whenProgressIsCompleted_shouldApplyCompletedTypeAdjustment() {
        float expectedAdjustment = Progress.of(Progress.COMPLETED.name()).adjusment + 50 * PAGE_PROGRESS_WEIGHT;

        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, Progress.COMPLETED.name(), 0, 50);

        assertThat(result.adjustment()).isCloseTo(expectedAdjustment, within(FLOAT_TOLERANCE));
    }

    @Test
    void toAdjustment_whenProgressIsAbandoned_shouldApplyAbandonedTypeAdjustment() {
        float expectedAdjustment = Progress.of(Progress.READING.name()).adjusment + 20 * PAGE_PROGRESS_WEIGHT;

        ProfileAdjustment result = bookProgressInteraction.toAdjustment(bookFeature, Progress.READING.name(), 0, 20);

        assertThat(result.adjustment()).isCloseTo(expectedAdjustment, within(FLOAT_TOLERANCE));
    }
}