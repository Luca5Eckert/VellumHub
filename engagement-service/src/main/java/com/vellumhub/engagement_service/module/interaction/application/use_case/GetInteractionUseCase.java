package com.vellumhub.engagement_service.module.interaction.application.use_case;

import com.vellumhub.engagement_service.module.interaction.application.query.GetInteractionQuery;
import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.domain.port.InteractionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GetInteractionUseCase {

    private final InteractionRepository interactionRepository;

    public GetInteractionUseCase(InteractionRepository interactionRepository) {
        this.interactionRepository = interactionRepository;
    }

    @Transactional(readOnly = true)
    public Interaction execute(GetInteractionQuery query) {
        return interactionRepository.findById(query.interactionId())
                .orElseThrow(() -> new RuntimeException("Interaction not found"));
    }

}
