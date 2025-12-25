package com.mrs.recommendation_service.service;

import com.mrs.recommendation_service.handler.GetRecommendationsHandler;
import com.mrs.recommendation_service.model.Recommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecommendationService Tests")
class RecommendationServiceTest {

    @Mock
    private GetRecommendationsHandler getRecommendationsHandler;

    @InjectMocks
    private RecommendationService recommendationService;

    @Nested
    @DisplayName("get() method")
    class GetTests {

        @Test
        @DisplayName("Should return recommendations for user")
        void get_WithValidUserId_ReturnsRecommendations() {
            UUID userId = UUID.randomUUID();
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();
            
            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(mediaId1, List.of("ACTION"), 0.8, 0.95, 0.9),
                    new Recommendation(mediaId2, List.of("HORROR"), 0.7, 0.87, 0.85)
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            List<Recommendation> result = recommendationService.get(userId);

            assertThat(result).hasSize(2);
            assertThat(result).isEqualTo(expectedRecommendations);
            verify(getRecommendationsHandler, times(1)).execute(userId);
        }

        @Test
        @DisplayName("Should return empty list when no recommendations")
        void get_WhenNoRecommendations_ReturnsEmptyList() {
            UUID userId = UUID.randomUUID();

            when(getRecommendationsHandler.execute(userId)).thenReturn(List.of());

            List<Recommendation> result = recommendationService.get(userId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Should propagate exception when user profile not found")
        void get_WhenUserProfileNotFound_PropagatesException() {
            UUID userId = UUID.randomUUID();

            when(getRecommendationsHandler.execute(userId))
                    .thenThrow(new RuntimeException("User profile not found"));

            assertThatThrownBy(() -> recommendationService.get(userId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("User profile not found");
        }
    }
}
