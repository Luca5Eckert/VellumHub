package com.vellumhub.recommendation_service.module.recommendation.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.recommendation.application.command.GetRecommendationsCommand;
import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.domain.port.RecommendationRepository;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRecommendationsUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private RecommendationRepository recommendationRepository;

    private GetRecommendationsUseCase getRecommendationsUseCase;

    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        getRecommendationsUseCase = new GetRecommendationsUseCase(
                bookFeatureRepository,
                recommendationRepository,
                new VellumHubMetrics(meterRegistry)
        );
    }

    @Test
    @DisplayName("Should return recommendations based on user's book interactions")
    void shouldReturnRecommendationsForUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetRecommendationsCommand command = new GetRecommendationsCommand(userId, 10, 0);
        List<UUID> userBookIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        Recommendation recommendation = createRecommendation("User Discovery");

        when(bookFeatureRepository.findAllByUserId(userId, 10, 0)).thenReturn(userBookIds);
        when(recommendationRepository.findAllById(userBookIds)).thenReturn(List.of(recommendation));

        // Act
        List<Recommendation> result = getRecommendationsUseCase.execute(command);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getTitle()).isEqualTo("User Discovery");

        verify(bookFeatureRepository).findAllByUserId(userId, 10, 0);
        verify(recommendationRepository).findAllById(userBookIds);
        verify(bookFeatureRepository, never()).findMostPopularMedias(anyInt(), anyInt());
        assertThat(recommendationsGeneratedCount()).isEqualTo(1.0);
        assertThat(recommendationGenerationTimerCount("success")).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should fallback to popular books when user has no interactions")
    void shouldFallbackToPopularBooksWhenNoUserInteractions() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetRecommendationsCommand command = new GetRecommendationsCommand(userId, 5, 0);
        List<UUID> popularBookIds = List.of(UUID.randomUUID());
        Recommendation popularRec = createRecommendation("Popular Choice");

        when(bookFeatureRepository.findAllByUserId(userId, 5, 0)).thenReturn(Collections.emptyList());
        when(bookFeatureRepository.findMostPopularMedias(5, 0)).thenReturn(popularBookIds);
        when(recommendationRepository.findAllById(popularBookIds)).thenReturn(List.of(popularRec));

        // Act
        List<Recommendation> result = getRecommendationsUseCase.execute(command);

        // Assert
        assertThat(result).containsExactly(popularRec);
        verify(bookFeatureRepository).findMostPopularMedias(5, 0);
        assertThat(recommendationsGeneratedCount()).isEqualTo(1.0);
        assertThat(recommendationGenerationTimerCount("success")).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should fallback to popular books when user interaction list is null")
    void shouldFallbackToPopularBooksWhenUserInteractionsNull() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetRecommendationsCommand command = new GetRecommendationsCommand(userId, 5, 0);
        List<UUID> popularBookIds = List.of(UUID.randomUUID());

        when(bookFeatureRepository.findAllByUserId(userId, 5, 0)).thenReturn(null);
        when(bookFeatureRepository.findMostPopularMedias(5, 0)).thenReturn(popularBookIds);
        when(recommendationRepository.findAllById(popularBookIds)).thenReturn(Collections.emptyList());

        // Act
        List<Recommendation> result = getRecommendationsUseCase.execute(command);

        // Assert
        assertThat(result).isEmpty();
        verify(bookFeatureRepository).findMostPopularMedias(5, 0);
        assertThat(emptyRecommendationsCount()).isEqualTo(1.0);
        assertThat(recommendationGenerationTimerCount("empty")).isEqualTo(1L);
    }

    private Recommendation createRecommendation(String title) {
        return new Recommendation(
                UUID.randomUUID(),
                title,
                "Description",
                2024,
                "http://image.url",
                "Author",
                Collections.emptyList()
        );
    }

    private double recommendationsGeneratedCount() {
        return meterRegistry.get(VellumHubMetrics.RECOMMENDATIONS_GENERATED)
                .tag("operation", "recommendation_generation")
                .tag("result", "success")
                .counter()
                .count();
    }

    private double emptyRecommendationsCount() {
        return meterRegistry.get(VellumHubMetrics.RECOMMENDATION_EMPTY_RESULTS)
                .tag("operation", "recommendation_generation")
                .tag("result", "empty")
                .counter()
                .count();
    }

    private long recommendationGenerationTimerCount(String result) {
        return meterRegistry.get(VellumHubMetrics.RECOMMENDATION_GENERATION_DURATION)
                .tag("operation", "recommendation_generation")
                .tag("result", result)
                .timer()
                .count();
    }
}
