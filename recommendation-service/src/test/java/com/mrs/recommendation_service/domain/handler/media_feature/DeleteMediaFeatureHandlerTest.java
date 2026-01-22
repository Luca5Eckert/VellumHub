package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMediaFeatureHandlerTest {

    @Mock
    private MediaFeatureRepository mediaFeatureRepository;

    @InjectMocks
    private DeleteMediaFeatureHandler deleteMediaFeatureHandler;

    @Test
    @DisplayName("Should delete media feature by ID")
    void shouldDeleteMediaFeature_ById() {
        // Arrange
        UUID mediaId = UUID.randomUUID();

        // Act
        deleteMediaFeatureHandler.execute(mediaId);

        // Assert
        verify(mediaFeatureRepository, times(1)).deleteById(mediaId);
    }

    @Test
    @DisplayName("Should call delete even if media feature does not exist")
    void shouldCallDelete_EvenIfNotExists() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        doNothing().when(mediaFeatureRepository).deleteById(mediaId);

        // Act
        deleteMediaFeatureHandler.execute(mediaId);

        // Assert
        verify(mediaFeatureRepository, times(1)).deleteById(mediaId);
    }
}
