package com.mrs.engagement_service.application.mapper;

import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.domain.model.Interaction;
import org.springframework.stereotype.Component;

@Component
public class InteractionMapper {

    public InteractionGetResponse toGetResponse(
            Interaction interaction
    ){
        return new InteractionGetResponse(
                interaction.getId(),
                interaction.getUserId(),
                interaction.getMediaId(),
                interaction.getType(),
                interaction.getInteractionValue(),
                interaction.getTimestamp()
        );
    }

}
