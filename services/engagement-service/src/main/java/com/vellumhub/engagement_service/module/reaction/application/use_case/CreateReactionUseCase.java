package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.EventProducer;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CreateReactionUseCase {

    private final ReactionRepository reactionRepository;
    private final BookSnapshotRepository bookSnapshotRepository;
    private final EventProducer<String, ReactionChangedEvent> eventProducer;
    private final VellumHubMetrics metrics;

    public CreateReactionUseCase(
            ReactionRepository reactionRepository,
            BookSnapshotRepository bookSnapshotRepository,
            EventProducer<String, ReactionChangedEvent> eventProducer,
            VellumHubMetrics metrics
    ) {
        this.reactionRepository = reactionRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
        this.eventProducer = eventProducer;
        this.metrics = metrics;
    }

    @Transactional
    public Reaction execute(CreateReactionCommand command) {
        BookSnapshot book = bookSnapshotRepository.findById(command.bookId())
                .orElseThrow(() -> new RuntimeException("Book snapshot not found"));

        Instant occurredAt = Instant.now();
        Reaction reaction = Reaction.of(
                command.userId(),
                book,
                command.typeReaction(),
                occurredAt
        );

        Reaction savedReaction = reactionRepository.save(reaction);

        var event = new ReactionChangedEvent(
                UUID.randomUUID(),
                savedReaction.getCreatedAt(),
                savedReaction.getId(),
                savedReaction.getUserId(),
                savedReaction.getBookSnapshot().getBookId(),
                null,
                savedReaction.getTypeReaction().name()
        );

        eventProducer.send(KafkaTopics.USER_REACTION_CHANGED, event.userId().toString(), event);
        metrics.recordBusinessCounter(VellumHubMetrics.REACTIONS_CHANGED, "reaction_creation", "success");

        return savedReaction;
    }
}
