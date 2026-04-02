package com.vellumhub.recommendation_service.module.user_profile.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class UserProfileTest {

    private static final int VECTOR_SIZE = 384;
    private static final UUID USER_ID = UUID.randomUUID();

    private float[] unitBookEmbedding;

    @BeforeEach
    void setUp() {
        unitBookEmbedding = new float[VECTOR_SIZE];
        unitBookEmbedding[0] = 1.0f;
    }

    private float[] uniformEmbedding(float value) {
        float[] embedding = new float[VECTOR_SIZE];
        Arrays.fill(embedding, value);
        return embedding;
    }

    private double magnitude(float[] vector) {
        double sum = 0.0;
        for (float v : vector) sum += v * v;
        return Math.sqrt(sum);
    }

    @Test
    void shouldCreateProfileWithDefaultValuesViaUuidConstructor() {
        UserProfile profile = new UserProfile(USER_ID);

        assertThat(profile.getUserId()).isEqualTo(USER_ID);
        assertThat(profile.getTotalEngagementScore()).isEqualTo(0.0);
        assertThat(profile.getInteractedBookIds()).isEmpty();
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getLastUpdated()).isNotNull();
    }

    @Test
    void shouldCreateProfileWithVectorViaFactoryMethod() {
        float[] vectors = uniformEmbedding(0.5f);

        UserProfile profile = UserProfile.create(USER_ID, vectors);

        assertThat(profile.getUserId()).isEqualTo(USER_ID);
        assertThat(profile.getProfileVector()).isEqualTo(vectors);
        assertThat(profile.getTotalEngagementScore()).isEqualTo(0.0);
        assertThat(profile.getInteractedBookIds()).isEmpty();
        assertThat(profile.getCreatedAt()).isNotNull();
        assertThat(profile.getLastUpdated()).isNotNull();
    }

    @Test
    void shouldIncrementEngagementScoreOnPositiveAdjustment() {
        UserProfile profile = new UserProfile(USER_ID);
        UUID bookId = UUID.randomUUID();
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 2.0f, unitBookEmbedding);

        profile.applyUpdate(adjustment);

        assertThat(profile.getTotalEngagementScore()).isEqualTo(2.0);
    }

    @Test
    void shouldDecrementEngagementScoreOnNegativeAdjustment() {
        UserProfile profile = new UserProfile(USER_ID);
        UUID bookId = UUID.randomUUID();
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, -1.5f, unitBookEmbedding);

        profile.applyUpdate(adjustment);

        assertThat(profile.getTotalEngagementScore()).isEqualTo(-1.5);
    }

    @Test
    void shouldAccumulateEngagementScoreAcrossMultipleUpdates() {
        UserProfile profile = new UserProfile(USER_ID);
        ProfileAdjustment first = new ProfileAdjustment(UUID.randomUUID(), 3.0f, unitBookEmbedding);
        ProfileAdjustment second = new ProfileAdjustment(UUID.randomUUID(), 2.0f, unitBookEmbedding);

        profile.applyUpdate(first);
        profile.applyUpdate(second);

        assertThat(profile.getTotalEngagementScore()).isEqualTo(5.0);
    }

    @Test
    void shouldNotChangeEngagementScoreOnZeroAdjustment() {
        UserProfile profile = new UserProfile(USER_ID);
        ProfileAdjustment adjustment = new ProfileAdjustment(UUID.randomUUID(), 0.0f, unitBookEmbedding);

        profile.applyUpdate(adjustment);

        assertThat(profile.getTotalEngagementScore()).isEqualTo(0.0);
    }


    @Test
    void shouldRegisterInteractedBookId() {
        UserProfile profile = new UserProfile(USER_ID);
        UUID bookId = UUID.randomUUID();
        ProfileAdjustment adjustment = new ProfileAdjustment(bookId, 1.0f, unitBookEmbedding);

        profile.applyUpdate(adjustment);

        assertThat(profile.getInteractedBookIds()).containsExactly(bookId);
    }

    @Test
    void shouldNotDuplicateBookIdOnRepeatedInteraction() {
        UserProfile profile = new UserProfile(USER_ID);
        UUID bookId = UUID.randomUUID();
        ProfileAdjustment first = new ProfileAdjustment(bookId, 1.0f, unitBookEmbedding);
        ProfileAdjustment second = new ProfileAdjustment(bookId, 1.0f, unitBookEmbedding);

        profile.applyUpdate(first);
        profile.applyUpdate(second);

        assertThat(profile.getInteractedBookIds()).hasSize(1).containsExactly(bookId);
    }

    @Test
    void shouldTrackMultipleDistinctBookIds() {
        UserProfile profile = new UserProfile(USER_ID);
        UUID bookA = UUID.randomUUID();
        UUID bookB = UUID.randomUUID();

        profile.applyUpdate(new ProfileAdjustment(bookA, 1.0f, unitBookEmbedding));
        profile.applyUpdate(new ProfileAdjustment(bookB, 1.0f, unitBookEmbedding));

        assertThat(profile.getInteractedBookIds()).containsExactlyInAnyOrder(bookA, bookB);
    }

    // -------------------------------------------------------------------------
    // applyUpdate — vector learning & normalization
    // -------------------------------------------------------------------------

    @Test
    void shouldProduceUnitMagnitudeVectorAfterUpdate() {
        UserProfile profile = UserProfile.create(USER_ID, uniformEmbedding(0.1f));
        ProfileAdjustment adjustment = new ProfileAdjustment(UUID.randomUUID(), 1.0f, uniformEmbedding(0.5f));

        profile.applyUpdate(adjustment);

        assertThat(magnitude(profile.getProfileVector())).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void shouldMaintainUnitMagnitudeAfterMultipleUpdates() {
        UserProfile profile = UserProfile.create(USER_ID, uniformEmbedding(0.1f));

        for (int i = 0; i < 10; i++) {
            profile.applyUpdate(new ProfileAdjustment(UUID.randomUUID(), 1.0f, uniformEmbedding(0.5f)));
        }

        assertThat(magnitude(profile.getProfileVector())).isCloseTo(1.0, within(1e-5));
    }

    @Test
    void shouldShiftProfileVectorTowardBookEmbeddingOnPositiveAdjustment() {
        float[] initial = new float[VECTOR_SIZE];
        initial[0] = 1.0f;
        UserProfile profile = UserProfile.create(USER_ID, initial);

        float[] bookEmbedding = new float[VECTOR_SIZE];
        bookEmbedding[1] = 1.0f;
        ProfileAdjustment adjustment = new ProfileAdjustment(UUID.randomUUID(), 1.0f, bookEmbedding);

        profile.applyUpdate(adjustment);

        assertThat(profile.getProfileVector()[1]).isGreaterThan(0.0f);
    }

    @Test
    void shouldShiftProfileVectorAwayFromBookEmbeddingOnNegativeAdjustment() {
        float[] initial = new float[VECTOR_SIZE];
        initial[0] = 1.0f;
        UserProfile profile = UserProfile.create(USER_ID, initial);

        float[] bookEmbedding = new float[VECTOR_SIZE];
        bookEmbedding[0] = 1.0f;
        float originalComponent = profile.getProfileVector()[0];

        ProfileAdjustment adjustment = new ProfileAdjustment(UUID.randomUUID(), -1.0f, bookEmbedding);
        profile.applyUpdate(adjustment);

        assertThat(profile.getProfileVector()[0]).isLessThanOrEqualTo(originalComponent);
    }

    @Test
    void shouldNotMutateBookEmbeddingDuringVectorLearning() {
        float[] bookEmbedding = uniformEmbedding(0.5f);
        float[] copy = bookEmbedding.clone();
        UserProfile profile = UserProfile.create(USER_ID, uniformEmbedding(0.1f));

        profile.applyUpdate(new ProfileAdjustment(UUID.randomUUID(), 1.0f, bookEmbedding));

        assertThat(bookEmbedding).isEqualTo(copy);
    }

    // -------------------------------------------------------------------------
    // applyUpdate — lastUpdated timestamp
    // -------------------------------------------------------------------------

    @Test
    void shouldUpdateLastUpdatedTimestampAfterApplyUpdate() throws InterruptedException {
        UserProfile profile = new UserProfile(USER_ID);
        Instant before = profile.getLastUpdated();
        Thread.sleep(10);

        profile.applyUpdate(new ProfileAdjustment(UUID.randomUUID(), 1.0f, unitBookEmbedding));

        assertThat(profile.getLastUpdated()).isAfter(before);
    }

    @Test
    void shouldNotModifyCreatedAtTimestampAfterApplyUpdate() {
        UserProfile profile = new UserProfile(USER_ID);
        Instant createdAt = profile.getCreatedAt();

        profile.applyUpdate(new ProfileAdjustment(UUID.randomUUID(), 1.0f, unitBookEmbedding));

        assertThat(profile.getCreatedAt()).isEqualTo(createdAt);
    }


    @Test
    void shouldThrowWhenBookEmbeddingIsNull() {
        UserProfile profile = new UserProfile(USER_ID);
        ProfileAdjustment adjustment = new ProfileAdjustment(UUID.randomUUID(), 1.0f, null);

        assertThatThrownBy(() -> profile.applyUpdate(adjustment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Book embedding dimension must match the profile vector dimension.");
    }

    @Test
    void shouldThrowWhenBookEmbeddingDimensionMismatch() {
        UserProfile profile = new UserProfile(USER_ID);
        float[] wrongSize = new float[100];
        ProfileAdjustment adjustment = new ProfileAdjustment(UUID.randomUUID(), 1.0f, wrongSize);

        assertThatThrownBy(() -> profile.applyUpdate(adjustment))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Book embedding dimension must match the profile vector dimension.");
    }
    @Test
    void shouldNotThrowWhenProfileVectorIsZeroAfterNegativeAdjustment() {
        float[] zeroVector = new float[VECTOR_SIZE];
        UserProfile profile = UserProfile.create(USER_ID, zeroVector);
        float[] bookEmbedding = new float[VECTOR_SIZE];

        assertThatCode(() ->
                profile.applyUpdate(new ProfileAdjustment(UUID.randomUUID(), -1.0f, bookEmbedding))
        ).doesNotThrowAnyException();
    }
}