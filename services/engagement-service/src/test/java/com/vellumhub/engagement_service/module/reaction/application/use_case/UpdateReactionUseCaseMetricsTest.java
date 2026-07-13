package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.EventProducer;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @DisplayName("Should count updated reaction after saving and publishing event")
    void shouldCountUpdatedReaction() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        long reactionId = 42L;
        var reaction = Reaction.builder()
                .id(reactionId)
                .userId(userId)
                .bookSnapshot(new BookSnapshot(bookId))
                .typeReaction(TypeReaction.POSITIVE)
                .build();
        when(reactionRepository.findById(reactionId)).thenReturn(Optional.of(reaction));

        useCase.execute(new UpdateReactionCommand(userId, reactionId, TypeReaction.VERY_POSITIVE));

        verify(reactionRepository).save(reaction);
        verify(eventProducer).send(eq("user-reaction-changed"), eq(userId.toString()), any());
        assertThat(reaction.getTypeReaction()).isEqualTo(TypeReaction.VERY_POSITIVE);
        assertThat(reactionsChangedCount("reaction_update")).isEqualTo(1.0);
    }

    private double reactionsChangedCount(String operation) {
        return meterRegistry.get(VellumHubMetrics.REACTIONS_CHANGED)
                .tag("operation", operation)
                .tag("result", "success")
                .counter()
                .count();
    }
}
