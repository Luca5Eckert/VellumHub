package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.CreateMediaEvent;
import com.mrs.catalog_service.domain.exception.InvalidMediaException;
import com.mrs.catalog_service.domain.model.Genre;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.model.MediaType;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private EventProducer<String, CreateMediaEvent> eventProducer;

    @InjectMocks
    private CreateMediaHandler createMediaHandler;

    @Test
    @DisplayName("Should save media and send event when media is valid")
    void shouldSaveMediaAndSendEvent_WhenMediaIsValid() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        List<Genre> genres = List.of(Genre.ACTION, Genre.THRILLER);

        Media media = Media.builder()
                .id(mediaId)
                .title("Test Movie")
                .description("Test Description")
                .releaseYear(2024)
                .mediaType(MediaType.MOVIE)
                .genres(genres)
                .build();

        // Act
        createMediaHandler.handler(media);

        // Assert
        verify(mediaRepository, times(1)).save(media);

        ArgumentCaptor<CreateMediaEvent> eventCaptor = ArgumentCaptor.forClass(CreateMediaEvent.class);
        verify(eventProducer, times(1))
                .send(eq("create-media"), eq(mediaId.toString()), eventCaptor.capture());

        CreateMediaEvent capturedEvent = eventCaptor.getValue();
        assertEquals(mediaId, capturedEvent.mediaId());
        assertEquals(2, capturedEvent.genres().size());
        assertTrue(capturedEvent.genres().contains("ACTION"));
        assertTrue(capturedEvent.genres().contains("THRILLER"));
    }

    @Test
    @DisplayName("Should throw InvalidMediaException when media is null")
    void shouldThrowException_WhenMediaIsNull() {
        // Act & Assert
        assertThrows(InvalidMediaException.class, () ->
                createMediaHandler.handler(null)
        );

        verifyNoInteractions(mediaRepository);
        verifyNoInteractions(eventProducer);
    }

    @Test
    @DisplayName("Should save media with empty genres list")
    void shouldSaveMedia_WhenGenresAreEmpty() {
        // Arrange
        UUID mediaId = UUID.randomUUID();

        Media media = Media.builder()
                .id(mediaId)
                .title("Test Movie")
                .releaseYear(2024)
                .mediaType(MediaType.MOVIE)
                .genres(List.of())
                .build();

        // Act
        createMediaHandler.handler(media);

        // Assert
        verify(mediaRepository, times(1)).save(media);

        ArgumentCaptor<CreateMediaEvent> eventCaptor = ArgumentCaptor.forClass(CreateMediaEvent.class);
        verify(eventProducer, times(1))
                .send(eq("create-media"), eq(mediaId.toString()), eventCaptor.capture());

        CreateMediaEvent capturedEvent = eventCaptor.getValue();
        assertEquals(mediaId, capturedEvent.mediaId());
        assertTrue(capturedEvent.genres().isEmpty());
    }
}
