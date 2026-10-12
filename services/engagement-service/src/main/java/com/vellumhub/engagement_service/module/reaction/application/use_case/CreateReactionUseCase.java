package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.model.BookSnapshot;
import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.reaction.application.command.CreateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.application.event.ReactionEventRecorder;
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
    private final ReactionEventRecorder recorder;
    private final VellumHubMetrics metrics;

    public CreateReactionUseCase(
            ReactionRepository reactionRepository,
            BookSnapshotRepository bookSnapshotRepository,
            ReactionEventRecorder recorder,
            VellumHubMetrics metrics
    ) {
        this.reactionRepository = reactionRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
        this.recorder = recorder;
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

        recorder.changed(savedReaction, null);
        metrics.recordBusinessCounter(VellumHubMetrics.REACTIONS_CHANGED, "reaction_creation", "success");

        return savedReaction;
    }
}

