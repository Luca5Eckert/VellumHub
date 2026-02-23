package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.GetAllRatingByBookCommand;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class GetAllRatingByBookUseCase {

    private final RatingRepository ratingRepository;

    public GetAllRatingByBookUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public Page<Rating> execute(GetAllRatingByBookCommand command){
        return ratingRepository.findAllByBookId(
                command.bookId(),
                command.page(),
                command.size()
        );
    }

}
