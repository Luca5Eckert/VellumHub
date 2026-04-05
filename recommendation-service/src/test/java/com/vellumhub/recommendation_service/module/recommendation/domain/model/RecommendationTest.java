package com.vellumhub.recommendation_service.module.recommendation.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationTest {

    private Recommendation buildRecommendation() {
        return Recommendation.builder()
                .bookId(UUID.randomUUID())
                .title("Original Title")
                .description("Original Description")
                .author("Original Author")
                .coverUrl("http://original.url")
                .releaseYear(2010)
                .genres(List.of("Fantasy", "Adventure"))
                .build();
    }

    @Test
    @DisplayName("Should update all fields when all values are non-null and non-zero")
    void shouldUpdateAllFields() {
        Recommendation rec = buildRecommendation();
        List<String> newGenres = List.of("Science Fiction");

        rec.update("New Title", "New Desc", "New Author", "http://new.url", 2023, newGenres);

        assertThat(rec.getTitle()).isEqualTo("New Title");
        assertThat(rec.getDescription()).isEqualTo("New Desc");
        assertThat(rec.getAuthor()).isEqualTo("New Author");
        assertThat(rec.getCoverUrl()).isEqualTo("http://new.url");
        assertThat(rec.getReleaseYear()).isEqualTo(2023);
        assertThat(rec.getGenres()).containsExactly("Science Fiction");
    }

    @Test
    @DisplayName("Should not update title when null is passed")
    void shouldNotUpdateTitleWhenNull() {
        Recommendation rec = buildRecommendation();

        rec.update(null, "New Desc", "New Author", "http://new.url", 2023, List.of());

        assertThat(rec.getTitle()).isEqualTo("Original Title");
    }

    @Test
    @DisplayName("Should not update description when null is passed")
    void shouldNotUpdateDescriptionWhenNull() {
        Recommendation rec = buildRecommendation();

        rec.update("New Title", null, "New Author", "http://new.url", 2023, List.of());

        assertThat(rec.getDescription()).isEqualTo("Original Description");
    }

    @Test
    @DisplayName("Should not update author when null is passed")
    void shouldNotUpdateAuthorWhenNull() {
        Recommendation rec = buildRecommendation();

        rec.update("New Title", "New Desc", null, "http://new.url", 2023, List.of());

        assertThat(rec.getAuthor()).isEqualTo("Original Author");
    }

    @Test
    @DisplayName("Should not update coverUrl when null is passed")
    void shouldNotUpdateCoverUrlWhenNull() {
        Recommendation rec = buildRecommendation();

        rec.update("New Title", "New Desc", "New Author", null, 2023, List.of());

        assertThat(rec.getCoverUrl()).isEqualTo("http://original.url");
    }

    @Test
    @DisplayName("Should not update releaseYear when zero is passed")
    void shouldNotUpdateReleaseYearWhenZero() {
        Recommendation rec = buildRecommendation();

        rec.update("New Title", "New Desc", "New Author", "http://new.url", 0, List.of());

        assertThat(rec.getReleaseYear()).isEqualTo(2010);
    }

    @Test
    @DisplayName("Should not update genres when null is passed")
    void shouldNotUpdateGenresWhenNull() {
        Recommendation rec = buildRecommendation();

        rec.update("New Title", "New Desc", "New Author", "http://new.url", 2023, null);

        assertThat(rec.getGenres()).containsExactly("Fantasy", "Adventure");
    }

    @Test
    @DisplayName("Should build recommendation with all fields via builder")
    void shouldBuildWithAllFields() {
        UUID bookId = UUID.randomUUID();
        Recommendation rec = Recommendation.builder()
                .bookId(bookId)
                .title("Title")
                .description("Description")
                .author("Author")
                .coverUrl("http://cover.url")
                .releaseYear(2015)
                .genres(List.of("Drama"))
                .build();

        assertThat(rec.getBookId()).isEqualTo(bookId);
        assertThat(rec.getTitle()).isEqualTo("Title");
        assertThat(rec.getDescription()).isEqualTo("Description");
        assertThat(rec.getAuthor()).isEqualTo("Author");
        assertThat(rec.getCoverUrl()).isEqualTo("http://cover.url");
        assertThat(rec.getReleaseYear()).isEqualTo(2015);
        assertThat(rec.getGenres()).containsExactly("Drama");
    }
}
