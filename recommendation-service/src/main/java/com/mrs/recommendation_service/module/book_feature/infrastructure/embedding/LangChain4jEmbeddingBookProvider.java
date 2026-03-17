package com.mrs.recommendation_service.module.book_feature.infrastructure.embedding;

import com.mrs.recommendation_service.module.book_feature.domain.model.Genre;
import com.mrs.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class LangChain4jEmbeddingBookProvider implements EmbeddingBookProvider {

    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private final EmbeddingModel embeddingModel;

    public LangChain4jEmbeddingBookProvider(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public float[] of(String title, String author, String description, List<Genre> genres) {

        String semanticContent = buildSemanticContent(title, author, description, genres);

        return embeddingModel
                .embed(semanticContent)
                .content()
                .vector();
    }

    private String buildSemanticContent(String title, String author, String description, List<Genre> genres) {

        String normalizedTitle = normalizeText(title);
        String normalizedAuthor = normalizeText(author);
        String normalizedDescription = normalizeDescription(description);
        String normalizedGenres = normalizeGenres(genres);

        StringBuilder content = new StringBuilder();

        if (!normalizedTitle.isBlank()) {
            content.append("Book title: ")
                    .append(normalizedTitle)
                    .append(".\n");
        }

        if (!normalizedAuthor.isBlank()) {
            content.append("Author: ")
                    .append(normalizedAuthor)
                    .append(".\n");
        }

        if (!normalizedGenres.isBlank()) {
            content.append("Genres: ")
                    .append(normalizedGenres)
                    .append(".\n");
        }

        if (!normalizedDescription.isBlank()) {
            content.append("Description: ")
                    .append(normalizedDescription);
        }

        if (content.isEmpty()) {
            throw new IllegalArgumentException("Cannot generate embedding without textual content.");
        }

        return content.toString().trim();
    }

    private String normalizeGenres(List<Genre> genres) {

        if (genres == null || genres.isEmpty()) {
            return "";
        }

        return genres.stream()
                .filter(Objects::nonNull)
                .map(Genre::getSemanticLabel)
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String normalizeDescription(String description) {

        String cleaned = normalizeText(description);

        if (cleaned.length() > MAX_DESCRIPTION_LENGTH) {
            cleaned = cleaned.substring(0, MAX_DESCRIPTION_LENGTH).trim();
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