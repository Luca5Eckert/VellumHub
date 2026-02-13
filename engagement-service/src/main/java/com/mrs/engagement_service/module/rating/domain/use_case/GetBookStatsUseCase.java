package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetBookStatsUseCase {

    private final RatingRepository ratingRepository;

    public GetBookStatsUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public EngagementStats execute(UUID mediaId){
        return ratingRepository.findStatusByMediaId(mediaId);
    }

}
