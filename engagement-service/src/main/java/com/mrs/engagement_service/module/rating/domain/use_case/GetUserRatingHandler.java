package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.application.dto.filter.RatingFilter;
import com.mrs.engagement_service.module.book_progress.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetUserRatingHandler {

    private final RatingRepository ratingRepository;

    public GetUserRatingHandler(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
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

        return ratingRepository.findAll(
                userId,
                ratingFilter,
                pageRequest
        );
    }

}
