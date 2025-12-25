package com.mrs.catalog_service.handler;

import com.mrs.catalog_service.event.DeleteMediaEvent;
import com.mrs.catalog_service.repository.MediaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeleteMediaHandler Tests")
class DeleteMediaHandlerTest {

    @Mock
    private MediaRepository mediaRepository;

    @Mock
    private KafkaTemplate<String, DeleteMediaEvent> kafka;

    @InjectMocks
    private DeleteMediaHandler deleteMediaHandler;

    @Test
    @DisplayName("Should delete media and publish Kafka event when media exists")
    void execute_WithExistingMedia_DeletesAndPublishesEvent() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(true);

        deleteMediaHandler.execute(mediaId);

        verify(mediaRepository, times(1)).existsById(mediaId);
        verify(mediaRepository, times(1)).deleteById(mediaId);
        verify(kafka, times(1)).send(eq("delete-media"), eq(mediaId.toString()), any(DeleteMediaEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when media does not exist")
    void execute_WithNonExistingMedia_ThrowsException() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(false);

        assertThatThrownBy(() -> deleteMediaHandler.execute(mediaId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Media not exist");

        verify(mediaRepository, never()).deleteById(any());
        verify(kafka, never()).send(anyString(), anyString(), any(DeleteMediaEvent.class));
    }

    @Test
    @DisplayName("Should publish event with correct media ID")
    void execute_WithValidMedia_PublishesEventWithCorrectId() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(true);

        deleteMediaHandler.execute(mediaId);

        ArgumentCaptor<DeleteMediaEvent> eventCaptor = ArgumentCaptor.forClass(DeleteMediaEvent.class);
        verify(kafka).send(eq("delete-media"), eq(mediaId.toString()), eventCaptor.capture());

        DeleteMediaEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.mediaId()).isEqualTo(mediaId);
    }

    @Test
    @DisplayName("Should use media ID as Kafka key")
    void execute_WithValidMedia_UsesMediaIdAsKafkaKey() {
        UUID mediaId = UUID.randomUUID();
        when(mediaRepository.existsById(mediaId)).thenReturn(true);

        deleteMediaHandler.execute(mediaId);

        verify(kafka).send(eq("delete-media"), eq(mediaId.toString()), any(DeleteMediaEvent.class));
    }
}
