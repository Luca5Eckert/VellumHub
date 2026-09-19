package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("Should persist and publish an auditable reaction creation")
    void shouldPublishAuditableReactionCreation() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        var command = new CreateReactionCommand(userId, bookId, TypeReaction.POSITIVE);
        when(bookSnapshotRepository.findById(bookId)).thenReturn(Optional.of(new BookSnapshot(bookId)));
        when(reactionRepository.save(any(Reaction.class))).thenAnswer(invocation -> {
            Reaction reaction = invocation.getArgument(0);
            reaction.setId(42L);
            return reaction;
        });

        Reaction savedReaction = useCase.execute(command);

        var reactionCaptor = ArgumentCaptor.forClass(Reaction.class);
        var eventCaptor = ArgumentCaptor.forClass(ReactionChangedEvent.class);
        verify(reactionRepository).save(reactionCaptor.capture());
        verify(eventProducer).send(eq("user-reaction-changed"), eq(userId.toString()), eventCaptor.capture());

        Reaction persisted = reactionCaptor.getValue();
        ReactionChangedEvent event = eventCaptor.getValue();

        assertThat(savedReaction).isSameAs(persisted);
        assertThat(persisted.getTypeReaction()).isEqualTo(TypeReaction.POSITIVE);
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isEqualTo(persisted.getCreatedAt());

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isEqualTo(persisted.getCreatedAt());
        assertThat(event.reactionId()).isEqualTo(42L);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.bookId()).isEqualTo(bookId);
        assertThat(event.oldTypeReaction()).isNull();
        assertThat(event.newTypeReaction()).isEqualTo(TypeReaction.POSITIVE.name());
        assertThat(event.resultingTypeReaction()).isEqualTo(TypeReaction.POSITIVE.name());
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
