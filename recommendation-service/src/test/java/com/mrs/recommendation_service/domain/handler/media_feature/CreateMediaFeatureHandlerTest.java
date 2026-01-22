package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMediaFeatureHandlerTest {

    @Mock
    private MediaFeatureRepository mediaFeatureRepository;

    @InjectMocks
    private CreateMediaFeatureHandler createMediaFeatureHandler;

    @Test
    @DisplayName("Should save media feature when valid")
    void shouldSaveMediaFeature_WhenValid() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("ACTION", "THRILLER");
        MediaFeature mediaFeature = new MediaFeature(mediaId, genres);

        // Act
        createMediaFeatureHandler.execute(mediaFeature);

        // Assert
        verify(mediaFeatureRepository, times(1)).save(mediaFeature);
    }

    @Test
    @DisplayName("Should save media feature with empty genres")
    void shouldSaveMediaFeature_WithEmptyGenres() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        MediaFeature mediaFeature = new MediaFeature(mediaId, List.of());

        // Act
        createMediaFeatureHandler.execute(mediaFeature);

        // Assert
        verify(mediaFeatureRepository, times(1)).save(mediaFeature);
    }
}
