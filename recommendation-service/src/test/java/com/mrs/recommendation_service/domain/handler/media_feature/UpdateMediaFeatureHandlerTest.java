package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.domain.command.UpdateMediaFeatureCommand;
import com.mrs.recommendation_service.domain.exception.media_feature.MediaFeatureNotFoundException;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMediaFeatureHandlerTest {

    @Mock
    private MediaFeatureRepository mediaFeatureRepository;

    @InjectMocks
    private UpdateMediaFeatureHandler updateMediaFeatureHandler;

    @Test
    @DisplayName("Should update media feature when it exists")
    void shouldUpdateMediaFeature_WhenExists() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> oldGenres = List.of("ACTION");
        List<String> newGenres = List.of("ACTION", "THRILLER", "COMEDY");

        MediaFeature mediaFeature = new MediaFeature(mediaId, oldGenres);
        UpdateMediaFeatureCommand command = new UpdateMediaFeatureCommand(mediaId, newGenres);

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));

        // Act
        updateMediaFeatureHandler.execute(command);

        // Assert
        ArgumentCaptor<MediaFeature> captor = ArgumentCaptor.forClass(MediaFeature.class);
        verify(mediaFeatureRepository, times(1)).save(captor.capture());

        MediaFeature savedMediaFeature = captor.getValue();
        assertEquals(newGenres, savedMediaFeature.getGenres());
        assertEquals(mediaId, savedMediaFeature.getMediaId());
    }

    @Test
    @DisplayName("Should throw MediaFeatureNotFoundException when media feature does not exist")
    void shouldThrowException_WhenMediaFeatureNotFound() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("ACTION");
        UpdateMediaFeatureCommand command = new UpdateMediaFeatureCommand(mediaId, genres);

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(MediaFeatureNotFoundException.class, () ->
                updateMediaFeatureHandler.execute(command)
        );

        verify(mediaFeatureRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update media feature with empty genres list")
    void shouldUpdateMediaFeature_WithEmptyGenres() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> oldGenres = List.of("ACTION", "THRILLER");
        List<String> newGenres = List.of();

        MediaFeature mediaFeature = new MediaFeature(mediaId, oldGenres);
        UpdateMediaFeatureCommand command = new UpdateMediaFeatureCommand(mediaId, newGenres);

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(mediaFeature));

        // Act
        updateMediaFeatureHandler.execute(command);

        // Assert
        ArgumentCaptor<MediaFeature> captor = ArgumentCaptor.forClass(MediaFeature.class);
        verify(mediaFeatureRepository, times(1)).save(captor.capture());

        MediaFeature savedMediaFeature = captor.getValue();
        assertTrue(savedMediaFeature.getGenres().isEmpty());
    }
}
