package com.mrs.engagement_service.application.mapper;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.RatingGetResponse;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Rating;
import com.mrs.engagement_service.domain.port.RatingMapper;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RatingMapperAdapter implements RatingMapper {

    @Override
    public RatingGetResponse toGetResponse(
            Rating rating
    ) {
        return new RatingGetResponse(
                rating.getId(),
                rating.getUserId(),
                rating.getMediaId(),
                rating.getStars(),
                rating.getReview(),
                rating.getTimestamp()
        );
    }

    @Override
    public GetMediaStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID mediaId) {
        return new GetMediaStatusResponse(
                mediaId,
                engagementStats.getAverageRating(),
                engagementStats.getTotalRatings()
        );
    }

}
