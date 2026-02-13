package com.mrs.engagement_service.module.rating.domain.handler;

import com.mrs.engagement_service.module.book_progress.domain.model.EngagementStats;
import com.mrs.engagement_service.module.rating.domain.port.EngagementRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetMediaStatsHandler {

    private final EngagementRepository engagementRepository;

    public GetMediaStatsHandler(EngagementRepository engagementRepository) {
        this.engagementRepository = engagementRepository;
    }

    public EngagementStats execute(UUID mediaId){
        return engagementRepository.findStatusByMediaId(mediaId);
    }

}
