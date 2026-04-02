package com.vellumhub.recommendation_service.module.user_profile.infrastructure.repository.provider.embedding;

import com.vellumhub.recommendation_service.module.user_profile.domain.port.EmbeddingUserProfileProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class LangChain4jEmbeddingUserProfileProvider implements EmbeddingUserProfileProvider {

    private static final int MAX_ABOUT_LENGTH = 2000;

    private final EmbeddingModel embeddingModel;

    public LangChain4jEmbeddingUserProfileProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] of(List<String> genres, String about) {

        String semanticContent = buildSemanticContent(genres, about);

        var rawVectors = embeddingModel
                .embed(semanticContent)
                .content()
                .vector();

        return normalizeVectors(rawVectors);
    }

    private float[] normalizeVectors(float[] rawVectors) {
        double sumSqrs = 0.0;
        for (float value : rawVectors) {
            sumSqrs += value * value;
        }
        double magnitude = Math.sqrt(sumSqrs);

        if (magnitude < 1e-9) {
            return rawVectors;
        }

        float[] normalizedVectors = new float[rawVectors.length];
        for (int i = 0; i < rawVectors.length; i++) {
            normalizedVectors[i] = (float) (rawVectors[i] / magnitude);
        }
        return normalizedVectors;
    }

    private String buildSemanticContent(List<String> genres, String about) {

        String normalizedGenres = normalizeGenres(genres);
        String normalizedAbout = normalizeAbout(about);

        StringBuilder content = new StringBuilder();

        if (!normalizedGenres.isBlank()) {
            content.append("Preferred genres: ")
                    .append(normalizedGenres)
                    .append(".\n");
        }

        if (!normalizedAbout.isBlank()) {
            content.append("Reader profile: ")
                    .append(normalizedAbout);
        }

        if (content.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate embedding without textual content.");
        }

        return content.toString().trim();
    }

    private String normalizeGenres(List<String> genres) {

        if (genres == null || genres.isEmpty()) {
            return "";
        }

        return genres.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String normalizeAbout(String about) {

        String cleaned = normalizeText(about);

        if (cleaned.length() > MAX_ABOUT_LENGTH) {
            cleaned = cleaned.substring(0, MAX_ABOUT_LENGTH).trim();
        }

        return cleaned;
    }

    private String normalizeText(String text) {

        if (text == null || text.isBlank()) {
            return "";
        }

        String cleaned = text
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();

        return Normalizer.normalize(cleaned, Normalizer.Form.NFKC);
    }
}