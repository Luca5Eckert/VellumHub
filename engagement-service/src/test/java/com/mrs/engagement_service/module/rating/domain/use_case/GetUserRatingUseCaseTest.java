package com.mrs.engagement_service.module.rating.domain.use_case;

import com.mrs.engagement_service.module.rating.domain.command.GetUserRatingCommand;
import com.mrs.engagement_service.module.rating.domain.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.RatingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserRatingUseCaseTest {

    @Mock
    private RatingRepository ratingRepository;

    @InjectMocks
    private GetUserRatingUseCase getUserRatingUseCase;

    @Test
    @DisplayName("Should return page of ratings for given user")
    void shouldReturnPageOfRatings() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetUserRatingCommand command = new GetUserRatingCommand(userId, 3, 5, null, null, 0, 10);

        Rating rating = new Rating(userId, UUID.randomUUID(), 4, "Good!", null);
        Page<Rating> expectedPage = new PageImpl<>(List.of(rating));

        when(ratingRepository.findAll(eq(userId), any(RatingFilter.class), any(PageRequest.class)))
                .thenReturn(expectedPage);

        // Act
        Page<Rating> result = getUserRatingUseCase.execute(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getUserId()).isEqualTo(userId);
        verify(ratingRepository, times(1)).findAll(eq(userId), any(RatingFilter.class), any(PageRequest.class));
    }

    @Test
    @DisplayName("Should return empty page when no ratings exist for user")
    void shouldReturnEmptyPageWhenNoRatingsExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetUserRatingCommand command = new GetUserRatingCommand(userId, null, null, null, null, 0, 10);

        when(ratingRepository.findAll(eq(userId), any(RatingFilter.class), any(PageRequest.class)))
                .thenReturn(Page.empty());

        // Act
        Page<Rating> result = getUserRatingUseCase.execute(command);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
    }
}
