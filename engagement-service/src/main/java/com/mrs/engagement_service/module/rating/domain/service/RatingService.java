package com.mrs.engagement_service.module.rating.domain.service;

import com.mrs.engagement_service.module.rating.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.module.rating.application.dto.RatingCreateRequest;
import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.use_case.CreateRatingUseCase;
import com.mrs.engagement_service.module.rating.domain.use_case.GetMediaStatsHandler;
import com.mrs.engagement_service.module.rating.domain.use_case.GetUserRatingHandler;
import com.mrs.engagement_service.module.rating.domain.model.EngagementStats;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RatingService {

    private final CreateRatingUseCase createRatingUseCase;
    private final GetUserRatingHandler getUserRatingHandler;
    private final GetMediaStatsHandler getMediaStatsHandler;

    private final RatingMapper ratingMapper;

    public RatingService(CreateRatingUseCase createRatingUseCase, GetUserRatingHandler getUserRatingHandler, GetMediaStatsHandler getMediaStatsHandler, RatingMapper ratingMapper) {
        this.createRatingUseCase = createRatingUseCase;
        this.getUserRatingHandler = getUserRatingHandler;
        this.getMediaStatsHandler = getMediaStatsHandler;
        this.ratingMapper = ratingMapper;
    }

    public void create(RatingCreateRequest ratingCreateRequest){
        Rating rating = new Rating(
                ratingCreateRequest.userId(),
                ratingCreateRequest.mediaId(),
                ratingCreateRequest.stars(),
                ratingCreateRequest.review(),
                LocalDateTime.now()
        );

        createRatingUseCase.execute(rating);
    }

    public List<RatingGetResponse> findAllOfUser(
            UUID userId,
            Integer minStars,
            Integer maxStars,
            OffsetDateTime from,
            OffsetDateTime to,
            int pageNumber,
            int pageSize
    ){
        RatingFilter ratingFilter = new RatingFilter(
                minStars,
                maxStars,
                from,
                to
        );

        Page<Rating> ratings = getUserRatingHandler.execute(
                ratingFilter,
                userId,
                pageSize,
                pageNumber
        );

        return ratings.stream()
                .map(ratingMapper::toGetResponse)
                .toList();

    }

    public GetMediaStatusResponse getMediaStatus(UUID mediaId){
        EngagementStats engagementStats = getMediaStatsHandler.execute(mediaId);

        return ratingMapper.toMediaStatusResponse(engagementStats, mediaId);
    }


}
