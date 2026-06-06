package com.vellumhub.recommendation_service.module.recommendation.presentation.mapper;

import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationMapperTest {

    private final RecommendationMapper mapper = new RecommendationMapper();

    @Test
    @DisplayName("Should map all fields from Recommendation to RecommendationResponse")
    void shouldMapAllFields() {
        UUID bookId = UUID.randomUUID();
        Recommendation recommendation = Recommendation.builder()
                .bookId(bookId)
                .title("Effective Java")
                .description("Best practices for Java programming")
                .author("Joshua Bloch")
                .coverUrl("https://example.com/effectivejava.jpg")
                .releaseYear(2018)
                .genres(List.of("Programming", "Java"))
                .build();

        RecommendationResponse response = mapper.toResponse(recommendation);

        assertThat(response.id()).isEqualTo(bookId);
        assertThat(response.title()).isEqualTo("Effective Java");
        assertThat(response.description()).isEqualTo("Best practices for Java programming");
        assertThat(response.author()).isEqualTo("Joshua Bloch");
        assertThat(response.coverUrl()).isEqualTo("https://example.com/effectivejava.jpg");
        assertThat(response.releaseYear()).isEqualTo(2018);
        assertThat(response.genres()).containsExactly("Programming", "Java");
    }

    @Test
    @DisplayName("Should map recommendation with empty genres list")
    void shouldMapWithEmptyGenres() {
        UUID bookId = UUID.randomUUID();
        Recommendation recommendation = Recommendation.builder()
                .bookId(bookId)
                .title("Unknown Book")
                .description("Some description")
                .author("Some Author")
                .coverUrl("https://example.com/cover.jpg")
                .releaseYear(2000)
                .genres(List.of())
                .build();

        RecommendationResponse response = mapper.toResponse(recommendation);

        assertThat(response.genres()).isEmpty();
        assertThat(response.id()).isEqualTo(bookId);
    }
}
