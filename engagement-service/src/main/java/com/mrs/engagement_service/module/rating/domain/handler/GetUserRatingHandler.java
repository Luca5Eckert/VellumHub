package com.mrs.engagement_service.module.rating.domain.handler;

import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.EngagementRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetUserRatingHandler {

    private final EngagementRepository engagementRepository;

    public GetUserRatingHandler(EngagementRepository engagementRepository) {
        this.engagementRepository = engagementRepository;
    }

    public Page<Rating> execute(
            RatingFilter ratingFilter,
            UUID userId,
            int pageSize,
            int pageNumber
    ){

        PageRequest pageRequest = PageRequest.of(
                pageNumber,
                pageSize
        );

        return engagementRepository.findAll(
                userId,
                ratingFilter,
                pageRequest
        );
    }

}
