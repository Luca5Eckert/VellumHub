package com.mrs.recommendation_service.module.recommendation.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.mrs.recommendation_service.module.book_feature.domain.port.CatalogClient;
import com.mrs.recommendation_service.module.recommendation.domain.command.GetRecommendationsCommand;
import com.mrs.recommendation_service.module.recommendation.domain.model.Recommendation;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetRecommendationsUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private CatalogClient client;

    @InjectMocks
    private GetRecommendationsUseCase getRecommendationsUseCase;

    @Test
    @DisplayName("Should return recommendations based on user's book interactions")
    void shouldReturnRecommendationsForUser() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetRecommendationsCommand command = new GetRecommendationsCommand(userId, 10, 0);

        List<UUID> userBookIds = List.of(UUID.randomUUID(), UUID.randomUUID());
        Recommendation recommendation = new Recommendation(UUID.randomUUID(), "Book Title", "Description",
                2020, "http://cover.jpg", List.of(Genre.FANTASY), Instant.now(), Instant.now());

        when(bookFeatureRepository.findAllByUserId(userId, 10, 0)).thenReturn(userBookIds);
        when(client.fetchRecommendationsBatch(userBookIds)).thenReturn(List.of(recommendation));

        // Act
        List<Recommendation> result = getRecommendationsUseCase.execute(command);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Book Title");
        verify(bookFeatureRepository, times(1)).findAllByUserId(userId, 10, 0);
        verify(client, times(1)).fetchRecommendationsBatch(userBookIds);
        verify(bookFeatureRepository, never()).findMostPopularMedias(anyInt(), anyInt());
    }

    @Test
    @DisplayName("Should fallback to popular books when user has no interactions")
    void shouldFallbackToPopularBooksWhenNoUserInteractions() {
        // Arrange
        UUID userId = UUID.randomUUID();
        GetRecommendationsCommand command = new GetRecommendationsCommand(userId, 5, 0);

        List<UUID> popularBookIds = List.of(UUID.randomUUID());
        Recommendation recommendation = new Recommendation(UUID.randomUUID(), "Popular Book", "Description",
                2019, "http://popular.jpg", List.of(Genre.ROMANCE), Instant.now(), Instant.now());

        when(bookFeatureRepository.findAllByUserId(userId, 5, 0)).thenReturn(Collections.emptyList());
        when(bookFeatureRepository.findMostPopularMedias(5, 0)).thenReturn(popularBookIds);
        when(client.fetchRecommendationsBatch(popularBookIds)).thenReturn(List.of(recommendation));

        // Act
        List<Recommendation> result = getRecommendationsUseCase.execute(command);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("Popular Book");
        verify(bookFeatureRepository, times(1)).findMostPopularMedias(5, 0);
        verify(client, times(1)).fetchRecommendationsBatch(popularBookIds);
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
        when(client.fetchRecommendationsBatch(popularBookIds)).thenReturn(Collections.emptyList());

        // Act
        List<Recommendation> result = getRecommendationsUseCase.execute(command);

        // Assert
        assertThat(result).isEmpty();
        verify(bookFeatureRepository, times(1)).findMostPopularMedias(5, 0);
    }
}
