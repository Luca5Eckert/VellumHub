package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.EventProducer;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateReactionUseCase {

    private final ReactionRepository reactionRepository;
    private final EventProducer<String, ReactionChangedEvent> eventProducer;
    private final VellumHubMetrics metrics;

    public UpdateReactionUseCase(ReactionRepository reactionRepository, EventProducer<String, ReactionChangedEvent> eventProducer, VellumHubMetrics metrics) {
        this.reactionRepository = reactionRepository;
        this.eventProducer = eventProducer;
        this.metrics = metrics;
    }

    @Transactional
    public void execute(UpdateReactionCommand command) {
        Reaction reaction = reactionRepository.findById(command.interactionId())
                .orElseThrow(() -> new RuntimeException("Reaction not found"));

        reaction.updateType(command.typeReaction(), command.userId());

        reactionRepository.save(reaction);

        var event = new ReactionChangedEvent(
                reaction.getUserId(),
                reaction.getBookSnapshot().getBookId(),
                reaction.getTypeReaction().name()
        );

        eventProducer.send(KafkaTopics.USER_REACTION_CHANGED, event.userId().toString(), event);
        metrics.recordBusinessCounter(VellumHubMetrics.REACTIONS_CHANGED, "reaction_update", "success");
    }

}
