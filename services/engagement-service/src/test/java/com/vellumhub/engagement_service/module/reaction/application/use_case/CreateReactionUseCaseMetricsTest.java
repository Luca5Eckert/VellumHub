package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateReactionUseCaseMetricsTest {

    @Mock
    private ReactionRepository reactionRepository;

    @Mock
    private BookSnapshotRepository bookSnapshotRepository;

    @Mock
    private EventProducer<String, ReactionChangedEvent> eventProducer;

    private CreateReactionUseCase useCase;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        useCase = new CreateReactionUseCase(
                reactionRepository,
                bookSnapshotRepository,
                eventProducer,
                new VellumHubMetrics(meterRegistry)
        );
    }

    @Test
    @DisplayName("Should count created reaction after saving and publishing event")
    void shouldCountCreatedReaction() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new CreateReactionCommand(userId, bookId, TypeReaction.POSITIVE);
        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(new BookSnapshot(bookId)));

        useCase.execute(command);

        var reactionCaptor = ArgumentCaptor.forClass(Reaction.class);
        verify(reactionRepository).save(reactionCaptor.capture());
        verify(eventProducer).send(eq("user-reaction-changed"), eq(userId.toString()), org.mockito.ArgumentMatchers.any());
        assertThat(reactionCaptor.getValue().getTypeReaction()).isEqualTo(TypeReaction.POSITIVE);
        assertThat(reactionsChangedCount("reaction_creation")).isEqualTo(1.0);
    }

    private double reactionsChangedCount(String operation) {
        return meterRegistry.get(VellumHubMetrics.REACTIONS_CHANGED)
                .tag("operation", operation)
                .tag("result", "success")
                .counter()
                .count();
    }
}
