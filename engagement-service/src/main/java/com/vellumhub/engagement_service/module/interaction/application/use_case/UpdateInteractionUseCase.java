package com.vellumhub.engagement_service.module.interaction.application.use_case;

import com.vellumhub.engagement_service.module.interaction.application.command.UpdateInteractionCommand;
import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.domain.port.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateInteractionUseCase {

    private final InteractionRepository interactionRepository;

    public UpdateInteractionUseCase(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
    }

    @Transactional
    public void execute(UpdateInteractionCommand command) {
        Interaction interaction = interactionRepository.findById(command.interactionId())
                .orElseThrow(() -> new RuntimeException("Interaction not found"));

        interaction.updateType(command.typeInteraction(), command.userId());

        interactionRepository.save(interaction);
    }

}
