package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class UpdateRatingUseCase {

    private final RatingRepository ratingRepository;

    public UpdateRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public RatingUpdateResult execute(UpdateRatingCommand command) {
        Rating rating = ratingRepository.findById(command.ratingId())
                .orElseThrow(() -> new RatingDomainException("Rating not found"));

        int oldStars = rating.getStars();
        String oldReview = rating.getReview();

        rating.update(
                command.stars(),
                command.review()
        );

        Rating savedRating = ratingRepository.save(rating);
        boolean reviewChanged = !Objects.equals(oldReview, savedRating.getReview());

        return new RatingUpdateResult(savedRating, oldStars, reviewChanged);
    }

}
