package com.mrs.engagement_service.module.rating.application.mapper;

import com.mrs.engagement_service.module.rating.application.dto.GetBookStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingMapper;
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
                rating.getBookId(),
                rating.getStars(),
                rating.getReview(),
                rating.getTimestamp()
        );
    }

    @Override
    public GetBookStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID bookId) {
        return new GetBookStatusResponse(
                bookId,
                engagementStats.getAverageRating(),
                engagementStats.getTotalRatings()
        );
    }

}
