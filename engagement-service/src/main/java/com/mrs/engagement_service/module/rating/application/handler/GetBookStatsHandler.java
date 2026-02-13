package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.GetBookStatusResponse;
import com.mrs.engagement_service.module.rating.application.mapper.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.rating.domain.use_case.GetBookStatsUseCase;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class GetBookStatsHandler {

    private final GetBookStatsUseCase getBookStatsUseCase;

    private final RatingMapper ratingMapper;

    public GetBookStatsHandler(GetBookStatsUseCase getBookStatsUseCase, RatingMapper ratingMapper) {
        this.getBookStatsUseCase = getBookStatsUseCase;
        this.ratingMapper = ratingMapper;
    }

    @Transactional(readOnly = true)
    public GetBookStatusResponse handle(UUID bookId) {
        EngagementStats engagementStats = getBookStatsUseCase.execute(bookId);

        return ratingMapper.toMediaStatusResponse(engagementStats, bookId);
    }

}
