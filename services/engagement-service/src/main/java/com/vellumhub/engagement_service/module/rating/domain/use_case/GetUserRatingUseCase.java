package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.rating.domain.filter.RatingFilter;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingNotFoundException;
import java.util.Objects;
import com.vellumhub.engagement_service.module.rating.domain.command.GetUserRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class GetUserRatingUseCase {

    private final RatingRepository ratingRepository;

    public GetUserRatingUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    public Page<Rating> execute(
            GetUserRatingCommand command
    ){

        if (command.requesterId() == null || !Objects.equals(command.userId(), command.requesterId())) {
            throw new RatingNotFoundException("Rating resource not found");
        }

        RatingFilter filter = RatingFilter.builder()
                .minStars(command.minStars())
                .maxStars(command.maxStars())
                .from(command.from())
                .to(command.to())
                .build();

        PageRequest pageRequest = PageRequest.of(
                command.pageNumber(),
                command.pageSize()
        );

        return ratingRepository.findAll(
                command.userId(),
                filter,
                pageRequest
        );
    }

}
