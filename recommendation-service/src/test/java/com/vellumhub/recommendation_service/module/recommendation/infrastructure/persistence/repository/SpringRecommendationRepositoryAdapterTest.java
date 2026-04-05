package com.vellumhub.recommendation_service.module.recommendation.infrastructure.persistence.repository;

import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpringRecommendationRepositoryAdapterTest {

    @Mock
    private RecommendationRepositoryJpa recommendationRepositoryJpa;

    @InjectMocks
    private SpringRecommendationRepositoryAdapter adapter;

    private Recommendation buildRecommendation(UUID bookId) {
        return Recommendation.builder()
                .bookId(bookId)
                .title("Test Book")
                .description("A test description")
                .author("Test Author")
                .coverUrl("https://cover.url")
                .releaseYear(2022)
                .genres(List.of("Fiction"))
                .build();
    }

    @Test
    @DisplayName("Should delegate save to JPA repository")
    void shouldDelegateSave() {
        UUID bookId = UUID.randomUUID();
        Recommendation rec = buildRecommendation(bookId);

        adapter.save(rec);

        verify(recommendationRepositoryJpa, times(1)).save(rec);
    }

    @Test
    @DisplayName("Should delegate findById to JPA repository and return result")
    void shouldDelegateFindById() {
        UUID bookId = UUID.randomUUID();
        Recommendation rec = buildRecommendation(bookId);
        when(recommendationRepositoryJpa.findById(bookId)).thenReturn(Optional.of(rec));

        Optional<Recommendation> result = adapter.findById(bookId);

        assertThat(result).isPresent().contains(rec);
        verify(recommendationRepositoryJpa).findById(bookId);
    }

    @Test
    @DisplayName("Should return empty Optional when recommendation not found by id")
    void shouldReturnEmptyWhenNotFound() {
        UUID bookId = UUID.randomUUID();
        when(recommendationRepositoryJpa.findById(bookId)).thenReturn(Optional.empty());

        Optional<Recommendation> result = adapter.findById(bookId);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should delegate deleteById to JPA repository")
    void shouldDelegateDeleteById() {
        UUID bookId = UUID.randomUUID();

        adapter.deleteById(bookId);

        verify(recommendationRepositoryJpa, times(1)).deleteById(bookId);
    }

    @Test
    @DisplayName("Should delegate findAllById to JPA repository and return list")
    void shouldDelegateFindAllById() {
        UUID bookId1 = UUID.randomUUID();
        UUID bookId2 = UUID.randomUUID();
        List<UUID> ids = List.of(bookId1, bookId2);
        List<Recommendation> recommendations = List.of(
                buildRecommendation(bookId1),
                buildRecommendation(bookId2)
        );
        when(recommendationRepositoryJpa.findAllById(ids)).thenReturn(recommendations);

        List<Recommendation> result = adapter.findAllById(ids);

        assertThat(result).hasSize(2);
        verify(recommendationRepositoryJpa).findAllById(ids);
    }

    @Test
    @DisplayName("Should return empty list when no recommendations found for given ids")
    void shouldReturnEmptyListWhenNoRecommendationsFound() {
        List<UUID> ids = List.of(UUID.randomUUID());
        when(recommendationRepositoryJpa.findAllById(ids)).thenReturn(List.of());

        List<Recommendation> result = adapter.findAllById(ids);

        assertThat(result).isEmpty();
    }
}
