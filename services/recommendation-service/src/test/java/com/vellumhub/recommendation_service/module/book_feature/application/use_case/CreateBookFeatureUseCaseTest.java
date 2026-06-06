package com.vellumhub.recommendation_service.module.book_feature.application.use_case;

import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.share.kafka.event.CreateBookEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBookFeatureUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @Mock
    private EmbeddingBookProvider embeddingBookProvider;

    @InjectMocks
    private CreateBookFeatureUseCase createBookFeatureUseCase;

    @Captor
    private ArgumentCaptor<BookFeature> bookFeatureCaptor;

    private float[] createValidEmbedding() {
        return new float[384];
    }

    private CreateBookEvent createSampleEvent(UUID bookId) {
        return new CreateBookEvent(
                bookId,
                "Domain-Driven Design",
                "A comprehensive guide to software design and architecture using domain-driven design principles.",
                2014,
                "https://example.com/cover.jpg",
                "Eric Evans",
                List.of("Software Engineering", "Architecture")
        );
    }

    @Test
    void shouldCreateAndSaveBookFeatureSuccessfully() {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = createSampleEvent(bookId);
        float[] validEmbedding = createValidEmbedding();

        when(embeddingBookProvider.of(
                event.title(),
                event.author(),
                event.description(),
                event.genres()
        )).thenReturn(validEmbedding);

        createBookFeatureUseCase.execute(event);

        verify(bookFeatureRepository, times(1)).save(bookFeatureCaptor.capture());
        BookFeature savedFeature = bookFeatureCaptor.getValue();

        assertNotNull(savedFeature);
        assertEquals(bookId, savedFeature.getBookId());
        assertArrayEquals(validEmbedding, savedFeature.getEmbedding());
        assertEquals(1.0, savedFeature.getPopularityScore());
    }

    @Test
    void shouldThrowExceptionWhenEmbeddingProviderReturnsInvalidVectorLength() {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = createSampleEvent(bookId);
        float[] invalidEmbedding = new float[128];

        when(embeddingBookProvider.of(
                event.title(),
                event.author(),
                event.description(),
                event.genres()
        )).thenReturn(invalidEmbedding);

        assertThrows(IllegalArgumentException.class, () -> createBookFeatureUseCase.execute(event));

        verify(bookFeatureRepository, never()).save(any(BookFeature.class));
    }

    @Test
    void shouldThrowExceptionWhenEmbeddingProviderReturnsNull() {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = createSampleEvent(bookId);

        when(embeddingBookProvider.of(
                event.title(),
                event.author(),
                event.description(),
                event.genres()
        )).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> createBookFeatureUseCase.execute(event));

        verify(bookFeatureRepository, never()).save(any(BookFeature.class));
    }

    @Test
    void shouldThrowExceptionWhenEmbeddingProviderFails() {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = createSampleEvent(bookId);

        when(embeddingBookProvider.of(
                event.title(),
                event.author(),
                event.description(),
                event.genres()
        )).thenThrow(new RuntimeException("AI Model Timeout"));

        assertThrows(RuntimeException.class, () -> createBookFeatureUseCase.execute(event));

        verify(bookFeatureRepository, never()).save(any(BookFeature.class));
    }

    @Test
    void shouldThrowExceptionWhenRepositoryFailsToSave() {
        UUID bookId = UUID.randomUUID();
        CreateBookEvent event = createSampleEvent(bookId);
        float[] validEmbedding = createValidEmbedding();

        when(embeddingBookProvider.of(
                event.title(),
                event.author(),
                event.description(),
                event.genres()
        )).thenReturn(validEmbedding);

        doThrow(new RuntimeException("Database Connection Error"))
                .when(bookFeatureRepository).save(any(BookFeature.class));

        assertThrows(RuntimeException.class, () -> createBookFeatureUseCase.execute(event));
    }
}