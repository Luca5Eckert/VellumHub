package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.exception.RatingDomainException;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
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
    @DisplayName("Should update rating successfully when rating exists")
    void shouldUpdateRatingSuccessfully() {
        // Arrange
        long ratingId = 1L;
        Rating existingRating = new Rating(UUID.randomUUID(), UUID.randomUUID(), 3, "Average", null);
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, 5, "Excellent!");

        when(ratingRepository.findById(ratingId)).thenReturn(Optional.of(existingRating));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Rating result = updateRatingUseCase.execute(command);

        // Assert
        assertThat(result.getStars()).isEqualTo(5);
        assertThat(result.getReview()).isEqualTo("Excellent!");
        verify(ratingRepository, times(1)).save(existingRating);
    }

    @Test
    @DisplayName("Should throw RatingDomainException when rating is not found")
    void shouldThrowExceptionWhenRatingNotFound() {
        // Arrange
        long ratingId = 99L;
        UpdateRatingCommand command = new UpdateRatingCommand(ratingId, 4, "Good");

        when(ratingRepository.findById(ratingId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> updateRatingUseCase.execute(command))
                .isInstanceOf(RatingDomainException.class)
                .hasMessageContaining("Rating not found");

        verify(ratingRepository, never()).save(any());
    }
}
