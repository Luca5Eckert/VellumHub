package com.vellumhub.engagement_service.module.interaction.presentation.mapper;

import com.vellumhub.engagement_service.module.interaction.domain.model.Interaction;
import com.vellumhub.engagement_service.module.interaction.presentation.dto.response.InteractionResponse;
import org.springframework.stereotype.Component;

@Component
public class InteractionMapper {

    public InteractionResponse toResponse(
            Interaction interaction
    ) {
        return new InteractionResponse(
                interaction.getId(),
                interaction.getUserId(),
                interaction.getBookSnapshot().getBookId(),
                interaction.getTypeInteraction()
        );
    }

}
