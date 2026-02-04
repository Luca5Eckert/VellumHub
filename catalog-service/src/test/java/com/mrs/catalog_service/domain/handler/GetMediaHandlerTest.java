package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.model.MediaType;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @InjectMocks
    private GetMediaHandler getMediaHandler;

    @Test
    @DisplayName("Should return media when it exists")
    void shouldReturnMedia_WhenMediaExists() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        Media expectedMedia = Media.builder()
                .id(mediaId)
                .title("Test Movie")
                .description("A test movie")
                .releaseYear(2024)
                .mediaType(MediaType.MOVIE)
                .genres(List.of(Genre.ACTION, Genre.COMEDY))
                .build();

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(expectedMedia));

        // Act
        Media result = getMediaHandler.execute(mediaId);

        // Assert
        assertNotNull(result);
        assertEquals(expectedMedia, result);
        assertEquals(mediaId, result.getId());
        assertEquals("Test Movie", result.getTitle());

        verify(mediaRepository, times(1)).findById(mediaId);
    }

    @Test
    @DisplayName("Should throw MediaNotFoundException when media does not exist")
    void shouldThrowException_WhenMediaDoesNotExist() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.findById(mediaId)).thenReturn(Optional.empty());

        // Act & Assert
        MediaNotFoundException exception = assertThrows(MediaNotFoundException.class, () ->
                getMediaHandler.execute(mediaId)
        );

        assertTrue(exception.getMessage().contains(mediaId.toString()));
        verify(mediaRepository, times(1)).findById(mediaId);
    }
}
