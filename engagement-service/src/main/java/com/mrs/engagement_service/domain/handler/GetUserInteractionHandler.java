package com.mrs.engagement_service.domain.handler;

import com.mrs.engagement_service.application.dto.filter.InteractionFilter;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetUserInteractionHandler {

    private final EngagementRepository engagementRepository;

    public GetUserInteractionHandler(EngagementRepository engagementRepository) {
        this.engagementRepository = engagementRepository;
    }

    public Page<Interaction> execute(
            InteractionFilter interactionFilter,
            UUID userId,
            int pageSize,
            int pageNumber
    ){

        PageRequest pageRequest = PageRequest.of(
                pageNumber,
                pageSize
        );

        return engagementRepository.findAll(
                userId,
                interactionFilter,
                pageRequest
        );
    }

}
