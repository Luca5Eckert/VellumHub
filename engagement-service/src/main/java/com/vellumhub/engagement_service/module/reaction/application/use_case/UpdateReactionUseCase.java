package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.event.ReactionEvent;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.EventProducer;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateReactionUseCase {

    private final ReactionRepository reactionRepository;
    private final EventProducer<String, ReactionEvent> eventProducer;

    public UpdateReactionUseCase(ReactionRepository reactionRepository, EventProducer<String, ReactionEvent> eventProducer) {
        this.reactionRepository = reactionRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void execute(UpdateReactionCommand command) {
        Reaction reaction = reactionRepository.findById(command.interactionId())
                .orElseThrow(() -> new RuntimeException("Reaction not found"));

        reaction.updateType(command.typeReaction(), command.userId());

        reactionRepository.save(reaction);

        var event = ReactionEvent.from(reaction);

        eventProducer.send("user-reacted", event.userId().toString(), event);
    }

}
