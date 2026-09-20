package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.ReactionUpdateResult;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
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
public class UpdateReactionUseCase {

    private final ReactionRepository reactionRepository;
    private final EventProducer<String, ReactionChangedEvent> eventProducer;
    private final VellumHubMetrics metrics;

    public UpdateReactionUseCase(
            ReactionRepository reactionRepository,
            EventProducer<String, ReactionChangedEvent> eventProducer,
            VellumHubMetrics metrics
    ) {
        this.reactionRepository = reactionRepository;
        this.eventProducer = eventProducer;
        this.metrics = metrics;
    }

    @Transactional
    public ReactionUpdateResult execute(UpdateReactionCommand command) {
        Reaction reaction = reactionRepository.findByIdForUpdate(command.interactionId())
                .orElseThrow(() -> new RuntimeException("Reaction not found"));

        Instant occurredAt = Instant.now();
        TypeReaction oldTypeReaction = reaction.updateType(
                command.typeReaction(),
                command.userId(),
                occurredAt
        );

        Reaction savedReaction = reactionRepository.save(reaction);
        ReactionUpdateResult updateResult = new ReactionUpdateResult(savedReaction, oldTypeReaction);

        var event = new ReactionChangedEvent(
                UUID.randomUUID(),
                savedReaction.getUpdatedAt(),
                savedReaction.getId(),
                savedReaction.getUserId(),
                savedReaction.getBookSnapshot().getBookId(),
                updateResult.oldTypeReaction().name(),
                savedReaction.getTypeReaction().name()
        );

        eventProducer.send(KafkaTopics.USER_REACTION_CHANGED, event.userId().toString(), event);
        metrics.recordBusinessCounter(VellumHubMetrics.REACTIONS_CHANGED, "reaction_update", "success");

        return updateResult;
    }
}
