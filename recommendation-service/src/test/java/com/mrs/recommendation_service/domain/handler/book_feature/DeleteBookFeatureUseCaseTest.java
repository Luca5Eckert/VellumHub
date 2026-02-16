package com.mrs.recommendation_service.domain.handler.book_feature;

import com.mrs.recommendation_service.module.book_feature.domain.use_case.DeleteBookFeatureUseCase;
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
class DeleteBookFeatureUseCaseTest {

    @Mock
    private BookFeatureRepository bookFeatureRepository;

    @InjectMocks
    private DeleteBookFeatureUseCase deleteMediaFeatureHandler;

    @Test
    @DisplayName("Should delete media feature by ID")
    void shouldDeleteMediaFeature_ById() {
        // Arrange
        UUID mediaId = UUID.randomUUID();

        // Act
        deleteMediaFeatureHandler.execute(mediaId);

        // Assert
        verify(bookFeatureRepository, times(1)).deleteById(mediaId);
    }

    @Test
    @DisplayName("Should call repository deleteById even if media does not exist")
    void shouldCallDeleteById_EvenIfMediaDoesNotExist() {
        // Arrange
        UUID nonExistentMediaId = UUID.randomUUID();

        // Act
        deleteMediaFeatureHandler.execute(nonExistentMediaId);

        // Assert
        verify(bookFeatureRepository, times(1)).deleteById(nonExistentMediaId);
    }
}
