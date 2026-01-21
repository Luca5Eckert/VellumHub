package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.DeleteMediaEvent;
import com.mrs.catalog_service.domain.exception.MediaNotExistException;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeleteMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private KafkaTemplate<String, DeleteMediaEvent> kafka;

    @InjectMocks
    private DeleteMediaHandler deleteMediaHandler;

    @Test
    @DisplayName("Should delete media and send event when media exists")
    void shouldDeleteMediaAndSendEvent_WhenMediaExists() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(true);

        // Act
        deleteMediaHandler.execute(mediaId);

        // Assert
        verify(mediaRepository, times(1)).deleteById(mediaId);

        ArgumentCaptor<DeleteMediaEvent> eventCaptor = ArgumentCaptor.forClass(DeleteMediaEvent.class);
        verify(kafka, times(1))
                .send(eq("delete-media"), eq(mediaId.toString()), eventCaptor.capture());

        DeleteMediaEvent capturedEvent = eventCaptor.getValue();
        assertEquals(mediaId, capturedEvent.mediaId());
    }

    @Test
    @DisplayName("Should throw MediaNotExistException when media does not exist")
    void shouldThrowException_WhenMediaDoesNotExist() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(false);

        // Act & Assert
        assertThrows(MediaNotExistException.class, () ->
                deleteMediaHandler.execute(mediaId)
        );

        verify(mediaRepository, never()).deleteById(any());
        verify(kafka, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should verify exception message contains media ID")
    void shouldThrowExceptionWithCorrectMessage_WhenMediaDoesNotExist() {
        // Arrange
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(false);

        // Act & Assert
        MediaNotExistException exception = assertThrows(MediaNotExistException.class, () ->
                deleteMediaHandler.execute(mediaId)
        );

        assertTrue(exception.getMessage().contains(mediaId.toString()));
        verify(mediaRepository, never()).deleteById(any());
    }
}
