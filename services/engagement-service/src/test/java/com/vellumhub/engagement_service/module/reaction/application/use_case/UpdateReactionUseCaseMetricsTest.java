package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import com.vellumhub.engagement_service.module.reaction.application.event.ReactionEventRecorder;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateReactionUseCaseMetricsTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private ReactionEventRecorder recorder;

    private UpdateReactionUseCase useCase;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new UpdateReactionUseCase(
                reactionRepository,
                recorder,
                new VellumHubMetrics(meterRegistry)
        );
    }

    @Test
    @DisplayName("Should publish the complete previous-to-current reaction transition")
    void shouldPublishCompleteReactionTransition() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        long reactionId = 42L;
        Instant createdAt = Instant.parse("2026-09-19T12:00:00Z");
        var reaction = reaction(reactionId, userId, bookId, TypeReaction.POSITIVE, createdAt);
        when(reactionRepository.findByIdForUpdate(reactionId)).thenReturn(Optional.of(reaction));
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new UpdateReactionCommand(userId, reactionId, TypeReaction.VERY_POSITIVE));

        verify(reactionRepository).save(reaction);
        verify(recorder).changed(reaction,TypeReaction.POSITIVE);

        assertThat(result.oldTypeReaction()).isEqualTo(TypeReaction.POSITIVE);
        assertThat(result.reaction()).isSameAs(reaction);
        assertThat(reaction.getTypeReaction()).isEqualTo(TypeReaction.VERY_POSITIVE);
        assertThat(reaction.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reaction.getUpdatedAt()).isAfter(createdAt);

        assertThat(reactionsChangedCount("reaction_update")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should preserve an explicit same-value reaction transition")
    void shouldNotPublishUnchangedReactionTransition() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        long reactionId = 43L;
        Instant createdAt = Instant.parse("2026-09-19T12:00:00Z");
        var reaction = reaction(reactionId, userId, bookId, TypeReaction.POSITIVE, createdAt);
        when(reactionRepository.findByIdForUpdate(reactionId)).thenReturn(Optional.of(reaction));
        useCase.execute(new UpdateReactionCommand(userId, reactionId, TypeReaction.POSITIVE));
        org.mockito.Mockito.verifyNoInteractions(recorder);
        org.mockito.Mockito.verify(reactionRepository, org.mockito.Mockito.never())
                .save(org.mockito.ArgumentMatchers.any());
    }

    private Reaction reaction(
            long reactionId,
            UUID userId,
            UUID bookId,
            TypeReaction typeReaction,
            Instant createdAt
    ) {
        return Reaction.builder()
                .id(reactionId)
                .userId(userId)
                .bookSnapshot(new BookSnapshot(bookId))
                .typeReaction(typeReaction)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private double reactionsChangedCount(String operation) {
        return meterRegistry.get(VellumHubMetrics.REACTIONS_CHANGED)
                .tag("operation", operation)
                .tag("result", "success")
                .counter()
                .count();
    }
}

