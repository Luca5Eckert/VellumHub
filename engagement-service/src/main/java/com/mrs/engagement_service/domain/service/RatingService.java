package com.mrs.engagement_service.domain.service;

import com.mrs.engagement_service.application.dto.GetMediaStatusResponse;
import com.mrs.engagement_service.application.dto.RatingCreateRequest;
import com.mrs.engagement_service.application.dto.RatingGetResponse;
import com.mrs.engagement_service.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.domain.handler.CreateRatingHandler;
import com.mrs.engagement_service.domain.handler.GetMediaStatsHandler;
import com.mrs.engagement_service.domain.handler.GetUserRatingHandler;
import com.mrs.engagement_service.domain.model.EngagementStats;
import com.mrs.engagement_service.domain.model.Rating;
import com.mrs.engagement_service.domain.port.RatingMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RatingService {

    private final CreateRatingHandler createRatingHandler;
    private final GetUserRatingHandler getUserRatingHandler;
    private final GetMediaStatsHandler getMediaStatsHandler;

    private final RatingMapper ratingMapper;

    public RatingService(CreateRatingHandler createRatingHandler, GetUserRatingHandler getUserRatingHandler, GetMediaStatsHandler getMediaStatsHandler, RatingMapper ratingMapper) {
        this.createRatingHandler = createRatingHandler;
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

        createRatingHandler.handler(rating);
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
