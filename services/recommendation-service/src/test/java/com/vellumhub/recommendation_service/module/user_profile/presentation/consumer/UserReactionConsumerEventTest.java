package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.recommendation_service.module.user_profile.application.command.ReactionChangedCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.ReactionChangedUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserReactionConsumerEventTest {

    @Mock
    private ReactionChangedUseCase reactionChangedUseCase;

    @Mock
    private VellumHubMetrics metrics;

    @InjectMocks
    private UserReactionConsumerEvent userReactionConsumerEvent;

    @Captor
    private ArgumentCaptor<ReactionChangedCommand> commandCaptor;

    @Test
    @DisplayName("Should pass the complete reaction transition to the use case")
    void shouldPassCompleteTransitionToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ReactionChangedEvent event = enrichedEvent(
                userId,
                bookId,
                "POSITIVE",
                "VERY_POSITIVE"
        );

        userReactionConsumerEvent.consume(event);

        verify(reactionChangedUseCase).execute(commandCaptor.capture());
        ReactionChangedCommand command = commandCaptor.getValue();

        assertThat(command.userId()).isEqualTo(userId);
        assertThat(command.bookId()).isEqualTo(bookId);
        assertThat(command.oldReactionType()).isEqualTo("POSITIVE");
        assertThat(command.newReactionType()).isEqualTo("VERY_POSITIVE");
    }

    @Test
    @DisplayName("Should preserve same-value transitions for zero-delta handling")
    void shouldPassSameValueTransitionToUseCase() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        userReactionConsumerEvent.consume(enrichedEvent(
                userId,
                bookId,
                "POSITIVE",
                "POSITIVE"
        ));

        verify(reactionChangedUseCase).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().oldReactionType()).isEqualTo("POSITIVE");
        assertThat(commandCaptor.getValue().newReactionType()).isEqualTo("POSITIVE");
    }

    @Test
    @DisplayName("Should interpret a reaction creation as having no previous type")
    void shouldPassCreationWithoutSyntheticPreviousReaction() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();

        userReactionConsumerEvent.consume(enrichedEvent(
                userId,
                bookId,
                null,
                "NEGATIVE"
        ));

        verify(reactionChangedUseCase).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().oldReactionType()).isNull();
        assertThat(commandCaptor.getValue().newReactionType()).isEqualTo("NEGATIVE");
    }

    @Test
    @DisplayName("Should remain compatible with the legacy reaction payload")
    @SuppressWarnings("removal")
    void shouldConsumeLegacyReactionPayload() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        ReactionChangedEvent legacyEvent = new ReactionChangedEvent(userId, bookId, "POSITIVE");

        userReactionConsumerEvent.consume(legacyEvent);

        verify(reactionChangedUseCase).execute(commandCaptor.capture());
        assertThat(commandCaptor.getValue().oldReactionType()).isNull();
        assertThat(commandCaptor.getValue().newReactionType()).isEqualTo("POSITIVE");
    }

    @Test
    @DisplayName("Should reject a payload without any resulting reaction type")
    void shouldRejectPayloadWithoutResultingReaction() {
        ReactionChangedEvent invalid = new ReactionChangedEvent(
                UUID.randomUUID(),
                Instant.now(),
                1L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "POSITIVE",
                null,
                null
        );

        assertThatThrownBy(() -> userReactionConsumerEvent.consume(invalid))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reaction change event must include a resulting reaction type");

        verify(reactionChangedUseCase, never()).execute(any());
    }

    @Test
    @DisplayName("Should propagate exception when use case fails")
    void shouldPropagateExceptionWhenUseCaseFails() {
        ReactionChangedEvent event = enrichedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "POSITIVE",
                "NEGATIVE"
        );
        doThrow(new RuntimeException("Profile update failed"))
                .when(reactionChangedUseCase)
                .execute(any());

        assertThatThrownBy(() -> userReactionConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Profile update failed");
    }

    private ReactionChangedEvent enrichedEvent(
            UUID userId,
            UUID bookId,
            String oldType,
            String newType
    ) {
        return new ReactionChangedEvent(
                UUID.randomUUID(),
                Instant.now(),
                42L,
                userId,
                bookId,
                oldType,
                newType
        );
    }
}
