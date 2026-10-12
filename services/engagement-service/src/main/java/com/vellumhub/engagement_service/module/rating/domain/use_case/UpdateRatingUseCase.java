package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UpdateRatingUseCase {

    private final RatingRepository ratingRepository;

    public UpdateRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    @Transactional
    public RatingUpdateResult execute(UpdateRatingCommand command, UUID authenticatedUserId) {
        Rating rating = ratingRepository.findByIdForUpdate(command.ratingId())
                .orElseThrow(() -> new RatingDomainException("Rating not found"));

        if(!Objects.equals(rating.getUserId(),authenticatedUserId)) {
            throw new RatingDomainException("User not authorized to update this rating");
        }

        int oldStars = rating.getStars();
        String oldReview = rating.getReview();

        boolean starsChanged=command.stars()!=null && command.stars()!=oldStars;
        boolean reviewChanged=command.review()!=null && !Objects.equals(oldReview,command.review());
        if(!starsChanged && !reviewChanged)
            return new RatingUpdateResult(rating,oldStars,false);

        rating.update(
                command.stars(),
                command.review()
        );

        Rating savedRating = ratingRepository.save(rating);

        return new RatingUpdateResult(savedRating, oldStars, reviewChanged);
    }

}

