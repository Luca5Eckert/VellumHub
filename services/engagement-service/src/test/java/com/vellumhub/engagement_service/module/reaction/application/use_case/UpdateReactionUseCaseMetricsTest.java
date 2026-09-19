package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.EventProducer;
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
    private EventProducer<String, ReactionChangedEvent> eventProducer;

    private UpdateReactionUseCase useCase;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new UpdateReactionUseCase(
                reactionRepository,
                eventProducer,
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
        when(reactionRepository.findById(reactionId)).thenReturn(Optional.of(reaction));
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = useCase.execute(new UpdateReactionCommand(userId, reactionId, TypeReaction.VERY_POSITIVE));

        var eventCaptor = ArgumentCaptor.forClass(ReactionChangedEvent.class);
        verify(reactionRepository).save(reaction);
        verify(eventProducer).send(eq("user-reaction-changed"), eq(userId.toString()), eventCaptor.capture());

        ReactionChangedEvent event = eventCaptor.getValue();

        assertThat(result.oldTypeReaction()).isEqualTo(TypeReaction.POSITIVE);
        assertThat(result.reaction()).isSameAs(reaction);
        assertThat(reaction.getTypeReaction()).isEqualTo(TypeReaction.VERY_POSITIVE);
        assertThat(reaction.getCreatedAt()).isEqualTo(createdAt);
        assertThat(reaction.getUpdatedAt()).isAfter(createdAt);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isEqualTo(reaction.getUpdatedAt());
        assertThat(event.reactionId()).isEqualTo(reactionId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.bookId()).isEqualTo(bookId);
        assertThat(event.oldTypeReaction()).isEqualTo(TypeReaction.POSITIVE.name());
        assertThat(event.newTypeReaction()).isEqualTo(TypeReaction.VERY_POSITIVE.name());
        assertThat(event.resultingTypeReaction()).isEqualTo(TypeReaction.VERY_POSITIVE.name());
        assertThat(reactionsChangedCount("reaction_update")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should preserve an explicit same-value reaction transition")
    void shouldPublishUnchangedReactionTransition() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        long reactionId = 43L;
        Instant createdAt = Instant.parse("2026-09-19T12:00:00Z");
        var reaction = reaction(reactionId, userId, bookId, TypeReaction.POSITIVE, createdAt);
        when(reactionRepository.findById(reactionId)).thenReturn(Optional.of(reaction));
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute(new UpdateReactionCommand(userId, reactionId, TypeReaction.POSITIVE));

        var eventCaptor = ArgumentCaptor.forClass(ReactionChangedEvent.class);
        verify(eventProducer).send(eq("user-reaction-changed"), eq(userId.toString()), eventCaptor.capture());

        ReactionChangedEvent event = eventCaptor.getValue();
        assertThat(event.oldTypeReaction()).isEqualTo(TypeReaction.POSITIVE.name());
        assertThat(event.newTypeReaction()).isEqualTo(TypeReaction.POSITIVE.name());
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
