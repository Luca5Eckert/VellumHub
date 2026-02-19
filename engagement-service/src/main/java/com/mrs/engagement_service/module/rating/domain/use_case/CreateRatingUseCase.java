package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.exception.RatingAlreadyExistException;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CreateRatingUseCase {

    private final RatingRepository ratingRepository;

    public CreateRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public Rating execute(CreateRatingCommand command) {
        if (ratingRepository.existsByUserIdAndBookId(command.userId(), command.bookId())) {
            throw new RatingAlreadyExistException();
        }

        Rating rating = Rating.builder()
                .userId(command.userId())
                .bookId(command.bookId())
                .stars(command.stars())
                .review(command.review())
                .timestamp(LocalDateTime.now())
                .build();

        return ratingRepository.save(rating);
    }

}
