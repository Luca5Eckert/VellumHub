package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.GetBookStatusResponse;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.rating.domain.port.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.use_case.GetMediaStatsUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetMediaStatsHandler {

    private final GetMediaStatsUseCase getMediaStatsUseCase;

    private final RatingMapper ratingMapper;

    public GetMediaStatsHandler(GetMediaStatsUseCase getMediaStatsUseCase, RatingMapper ratingMapper) {
        this.getMediaStatsUseCase = getMediaStatsUseCase;
        this.ratingMapper = ratingMapper;
    }

    @Transactional(readOnly = true)
    public GetBookStatusResponse handle(UUID bookId) {
        EngagementStats engagementStats = getMediaStatsUseCase.execute(bookId);

        return ratingMapper.toMediaStatusResponse(engagementStats, bookId);
    }

}
