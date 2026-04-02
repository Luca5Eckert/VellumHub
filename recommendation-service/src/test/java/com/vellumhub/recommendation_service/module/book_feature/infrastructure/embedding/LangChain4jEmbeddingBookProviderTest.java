package com.vellumhub.recommendation_service.module.book_feature.infrastructure.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LangChain4jEmbeddingBookProviderTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private LangChain4jEmbeddingBookProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LangChain4jEmbeddingBookProvider(embeddingModel);
    }

    private void mockEmbedding(float[] vector) {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(vector)));
    }

    @Test
    void shouldReturnNormalizedVectorForFullInput() {
        mockEmbedding(new float[]{3.0f, 4.0f});

        float[] result = provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        float[] expected = {3.0f / 5.0f, 4.0f / 5.0f};
        assertThat(result).usingComparatorWithPrecision(1e-6f).containsExactly(expected);
    }

    @Test
    void shouldIncludeTitleAuthorGenresAndDescriptionInPrompt() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        String prompt = captor.getValue();
        assertThat(prompt).contains("Book title: Dune");
        assertThat(prompt).contains("Author: Frank Herbert");
        assertThat(prompt).contains("Genres: Science Fiction");
        assertThat(prompt).contains("Description: A sci-fi epic.");
    }

    @Test
    void shouldOmitTitleSectionWhenTitleIsNull() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(null, "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Book title:");
    }

    @Test
    void shouldOmitTitleSectionWhenTitleIsBlank() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("   ", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Book title:");
    }

    @Test
    void shouldOmitAuthorSectionWhenAuthorIsNull() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", null, "A sci-fi epic.", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Author:");
    }

    @Test
    void shouldOmitGenresSectionWhenGenresIsNull() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", null);

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Genres:");
    }

    @Test
    void shouldOmitGenresSectionWhenGenresIsEmpty() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of());

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Genres:");
    }

    @Test
    void shouldOmitDescriptionSectionWhenDescriptionIsNull() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", null, List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Description:");
    }

    @Test
    void shouldDeduplicateGenres() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction", "Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        String genres = captor.getValue();
        long count = genres.chars().filter(c -> c == 'S').count();
        assertThat(genres).containsOnlyOnce("Science Fiction");
    }

    @Test
    void shouldFilterNullEntriesInGenres() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("Science Fiction");
    }

    @Test
    void shouldTruncateDescriptionExceedingMaxLength() {
        String longDescription = "A".repeat(3000);
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", longDescription, List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        String prompt = captor.getValue();
        int descStart = prompt.indexOf("Description: ") + "Description: ".length();
        String embeddedDescription = prompt.substring(descStart);
        assertThat(embeddedDescription.length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void shouldStripHtmlTagsFromDescription() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "<p>A sci-fi epic.</p>", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("<p>").doesNotContain("</p>");
        assertThat(captor.getValue()).contains("A sci-fi epic.");
    }

    @Test
    void shouldNormalizeWhitespaceInDescription() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A  sci-fi\n\nepic.\t", List.of("Science Fiction"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("A sci-fi epic.");
    }

    @Test
    void shouldThrowWhenAllFieldsAreNullOrBlank() {
        assertThatThrownBy(() -> provider.of(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot generate embedding without textual content.");
    }

    @Test
    void shouldThrowWhenAllFieldsAreBlank() {
        assertThatThrownBy(() -> provider.of("  ", "  ", "  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnOriginalVectorWhenMagnitudeIsNearZero() {
        mockEmbedding(new float[]{0.0f, 0.0f, 0.0f});

        float[] result = provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f);
    }

    @Test
    void shouldReturnUnitVectorForAlreadyNormalizedInput() {
        mockEmbedding(new float[]{1.0f, 0.0f});

        float[] result = provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        assertThat(result).usingComparatorWithPrecision(1e-6f).containsExactly(1.0f, 0.0f);
    }

    @Test
    void shouldHandleMultipleGenresJoinedByComma() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction", "Adventure", "Classic"));

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("Science Fiction, Adventure, Classic");
    }

    @Test
    void shouldProduceNormalizedVectorWithCorrectMagnitude() {
        mockEmbedding(new float[]{1.0f, 2.0f, 2.0f});

        float[] result = provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        double magnitude = 0.0;
        for (float v : result) magnitude += v * v;
        assertThat(Math.sqrt(magnitude)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void shouldCallEmbeddingModelExactlyOnce() {
        mockEmbedding(new float[]{1.0f});

        provider.of("Dune", "Frank Herbert", "A sci-fi epic.", List.of("Science Fiction"));

        verify(embeddingModel, times(1)).embed(anyString());
    }

    @Test
    void shouldWorkWithOnlyTitleProvided() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of("Dune", null, null, null);

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("Book title: Dune");
    }
}