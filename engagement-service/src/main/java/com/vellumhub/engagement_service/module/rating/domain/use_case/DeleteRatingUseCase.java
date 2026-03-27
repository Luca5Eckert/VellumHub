package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.DeleteRatingCommand;
import com.mrs.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.mrs.engagement_service.module.rating.domain.exception.RatingNotFoundException;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

@Component
public class DeleteRatingUseCase {

    private final RatingRepository ratingRepository;

    public DeleteRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public void execute(DeleteRatingCommand command) {
        Rating rating = ratingRepository.findById(command.ratingId())
                .orElseThrow(() -> new RatingNotFoundException("Rating not found with id: " + command.ratingId()));

        if(rating.getUserId() != command.userId()){
            throw new RatingDomainException("User not authorized to delete this rating");
        }

        ratingRepository.deleteById(command.ratingId());
    }

}
