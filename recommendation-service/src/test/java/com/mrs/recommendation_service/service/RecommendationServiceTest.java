package com.mrs.recommendation_service.service;

import com.mrs.recommendation_service.domain.handler.media_feature.GetRecommendationsHandler;
import com.mrs.recommendation_service.domain.model.Recommendation;
import com.mrs.recommendation_service.domain.service.RecommendationService;
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
import static org.mockito.Mockito.*;

/**
 * Unit tests for RecommendationService.
 * Tests cover all service methods with mocked handlers.
 * Follows the pattern: condition_expectedBehavior.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock
    private GetRecommendationsHandler getRecommendationsHandler;

    @InjectMocks
    private RecommendationService recommendationService;

    @Nested
    @DisplayName("get() method tests")
    class GetTests {

        @Test
        @DisplayName("whenValidUserId_shouldReturnRecommendations")
        void whenValidUserId_shouldReturnRecommendations() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId1 = UUID.randomUUID();
            UUID mediaId2 = UUID.randomUUID();

            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(
                            mediaId1,
                            List.of("ACTION", "SCIFI"),
                            85.5,
                            92.3,
                            88.0
                    ),
                    new Recommendation(
                            mediaId2,
                            List.of("DRAMA", "COMEDY"),
                            78.2,
                            85.1,
                            81.5
                    )
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).mediaId()).isEqualTo(mediaId1);
            assertThat(result.get(0).genres()).containsExactly("ACTION", "SCIFI");
            assertThat(result.get(0).popularityScore()).isEqualTo(85.5);
            assertThat(result.get(0).recommendationScore()).isEqualTo(92.3);
            assertThat(result.get(0).contentScore()).isEqualTo(88.0);

            assertThat(result.get(1).mediaId()).isEqualTo(mediaId2);
            assertThat(result.get(1).genres()).containsExactly("DRAMA", "COMEDY");

            verify(getRecommendationsHandler, times(1)).execute(userId);
        }

        @Test
        @DisplayName("whenUserHasNoRecommendations_shouldReturnEmptyList")
        void whenUserHasNoRecommendations_shouldReturnEmptyList() {
            // Arrange
            UUID userId = UUID.randomUUID();

            when(getRecommendationsHandler.execute(userId)).thenReturn(List.of());

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result).isEmpty();
            verify(getRecommendationsHandler, times(1)).execute(userId);
        }

        @Test
        @DisplayName("whenSingleRecommendation_shouldReturnSingleItemList")
        void whenSingleRecommendation_shouldReturnSingleItemList() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("ACTION"),
                            95.0,
                            98.5,
                            96.0
                    )
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result).hasSize(1);
            assertThat(result.get(0).mediaId()).isEqualTo(mediaId);
            assertThat(result.get(0).popularityScore()).isEqualTo(95.0);
        }

        @Test
        @DisplayName("whenRecommendationWithHighScores_shouldReturnCorrectValues")
        void whenRecommendationWithHighScores_shouldReturnCorrectValues() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("ACTION", "THRILLER"),
                            100.0,   // max popularity
                            100.0,   // max recommendation
                            100.0    // max content
                    )
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result.get(0).popularityScore()).isEqualTo(100.0);
            assertThat(result.get(0).recommendationScore()).isEqualTo(100.0);
            assertThat(result.get(0).contentScore()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("whenRecommendationWithLowScores_shouldReturnCorrectValues")
        void whenRecommendationWithLowScores_shouldReturnCorrectValues() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("DOCUMENTARY"),
                            0.1,    // very low popularity
                            0.5,    // very low recommendation
                            0.3     // very low content
                    )
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result.get(0).popularityScore()).isEqualTo(0.1);
            assertThat(result.get(0).recommendationScore()).isEqualTo(0.5);
            assertThat(result.get(0).contentScore()).isEqualTo(0.3);
        }

        @Test
        @DisplayName("whenMultipleGenres_shouldPreserveGenreOrder")
        void whenMultipleGenres_shouldPreserveGenreOrder() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<String> genres = List.of("ACTION", "SCIFI", "THRILLER", "ADVENTURE", "DRAMA");

            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(
                            mediaId,
                            genres,
                            85.5,
                            92.3,
                            88.0
                    )
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result.get(0).genres())
                    .containsExactly("ACTION", "SCIFI", "THRILLER", "ADVENTURE", "DRAMA");
        }

        @Test
        @DisplayName("whenRecommendationWithNullScores_shouldHandleGracefully")
        void whenRecommendationWithNullScores_shouldHandleGracefully() {
            // Arrange
            UUID userId = UUID.randomUUID();
            UUID mediaId = UUID.randomUUID();

            List<Recommendation> expectedRecommendations = List.of(
                    new Recommendation(
                            mediaId,
                            List.of("ACTION"),
                            null,   // null popularity
                            null,   // null recommendation
                            null    // null content
                    )
            );

            when(getRecommendationsHandler.execute(userId)).thenReturn(expectedRecommendations);

            // Act
            List<Recommendation> result = recommendationService.get(userId);

            // Assert
            assertThat(result.get(0).mediaId()).isEqualTo(mediaId);
            assertThat(result.get(0).popularityScore()).isNull();
            assertThat(result.get(0).recommendationScore()).isNull();
            assertThat(result.get(0).contentScore()).isNull();
        }

        @Test
        @DisplayName("whenDifferentUserIds_shouldCallHandlerWithCorrectId")
        void whenDifferentUserIds_shouldCallHandlerWithCorrectId() {
            // Arrange
            UUID userId1 = UUID.randomUUID();
            UUID userId2 = UUID.randomUUID();

            when(getRecommendationsHandler.execute(any(UUID.class))).thenReturn(List.of());

            // Act
            recommendationService.get(userId1);
            recommendationService.get(userId2);

            // Assert
            verify(getRecommendationsHandler, times(1)).execute(userId1);
            verify(getRecommendationsHandler, times(1)).execute(userId2);
            verify(getRecommendationsHandler, times(2)).execute(any(UUID.class));
        }
    }
}
