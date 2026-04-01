package com.vellumhub.engagement_service.module.reaction.application.use_case;

import com.vellumhub.engagement_service.module.reaction.application.command.UpdateReactionCommand;
import com.vellumhub.engagement_service.module.reaction.domain.model.Reaction;
import com.vellumhub.engagement_service.module.reaction.domain.port.ReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateReactionUseCase {

    private final ReactionRepository reactionRepository;

    public UpdateReactionUseCase(ReactionRepository reactionRepository) {
        this.reactionRepository = reactionRepository;
    }

    @Transactional
    public void execute(UpdateReactionCommand command) {
        Reaction reaction = reactionRepository.findById(command.interactionId())
                .orElseThrow(() -> new RuntimeException("Reaction not found"));

        reaction.updateType(command.typeReaction(), command.userId());

        reactionRepository.save(reaction);
    }

}
