package com.mrs.engagement_service.service;

import com.mrs.engagement_service.dto.InteractionCreateRequest;
import com.mrs.engagement_service.dto.InteractionGetResponse;
import com.mrs.engagement_service.dto.filter.InteractionFilter;
import com.mrs.engagement_service.handler.CreateEngagementHandler;
import com.mrs.engagement_service.handler.GetUserInteractionHandler;
import com.mrs.engagement_service.mapper.InteractionMapper;
import com.mrs.engagement_service.model.Interaction;
import com.mrs.engagement_service.model.InteractionType;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class EngagementService {

    private final CreateEngagementHandler createEngagementHandler;
    private final GetUserInteractionHandler getUserInteractionHandler;

    private final InteractionMapper interactionMapper;

    public EngagementService(CreateEngagementHandler createEngagementHandler, GetUserInteractionHandler getUserInteractionHandler, InteractionMapper interactionMapper) {
        this.createEngagementHandler = createEngagementHandler;
        this.getUserInteractionHandler = getUserInteractionHandler;
        this.interactionMapper = interactionMapper;
    }

    public void create(InteractionCreateRequest interactionCreateRequest){
        Interaction interaction = new Interaction(
                interactionCreateRequest.userId(),
                interactionCreateRequest.mediaId(),
                interactionCreateRequest.type(),
                interactionCreateRequest.interactionValue(),
                LocalDateTime.now()
        );

        createEngagementHandler.handler(interaction);
    }

    public List<InteractionGetResponse> findAllOfUser(
            UUID userId,
            InteractionType type,
            OffsetDateTime from,
            OffsetDateTime to,
            int pageNumber,
            int pageSize
    ){
        InteractionFilter interactionFilter = new InteractionFilter(
                type,
                from,
                to
        );

        Page<Interaction> interactions = getUserInteractionHandler.execute(
                interactionFilter,
                userId,
                pageNumber,
                pageSize
        );

        return interactions.stream()
                .map(interactionMapper::toGetResponse)
                .toList();

    }

}
