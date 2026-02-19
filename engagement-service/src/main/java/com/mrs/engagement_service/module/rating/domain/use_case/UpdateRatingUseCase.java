package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

@Component
public class UpdateRatingUseCase {

    private final RatingRepository ratingRepository;

    public UpdateRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public Rating execute(UpdateRatingCommand command) {
        Rating rating = ratingRepository.findById(command.ratingId())
                .orElseThrow(() -> new RatingDomainException("Rating not found"));

        rating.update(
                command.stars(),
                command.review()
        );

        return ratingRepository.save(rating);
    }

}
