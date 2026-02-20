package com.mrs.recommendation_service.module.book_feature.domain.use_case;

import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateBookFeatureUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @InjectMocks
    private CreateBookFeatureUseCase createBookFeatureUseCase;

    @Test
    @DisplayName("Should save book feature successfully")
    void shouldSaveBookFeatureSuccessfully() {
        // Arrange
        BookFeature bookFeature = new BookFeature(UUID.randomUUID(), new float[]{1.0f, 0.0f, 0.5f});

        // Act
        createBookFeatureUseCase.execute(bookFeature);

        // Assert
        verify(bookFeatureRepository, times(1)).save(bookFeature);
    }

    @Test
    @DisplayName("Should propagate exception when repository fails to save")
    void shouldPropagateExceptionWhenRepositoryFails() {
        // Arrange
        BookFeature bookFeature = new BookFeature(UUID.randomUUID(), new float[]{1.0f});
        doThrow(new RuntimeException("Database error")).when(bookFeatureRepository).save(bookFeature);

        // Act & Assert
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> createBookFeatureUseCase.execute(bookFeature));

        verify(bookFeatureRepository, times(1)).save(bookFeature);
    }
}
