package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.model.ReactionUpdateResult;
import com.vellumhub.engagement_service.module.reaction.domain.model.TypeReaction;
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
public class UpdateReactionUseCase {

    private final ReactionRepository reactionRepository;
    private final ReactionEventRecorder recorder;
    private final VellumHubMetrics metrics;

    public UpdateReactionUseCase(
            ReactionRepository reactionRepository,
            ReactionEventRecorder recorder,
            VellumHubMetrics metrics
    ) {
        this.reactionRepository = reactionRepository;
        this.recorder = recorder;
        this.metrics = metrics;
    }

    @Transactional
    public ReactionUpdateResult execute(UpdateReactionCommand command) {
        Reaction reaction = reactionRepository.findByIdForUpdate(command.interactionId())
                .orElseThrow(() -> new RuntimeException("Reaction not found"));

        if(!reaction.getUserId().equals(command.userId())) {
            throw new com.vellumhub.engagement_service.module.reaction.domain.exception.ReactionException(
                    "User cannot update reaction of another user");
        }
        if(reaction.getTypeReaction()==command.typeReaction()) {
            return new ReactionUpdateResult(reaction,reaction.getTypeReaction());
        }

        Instant occurredAt = Instant.now();
        TypeReaction oldTypeReaction = reaction.updateType(
                command.typeReaction(),
                command.userId(),
                occurredAt
        );

        Reaction savedReaction = reactionRepository.save(reaction);
        ReactionUpdateResult updateResult = new ReactionUpdateResult(savedReaction, oldTypeReaction);

        recorder.changed(savedReaction, oldTypeReaction);
        metrics.recordBusinessCounter(VellumHubMetrics.REACTIONS_CHANGED, "reaction_update", "success");

        return updateResult;
    }
}

