package com.mrs.engagement_service.module.rating.domain.service;


import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.use_case.CreateRatingUseCase;
import com.mrs.engagement_service.module.rating.domain.use_case.GetMediaStatsUseCase;
import com.mrs.engagement_service.module.rating.domain.use_case.GetUserRatingHandler;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingMapper;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RatingService {

    private final GetUserRatingHandler getUserRatingHandler;

    private final RatingMapper ratingMapper;

    public RatingService(GetUserRatingHandler getUserRatingHandler, RatingMapper ratingMapper) {
        this.getUserRatingHandler = getUserRatingHandler;
        this.ratingMapper = ratingMapper;
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



}
