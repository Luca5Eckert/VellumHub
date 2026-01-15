package com.mrs.engagement_service.handler;

import com.mrs.engagement_service.dto.filter.InteractionFilter;
import com.mrs.engagement_service.model.Interaction;
import com.mrs.engagement_service.repository.EngagementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
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
