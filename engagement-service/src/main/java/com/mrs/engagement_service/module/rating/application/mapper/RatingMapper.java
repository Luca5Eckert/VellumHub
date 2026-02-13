package com.mrs.engagement_service.module.rating.application.mapper;

import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import org.springframework.stereotype.Component;

@Component
public class RatingMapper {

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

}
