package com.vellumhub.engagement_service.module.rating.application.handler;

import com.vellumhub.engagement_service.module.rating.application.dto.RatingGetResponse;
import com.vellumhub.engagement_service.module.rating.application.dto.UpdateRatingRequest;
import com.vellumhub.engagement_service.module.rating.application.mapper.RatingMapper;
import com.vellumhub.engagement_service.module.rating.domain.command.UpdateRatingCommand;
import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.model.RatingUpdateResult;
import com.vellumhub.engagement_service.module.rating.domain.producer.UpdatedRatingEventProducer;
import com.vellumhub.engagement_service.module.rating.domain.use_case.UpdateRatingUseCase;
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

    @Mock
    private UpdatedRatingEventProducer updatedRatingEventProducer;

    @InjectMocks
    private UpdateRatingHandler updateRatingHandler;

    @Test
    @DisplayName("Should publish updated-rating and return updated rating response")
    void shouldPublishUpdatedRatingAndReturnResponse() {
        long ratingId = 1L;
        UpdateRatingRequest request = new UpdateRatingRequest(5, "Amazing!");
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Rating updatedRating = Rating.builder()
                .id(ratingId)
                .userId(userId)
                .bookId(bookId)
                .stars(5)
                .review("Amazing!")
                .timestamp(LocalDateTime.now())
                .build();
        RatingUpdateResult updateResult = new RatingUpdateResult(updatedRating, 4, true);
        RatingGetResponse expectedResponse = new RatingGetResponse(
                ratingId,
                userId,
                bookId,
                5,
                "Amazing!",
                updatedRating.getTimestamp()
        );

        when(updateRatingUseCase.execute(any(UpdateRatingCommand.class))).thenReturn(updateResult);
        when(mapper.toGetResponse(updatedRating)).thenReturn(expectedResponse);

        RatingGetResponse result = updateRatingHandler.handle(ratingId, request);

        assertThat(result).isEqualTo(expectedResponse);
        verify(updateRatingUseCase).execute(any(UpdateRatingCommand.class));
        verify(updatedRatingEventProducer).produce(updateResult);
        verify(mapper).toGetResponse(updatedRating);
    }

    @Test
    @DisplayName("Should not publish updated-rating when update fails")
    void shouldNotPublishWhenUseCaseFails() {
        long ratingId = 99L;
        UpdateRatingRequest request = new UpdateRatingRequest(4, "Good");

        when(updateRatingUseCase.execute(any(UpdateRatingCommand.class)))
                .thenThrow(new RuntimeException("Rating not found"));

        assertThatThrownBy(() -> updateRatingHandler.handle(ratingId, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Rating not found");

        verifyNoInteractions(updatedRatingEventProducer, mapper);
    }
}
