package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetMediaStatsUseCase {

    private final RatingRepository ratingRepository;

    public GetMediaStatsUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public EngagementStats execute(UUID mediaId){
        return ratingRepository.findStatusByMediaId(mediaId);
    }

}
