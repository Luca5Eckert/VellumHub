package com.mrs.recommendation_service.module.recommendation.application.handler;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import com.mrs.recommendation_service.module.recommendation.application.dto.RecommendationResponse;
import com.mrs.recommendation_service.module.recommendation.application.mapper.RecommendationMapper;
import com.mrs.recommendation_service.module.recommendation.domain.command.GetRecommendationsCommand;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.mrs.recommendation_service.module.recommendation.domain.use_case.GetRecommendationsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRecommendationsHandlerTest {

    @Mock
    private GetRecommendationsUseCase getRecommendationsUseCase;

    @Mock
    private RecommendationMapper recommendationMapper;

    @InjectMocks
    private GetRecommendationsHandler getRecommendationsHandler;

    @Test
    @DisplayName("Should return list of recommendation responses")
    void shouldReturnRecommendationResponseList() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Recommendation recommendation = new Recommendation(bookId, "Clean Code", "A great book",
                2008, "http://cover.jpg", List.of(Genre.SCIENCE_TECHNOLOGY), Instant.now(), Instant.now());
        RecommendationResponse response = new RecommendationResponse(bookId, "Clean Code", "A great book",
                2008, "http://cover.jpg", List.of(Genre.SCIENCE_TECHNOLOGY));

        when(getRecommendationsUseCase.execute(any(GetRecommendationsCommand.class))).thenReturn(List.of(recommendation));
        when(recommendationMapper.toResponse(recommendation)).thenReturn(response);

        // Act
        List<RecommendationResponse> result = getRecommendationsHandler.handle(userId, 10, 0);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Clean Code");
        verify(getRecommendationsUseCase, times(1)).execute(any(GetRecommendationsCommand.class));
        verify(recommendationMapper, times(1)).toResponse(recommendation);
    }

    @Test
    @DisplayName("Should return empty list when no recommendations available")
    void shouldReturnEmptyListWhenNoRecommendations() {
        // Arrange
        UUID userId = UUID.randomUUID();

        when(getRecommendationsUseCase.execute(any(GetRecommendationsCommand.class))).thenReturn(Collections.emptyList());

        // Act
        List<RecommendationResponse> result = getRecommendationsHandler.handle(userId, 10, 0);

        // Assert
        assertThat(result).isEmpty();
        verifyNoInteractions(recommendationMapper);
    }
}
