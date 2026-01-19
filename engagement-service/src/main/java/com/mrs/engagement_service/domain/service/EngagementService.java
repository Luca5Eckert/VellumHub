package com.mrs.engagement_service.domain.service;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.InteractionCreateRequest;
import com.mrs.engagement_service.application.dto.InteractionGetResponse;
import com.mrs.engagement_service.application.dto.filter.InteractionFilter;
import com.mrs.engagement_service.domain.handler.CreateEngagementHandler;
import com.mrs.engagement_service.domain.handler.GetMediaStatsHandler;
import com.mrs.engagement_service.domain.handler.GetUserInteractionHandler;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.model.InteractionType;
import com.mrs.engagement_service.domain.port.InteractionMapper;
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
    private final GetMediaStatsHandler getMediaStatsHandler;

    private final InteractionMapper interactionMapper;

    public EngagementService(CreateEngagementHandler createEngagementHandler, GetUserInteractionHandler getUserInteractionHandler, GetMediaStatsHandler getMediaStatsHandler, InteractionMapper interactionMapper) {
        this.createEngagementHandler = createEngagementHandler;
        this.getUserInteractionHandler = getUserInteractionHandler;
        this.getMediaStatsHandler = getMediaStatsHandler;
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

    public GetMediaStatusResponse getMediaStatus(UUID mediaId){
        EngagementStats engagementStats = getMediaStatsHandler.execute(mediaId);

        return interactionMapper.toMediaStatusResponse(engagementStats, mediaId);
    }


}
