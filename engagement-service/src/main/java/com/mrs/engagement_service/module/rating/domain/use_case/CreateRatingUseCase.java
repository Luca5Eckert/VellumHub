package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.exception.RatingAlreadyExistException;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateRatingUseCase {

    private final RatingRepository ratingRepository;

    public CreateRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public void execute(CreateRatingCommand command) {
        if (ratingRepository.existsByUserIdAndBookId(command.userId(), command.bookId())) {
            throw new RatingAlreadyExistException();
        }

        Rating rating = Rating.builder()
                .userId(command.userId())
                .bookId(command.bookId())
                .stars(command.stars())
                .review(command.review())
                .build();

        ratingRepository.save(rating);
    }

}
