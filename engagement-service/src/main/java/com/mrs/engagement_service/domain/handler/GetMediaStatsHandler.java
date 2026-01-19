package com.mrs.engagement_service.domain.handler;

import com.mrs.engagement_service.domain.model.EngagementStatus;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetMediaStatsHandler {

    private final EngagementRepository engagementRepository;

    public GetMediaStatsHandler(EngagementRepository engagementRepository) {
        this.engagementRepository = engagementRepository;
    }

    public EngagementStatus execute(UUID mediaId){
        return engagementRepository.findStatusByMediaId(mediaId);
    }

}
