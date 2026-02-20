package com.mrs.engagement_service.module.rating.application.handler;

import com.mrs.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.mrs.engagement_service.module.rating.application.dto.UpdateRatingRequest;
import com.mrs.engagement_service.module.rating.application.mapper.RatingMapper;
import com.mrs.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.use_case.UpdateRatingUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateRatingHandlerTest {

    @Mock
    private UpdateRatingUseCase updateRatingUseCase;

    @Mock
    private RatingMapper mapper;

    @InjectMocks
    private UpdateRatingHandler updateRatingHandler;

    @Test
    @DisplayName("Should return updated rating response successfully")
    void shouldReturnUpdatedRatingResponse() {
        // Arrange
        long ratingId = 1L;
        UpdateRatingRequest request = new UpdateRatingRequest(5, "Amazing!");
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Rating updatedRating = new Rating(userId, bookId, 5, "Amazing!", LocalDateTime.now());
        RatingGetResponse expectedResponse = new RatingGetResponse(ratingId, userId, bookId, 5, "Amazing!", LocalDateTime.now());

        when(updateRatingUseCase.execute(any(UpdateRatingCommand.class))).thenReturn(updatedRating);
        when(mapper.toGetResponse(updatedRating)).thenReturn(expectedResponse);

        // Act
        RatingGetResponse result = updateRatingHandler.handle(ratingId, request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.stars()).isEqualTo(5);
        assertThat(result.review()).isEqualTo("Amazing!");
        verify(updateRatingUseCase, times(1)).execute(any(UpdateRatingCommand.class));
        verify(mapper, times(1)).toGetResponse(updatedRating);
    }

    @Test
    @DisplayName("Should propagate exception when use case fails")
    void shouldPropagateExceptionWhenUseCaseFails() {
        // Arrange
        long ratingId = 99L;
        UpdateRatingRequest request = new UpdateRatingRequest(4, "Good");

        when(updateRatingUseCase.execute(any(UpdateRatingCommand.class)))
                .thenThrow(new RuntimeException("Rating not found"));

        // Act & Assert
        assertThatThrownBy(() -> updateRatingHandler.handle(ratingId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Rating not found");

        verifyNoInteractions(mapper);
    }
}
