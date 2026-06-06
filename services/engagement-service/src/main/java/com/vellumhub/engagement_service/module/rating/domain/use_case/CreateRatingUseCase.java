package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingAlreadyExistException;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CreateRatingUseCase {

    private final RatingRepository ratingRepository;
    private final BookSnapshotRepository bookSnapshotRepository;

    public CreateRatingUseCase(RatingRepository ratingRepository, BookSnapshotRepository bookSnapshotRepository) {
        this.ratingRepository = ratingRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
    }

    public Rating execute(CreateRatingCommand command) {
        if(!bookSnapshotRepository.existsById(command.bookId())) throw new RatingDomainException("Book not exists");

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
