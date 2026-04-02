package com.vellumhub.recommendation_service.module.book_feature.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookFeatureTest {

    private float[] createValidEmbedding() {
        return new float[384];
    }

    private float[] createInvalidEmbedding() {
        return new float[128];
    }

    @Test
    void shouldCreateBookFeatureSuccessfully() {
        UUID bookId = UUID.randomUUID();
        float[] embedding = createValidEmbedding();
        double popularityScore = 4.5;

        BookFeature bookFeature = BookFeature.create(bookId, embedding, popularityScore);

        assertNotNull(bookFeature);
        assertEquals(bookId, bookFeature.getBookId());
        assertArrayEquals(embedding, bookFeature.getEmbedding());
        assertEquals(popularityScore, bookFeature.getPopularityScore());
        assertNotNull(bookFeature.getLastUpdated());
    }

    @Test
    void shouldThrowExceptionWhenCreatingWithNullEmbedding() {
        UUID bookId = UUID.randomUUID();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                BookFeature.create(bookId, null, 4.5)
        );

        assertEquals("Embedding vector cannot be null and must have at least 384 dimensions", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenCreatingWithInvalidEmbeddingLength() {
        UUID bookId = UUID.randomUUID();
        float[] invalidEmbedding = createInvalidEmbedding();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                BookFeature.create(bookId, invalidEmbedding, 4.5)
        );

        assertEquals("Embedding vector cannot be null and must have at least 384 dimensions", exception.getMessage());
    }

    @Test
    void shouldUpdateEmbeddingSuccessfully() throws InterruptedException {
        BookFeature bookFeature = BookFeature.create(UUID.randomUUID(), createValidEmbedding(), 4.5);
        Instant initialUpdated = bookFeature.getLastUpdated();

        Thread.sleep(1);

        float[] newEmbedding = createValidEmbedding();
        newEmbedding[0] = 1.5f;

        bookFeature.updateEmbedding(newEmbedding);

        assertArrayEquals(newEmbedding, bookFeature.getEmbedding());
        assertTrue(bookFeature.getLastUpdated().isAfter(initialUpdated));
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithNullEmbedding() {
        BookFeature bookFeature = BookFeature.create(UUID.randomUUID(), createValidEmbedding(), 4.5);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                bookFeature.updateEmbedding(null)
        );

        assertEquals("Embedding vector cannot be null and must have at least 384 dimensions", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenUpdatingWithInvalidEmbeddingLength() {
        BookFeature bookFeature = BookFeature.create(UUID.randomUUID(), createValidEmbedding(), 4.5);
        float[] invalidEmbedding = createInvalidEmbedding();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                bookFeature.updateEmbedding(invalidEmbedding)
        );

        assertEquals("Embedding vector cannot be null and must have at least 384 dimensions", exception.getMessage());
    }

    @Test
    void shouldUpdatePopularitySuccessfully() throws InterruptedException {
        BookFeature bookFeature = BookFeature.create(UUID.randomUUID(), createValidEmbedding(), 4.5);
        Instant initialUpdated = bookFeature.getLastUpdated();
        double newScore = 8.9;

        Thread.sleep(1);

        bookFeature.updatePopularity(newScore);

        assertEquals(newScore, bookFeature.getPopularityScore());
        assertTrue(bookFeature.getLastUpdated().isAfter(initialUpdated));
    }
}