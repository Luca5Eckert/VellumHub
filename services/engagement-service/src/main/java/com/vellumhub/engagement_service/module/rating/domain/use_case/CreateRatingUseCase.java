package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.book_snapshot.domain.port.BookSnapshotRepository;
import com.vellumhub.engagement_service.module.rating.domain.command.CreateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingAlreadyExistException;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CreateRatingUseCase {

    private final RatingRepository ratingRepository;
    private final BookSnapshotRepository bookSnapshotRepository;
    private final VellumHubMetrics metrics;

    public CreateRatingUseCase(RatingRepository ratingRepository, BookSnapshotRepository bookSnapshotRepository, VellumHubMetrics metrics) {
        this.ratingRepository = ratingRepository;
        this.bookSnapshotRepository = bookSnapshotRepository;
        this.metrics = metrics;
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

        Rating savedRating = ratingRepository.save(rating);
        metrics.recordBusinessCounter(VellumHubMetrics.RATINGS_CREATED, "rating_creation", "success");
        return savedRating;
    }

}
