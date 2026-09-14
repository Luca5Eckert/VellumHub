package com.vellumhub.engagement_service.module.rating.domain.use_case;

import com.vellumhub.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.engagement_service.module.rating.domain.port.RatingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateRatingUseCaseTest {

    @Mock
    private RatingRepository ratingRepository;

    @InjectMocks
    private UpdateRatingUseCase updateRatingUseCase;

    @Test
    @DisplayName("Should preserve previous stars when updating inside the same recommendation bucket")
    void shouldPreservePreviousStarsForSameBucketUpdate() {
        long ratingId = 1L;
        Rating existingRating = rating(ratingId, 4, "Great");
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, 5, "Great");
        mockPersistence(existingRating);

        RatingUpdateResult result = updateRatingUseCase.execute(command);

        assertThat(result.oldStars()).isEqualTo(4);
        assertThat(result.rating().getStars()).isEqualTo(5);
        assertThat(result.reviewChanged()).isFalse();
        verify(ratingRepository).save(existingRating);
    }

    @Test
    @DisplayName("Should preserve previous stars when update crosses recommendation buckets")
    void shouldPreservePreviousStarsForCrossBucketUpdate() {
        long ratingId = 2L;
        Rating existingRating = rating(ratingId, 2, "Not for me");
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, 4, "Much better now");
        mockPersistence(existingRating);

        RatingUpdateResult result = updateRatingUseCase.execute(command);

        assertThat(result.oldStars()).isEqualTo(2);
        assertThat(result.rating().getStars()).isEqualTo(4);
        assertThat(result.reviewChanged()).isTrue();
    }

    @Test
    @DisplayName("Should mark review-only update without inventing a star transition")
    void shouldMarkReviewOnlyUpdate() {
        long ratingId = 3L;
        Rating existingRating = rating(ratingId, 4, "Good");
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, null, "Very good");
        mockPersistence(existingRating);

        RatingUpdateResult result = updateRatingUseCase.execute(command);

        assertThat(result.oldStars()).isEqualTo(4);
        assertThat(result.rating().getStars()).isEqualTo(4);
        assertThat(result.rating().getReview()).isEqualTo("Very good");
        assertThat(result.reviewChanged()).isTrue();
    }

    @Test
    @DisplayName("Should not mark review as changed when the resulting review is identical")
    void shouldNotMarkUnchangedReview() {
        long ratingId = 4L;
        Rating existingRating = rating(ratingId, 3, "Average");
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, 4, "Average");
        mockPersistence(existingRating);

        RatingUpdateResult result = updateRatingUseCase.execute(command);

        assertThat(result.oldStars()).isEqualTo(3);
        assertThat(result.rating().getStars()).isEqualTo(4);
        assertThat(result.reviewChanged()).isFalse();
    }

    @Test
    @DisplayName("Should throw RatingDomainException when rating is not found")
    void shouldThrowExceptionWhenRatingNotFound() {
        long ratingId = 99L;
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, 4, "Good");

        when(ratingRepository.findById(ratingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> updateRatingUseCase.execute(command))
                .isInstanceOf(RatingDomainException.class)
                .hasMessageContaining("Rating not found");

        verify(ratingRepository, never()).save(any());
    }

    private void mockPersistence(Rating rating) {
        when(ratingRepository.findById(rating.getId())).thenReturn(Optional.of(rating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Rating rating(long id, int stars, String review) {
        return Rating.builder()
                .id(id)
                .userId(UUID.randomUUID())
                .bookId(UUID.randomUUID())
                .stars(stars)
                .review(review)
                .build();
    }
}
