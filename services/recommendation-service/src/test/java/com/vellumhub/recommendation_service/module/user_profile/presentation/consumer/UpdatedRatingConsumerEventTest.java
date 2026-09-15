package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.kafka.contracts.engagement.UpdatedRatingEvent;
import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateUserProfileWithRatingCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateUserProfileWithRatingUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UpdatedRatingConsumerEventTest {

    @Mock
    private UpdateUserProfileWithRatingUseCase updateUserProfileWithRatingUseCase;

    @Mock
    private VellumHubMetrics metrics;

    @InjectMocks
    private UpdatedRatingConsumerEvent updatedRatingConsumerEvent;

    @Captor
    private ArgumentCaptor<UpdateUserProfileWithRatingCommand> commandCaptor;

    @Test
    void shouldPassSameBucketTransitionToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        updatedRatingConsumerEvent.consume(updatedRating(userId, bookId, 4, 5));

        verify(updateUserProfileWithRatingUseCase).execute(commandCaptor.capture());
        UpdateUserProfileWithRatingCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.oldStars()).isEqualTo(4);
        assertThat(command.newStars()).isEqualTo(5);
        assertThat(command.isNewRating()).isFalse();
    }

    @Test
    void shouldPassCrossBucketTransitionToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        updatedRatingConsumerEvent.consume(updatedRating(userId, bookId, 2, 4));

        verify(updateUserProfileWithRatingUseCase).execute(commandCaptor.capture());
        UpdateUserProfileWithRatingCommand command = commandCaptor.getValue();

        assertThat(command.oldStars()).isEqualTo(2);
        assertThat(command.newStars()).isEqualTo(4);
        assertThat(command.isNewRating()).isFalse();
    }

    @Test
    void shouldRejectUpdatedRatingWithoutOldStars() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UpdatedRatingEvent event = new UpdatedRatingEvent(
                UUID.randomUUID(),
                Instant.now(),
                1L,
                userId,
                bookId,
                null,
                4,
                false
        );

        assertThatThrownBy(() -> updatedRatingConsumerEvent.consume(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Updated rating event must include oldStars");

        verify(updateUserProfileWithRatingUseCase, never()).execute(any());
    }

    @Test
    void shouldPropagateExceptionWhenUseCaseFails() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        UpdatedRatingEvent event = updatedRating(userId, bookId, 2, 4);
        doThrow(new RuntimeException("Profile update failed"))
                .when(updateUserProfileWithRatingUseCase)
                .execute(any());

        assertThatThrownBy(() -> updatedRatingConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Profile update failed");
    }

    private UpdatedRatingEvent updatedRating(UUID userId, UUID bookId, int oldStars, int newStars) {
        return new UpdatedRatingEvent(
                UUID.randomUUID(),
                Instant.now(),
                1L,
                userId,
                bookId,
                oldStars,
                newStars,
                false
        );
    }
}
