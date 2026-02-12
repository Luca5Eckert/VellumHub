package com.mrs.engagement_service.module.rating.domain.port;

import com.mrs.engagement_service.module.rating.application.dto.GetBookStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;

import java.util.UUID;

public interface RatingMapper {

    RatingGetResponse toGetResponse(Rating rating);

    GetBookStatusResponse toMediaStatusResponse(EngagementStats engagementStats, UUID bookId);

}
