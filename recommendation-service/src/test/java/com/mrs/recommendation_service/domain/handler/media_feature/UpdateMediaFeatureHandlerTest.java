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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateMediaFeatureHandlerTest {

    @Mock
    private MediaFeatureRepository mediaFeatureRepository;

    @InjectMocks
    private UpdateMediaFeatureHandler updateMediaFeatureHandler;

    @Test
    @DisplayName("Should update media feature when it exists")
    void shouldUpdateMediaFeature_WhenItExists() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> oldGenres = List.of("ACTION");
        List<String> newGenres = List.of("ACTION", "COMEDY", "THRILLER");

        MediaFeature existingMediaFeature = new MediaFeature(mediaId, oldGenres);
        UpdateMediaFeatureCommand command = new UpdateMediaFeatureCommand(mediaId, newGenres);

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(existingMediaFeature));

        // Act
        updateMediaFeatureHandler.execute(command);

        // Assert
        ArgumentCaptor<MediaFeature> mediaFeatureCaptor = ArgumentCaptor.forClass(MediaFeature.class);
        verify(mediaFeatureRepository, times(1)).save(mediaFeatureCaptor.capture());

        MediaFeature savedMediaFeature = mediaFeatureCaptor.getValue();
        assertEquals(newGenres, savedMediaFeature.getGenres());
    }

    @Test
    @DisplayName("Should throw MediaFeatureNotFoundException when media feature does not exist")
    void shouldThrowException_WhenMediaFeatureDoesNotExist() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> genres = List.of("ACTION", "COMEDY");
        UpdateMediaFeatureCommand command = new UpdateMediaFeatureCommand(mediaId, genres);

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(MediaFeatureNotFoundException.class, () ->
                updateMediaFeatureHandler.execute(command)
        );

        verify(mediaFeatureRepository, times(1)).findById(mediaId);
        verify(mediaFeatureRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update media feature with empty genres list")
    void shouldUpdateMediaFeature_WithEmptyGenresList() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<String> oldGenres = List.of("ACTION", "COMEDY");
        List<String> newGenres = List.of();

        MediaFeature existingMediaFeature = new MediaFeature(mediaId, oldGenres);
        UpdateMediaFeatureCommand command = new UpdateMediaFeatureCommand(mediaId, newGenres);

        when(mediaFeatureRepository.findById(mediaId)).thenReturn(Optional.of(existingMediaFeature));

        // Act
        updateMediaFeatureHandler.execute(command);

        // Assert
        ArgumentCaptor<MediaFeature> mediaFeatureCaptor = ArgumentCaptor.forClass(MediaFeature.class);
        verify(mediaFeatureRepository, times(1)).save(mediaFeatureCaptor.capture());

        MediaFeature savedMediaFeature = mediaFeatureCaptor.getValue();
        assertTrue(savedMediaFeature.getGenres().isEmpty());
    }
}
