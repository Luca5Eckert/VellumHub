package com.vellumhub.recommendation_service.module.user_profile.infrastructure.repository.provider.embedding;

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
class LangChain4jEmbeddingUserProfileProviderTest {

    @Mock
    private EmbeddingModel embeddingModel;

    private LangChain4jEmbeddingUserProfileProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LangChain4jEmbeddingUserProfileProvider(embeddingModel);
    }

    private void mockEmbedding(float[] vector) {
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(Embedding.from(vector)));
    }

    @Test
    void shouldReturnNormalizedVectorForFullInput() {
        mockEmbedding(new float[]{3.0f, 4.0f});

        float[] result = provider.of(List.of("Fantasy", "Sci-Fi"), "I enjoy epic world-building.");

        float[] expected = {3.0f / 5.0f, 4.0f / 5.0f};
        assertThat(result).usingComparatorWithPrecision(1e-6f).containsExactly(expected);
    }

    @Test
    void shouldIncludeGenresAndAboutInPrompt() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy"), "I enjoy epic world-building.");

        verify(embeddingModel).embed(captor.capture());
        String prompt = captor.getValue();
        assertThat(prompt).contains("Preferred genres: Fantasy");
        assertThat(prompt).contains("Reader profile: I enjoy epic world-building.");
    }

    @Test
    void shouldOmitGenresSectionWhenGenresIsNull() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(null, "I enjoy epic world-building.");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Preferred genres:");
    }

    @Test
    void shouldOmitGenresSectionWhenGenresIsEmpty() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of(), "I enjoy epic world-building.");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Preferred genres:");
    }

    @Test
    void shouldOmitAboutSectionWhenAboutIsNull() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy"), null);

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Reader profile:");
    }

    @Test
    void shouldOmitAboutSectionWhenAboutIsBlank() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy"), "   ");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("Reader profile:");
    }

    @Test
    void shouldDeduplicateGenres() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy", "Fantasy"), "I enjoy epic world-building.");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).containsOnlyOnce("Fantasy");
    }

    @Test
    void shouldHandleMultipleGenresJoinedByComma() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy", "Mystery", "Thriller"), "I enjoy epic world-building.");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("Fantasy, Mystery, Thriller");
    }

    @Test
    void shouldTruncateAboutExceedingMaxLength() {
        String longAbout = "B".repeat(3000);
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy"), longAbout);

        verify(embeddingModel).embed(captor.capture());
        String prompt = captor.getValue();
        int aboutStart = prompt.indexOf("Reader profile: ") + "Reader profile: ".length();
        String embeddedAbout = prompt.substring(aboutStart);
        assertThat(embeddedAbout.length()).isLessThanOrEqualTo(2000);
    }

    @Test
    void shouldStripHtmlTagsFromAbout() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy"), "<b>I enjoy epic world-building.</b>");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).doesNotContain("<b>").doesNotContain("</b>");
        assertThat(captor.getValue()).contains("I enjoy epic world-building.");
    }

    @Test
    void shouldNormalizeWhitespaceInAbout() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy"), "I enjoy  epic\n\nworld-building.\t");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("I enjoy epic world-building.");
    }

    @Test
    void shouldThrowWhenAllFieldsAreNullOrBlank() {
        assertThatThrownBy(() -> provider.of(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot generate embedding without textual content.");
    }

    @Test
    void shouldThrowWhenGenresIsEmptyAndAboutIsBlank() {
        assertThatThrownBy(() -> provider.of(List.of(), "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnOriginalVectorWhenMagnitudeIsNearZero() {
        mockEmbedding(new float[]{0.0f, 0.0f, 0.0f});

        float[] result = provider.of(List.of("Fantasy"), "I enjoy epic world-building.");

        assertThat(result).containsExactly(0.0f, 0.0f, 0.0f);
    }

    @Test
    void shouldReturnUnitVectorForAlreadyNormalizedInput() {
        mockEmbedding(new float[]{1.0f, 0.0f});

        float[] result = provider.of(List.of("Fantasy"), "I enjoy epic world-building.");

        assertThat(result).usingComparatorWithPrecision(1e-6f).containsExactly(1.0f, 0.0f);
    }

    @Test
    void shouldProduceNormalizedVectorWithCorrectMagnitude() {
        mockEmbedding(new float[]{1.0f, 2.0f, 2.0f});

        float[] result = provider.of(List.of("Fantasy"), "I enjoy epic world-building.");

        double magnitude = 0.0;
        for (float v : result) magnitude += v * v;
        assertThat(Math.sqrt(magnitude)).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void shouldCallEmbeddingModelExactlyOnce() {
        mockEmbedding(new float[]{1.0f});

        provider.of(List.of("Fantasy"), "I enjoy epic world-building.");

        verify(embeddingModel, times(1)).embed(anyString());
    }

    @Test
    void shouldWorkWithOnlyAboutProvided() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(null, "I enjoy epic world-building.");

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("Reader profile: I enjoy epic world-building.");
    }

    @Test
    void shouldWorkWithOnlyGenresProvided() {
        mockEmbedding(new float[]{1.0f});
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

        provider.of(List.of("Fantasy", "Mystery"), null);

        verify(embeddingModel).embed(captor.capture());
        assertThat(captor.getValue()).contains("Preferred genres: Fantasy, Mystery");
    }
}