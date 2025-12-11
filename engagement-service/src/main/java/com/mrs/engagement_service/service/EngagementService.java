package com.mrs.engagement_service.service;

import com.mrs.engagement_service.dto.InteractionCreateRequest;
import com.mrs.engagement_service.handler.CreateEngagementHandler;
import com.mrs.engagement_service.model.Interaction;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class EngagementService {

    private final CreateEngagementHandler createEngagementHandler;

    public EngagementService(CreateEngagementHandler createEngagementHandler) {
        this.createEngagementHandler = createEngagementHandler;
    }

    public void create(InteractionCreateRequest interactionCreateRequest){
        Interaction interaction = new Interaction(
                interactionCreateRequest.userId(),
                interactionCreateRequest.mediaId(),
                interactionCreateRequest.type(),
                LocalDateTime.now()
        );

        createEngagementHandler.handler(interaction);
    }

}
