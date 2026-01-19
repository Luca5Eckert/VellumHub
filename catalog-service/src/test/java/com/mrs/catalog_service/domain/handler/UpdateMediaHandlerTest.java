package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.UpdateMediaRequest;
import com.mrs.catalog_service.domain.event.UpdateMediaEvent;
import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.MediaRepository;
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
class UpdateMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private EventProducer<String, UpdateMediaEvent> eventProducer;

    @InjectMocks
    private UpdateMediaHandler updateMediaHandler;

    @Test
    @DisplayName("Should update media and send event when genres are provided")
    void shouldUpdateMediaAndSendEvent_WhenGenresArePresent() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<Genre> newGenres = List.of(Genre.COMEDY, Genre.ACTION);

        UpdateMediaRequest request = new UpdateMediaRequest(
                "New Title",
                "New Desc",
                2024,
                "http://url.com",
                newGenres
        );


        Media existingMedia = Media.builder()
                .id(mediaId)
                .title("Old Title")
                .genres(List.of(Genre.COMEDY))
                .build();

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(existingMedia));

        // Act
        updateMediaHandler.execute(mediaId, request);

        // Assert
        ArgumentCaptor<Media> mediaCaptor = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(mediaCaptor.capture());

        Media savedMedia = mediaCaptor.getValue();
        assertEquals("New Title", savedMedia.getTitle());

        ArgumentCaptor<UpdateMediaEvent> eventCaptor = ArgumentCaptor.forClass(UpdateMediaEvent.class);
        verify(eventProducer, times(1))
                .send(eq("update-media"), eq(mediaId.toString()), eventCaptor.capture());

        assertEquals(mediaId.toString(), eventCaptor.getValue().mediaId());
        assertEquals(newGenres.stream().map(Object::toString).toList(), eventCaptor.getValue().genres());
    }

    @Test
    @DisplayName("Should update media but NOT send event when genres are null")
    void shouldUpdateMediaButNotSendEvent_WhenGenresAreNull() {
        // Arrange
        UUID mediaId = UUID.randomUUID();

        UpdateMediaRequest request = new UpdateMediaRequest(
                "New Title",
                "New Desc",
                2024,
                "http://url.com",
                null
        );

        Media existingMedia = Media.builder()
                .id(mediaId)
                .title("Old Title")
                .genres(List.of(Genre.COMEDY))
                .build();

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.of(existingMedia));

        // Act
        updateMediaHandler.execute(mediaId, request);

        // Assert
        verify(mediaRepository).save(any(Media.class));

        verify(eventProducer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw MediaNotFoundException when media does not exist")
    void shouldThrowException_WhenMediaNotFound() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        UpdateMediaRequest request = new UpdateMediaRequest("T", "D", 2022, "U", List.of(Genre.COMEDY));

        when(mediaRepository.findById(mediaId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(MediaNotFoundException.class, () ->
                updateMediaHandler.execute(mediaId, request)
        );

        verify(mediaRepository, never()).save(any());
        verify(eventProducer, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw NullPointerException when request is null")
    void shouldThrowException_WhenRequestIsNull() {
        // Arrange
        UUID mediaId = UUID.randomUUID();

        // Act & Assert
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                updateMediaHandler.execute(mediaId, null)
        );

        assertEquals("UpdateMediaRequest must not be null", exception.getMessage());
        verifyNoInteractions(mediaRepository);
        verifyNoInteractions(eventProducer);
    }

}