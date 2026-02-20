package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.mapper.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.command.GetUserRatingCommand;
import com.mrs.engagement_service.module.rating.domain.filter.RatingFilter;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.use_case.GetUserRatingUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserRatingHandlerTest {

    @Mock
    private GetUserRatingUseCase getUserRatingUseCase;

    @Mock
    private RatingMapper ratingMapper;

    @InjectMocks
    private GetUserRatingHandler getUserRatingHandler;

    @Test
    @DisplayName("Should return list of rating responses for given user")
    void shouldReturnRatingListForUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Rating rating = new Rating(userId, bookId, 4, "Good book", LocalDateTime.now());
        RatingGetResponse response = new RatingGetResponse(1L, userId, bookId, 4, "Good book", LocalDateTime.now());

        when(getUserRatingUseCase.execute(any(GetUserRatingCommand.class)))
                .thenReturn(new PageImpl<>(List.of(rating)));
        when(ratingMapper.toGetResponse(rating)).thenReturn(response);

        // Act
        List<RatingGetResponse> result = getUserRatingHandler.handle(userId, null, null, null, null, 0, 10);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(userId);
        verify(getUserRatingUseCase, times(1)).execute(any(GetUserRatingCommand.class));
        verify(ratingMapper, times(1)).toGetResponse(rating);
    }

    @Test
    @DisplayName("Should return empty list when user has no ratings")
    void shouldReturnEmptyListWhenNoRatings() {
        // Arrange
        UUID userId = UUID.randomUUID();

        when(getUserRatingUseCase.execute(any(GetUserRatingCommand.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Act
        List<RatingGetResponse> result = getUserRatingHandler.handle(userId, null, null, null, null, 0, 10);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(ratingMapper);
    }
}
