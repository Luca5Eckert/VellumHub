package com.mrs.engagement_service.domain.handler;

import com.mrs.engagement_service.domain.event.InteractionEvent;
import com.mrs.engagement_service.domain.exception.InvalidInteractionException;
import com.mrs.engagement_service.domain.model.Interaction;
import com.mrs.engagement_service.domain.model.InteractionType;
import com.mrs.engagement_service.domain.port.EngagementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateEngagementHandlerTest {

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private KafkaTemplate<String, InteractionEvent> kafka;

    @InjectMocks
    private CreateEngagementHandler createEngagementHandler;

    @Test
    @DisplayName("Should save interaction and send event when interaction is valid")
    void shouldSaveInteractionAndSendEvent_WhenInteractionIsValid() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();

        Interaction interaction = new Interaction(
                1L,
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0,
                timestamp
        );

        // Act
        createEngagementHandler.handler(interaction);

        // Assert
        verify(engagementRepository, times(1)).save(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka, times(1)).send(
                eq("engagement-created"),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        InteractionEvent capturedEvent = eventCaptor.getValue();
        assertEquals(1L, capturedEvent.id());
        assertEquals(userId, capturedEvent.userId());
        assertEquals(mediaId, capturedEvent.mediaId());
        assertEquals(InteractionType.LIKE, capturedEvent.interactionType());
        assertEquals(1.0, capturedEvent.interactionValue());
        assertEquals(timestamp, capturedEvent.timestamp());
    }

    @Test
    @DisplayName("Should throw InvalidInteractionException when interaction is null")
    void shouldThrowException_WhenInteractionIsNull() {
        // Act & Assert
        InvalidInteractionException exception = assertThrows(InvalidInteractionException.class, () ->
                createEngagementHandler.handler(null)
        );

        assertEquals("Interaction cannot be null", exception.getMessage());
        verifyNoInteractions(engagementRepository);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("Should save DISLIKE interaction and send event")
    void shouldSaveDislikeInteraction_AndSendEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();

        Interaction interaction = new Interaction(
                2L,
                userId,
                mediaId,
                InteractionType.DISLIKE,
                -1.0,
                timestamp
        );

        // Act
        createEngagementHandler.handler(interaction);

        // Assert
        verify(engagementRepository, times(1)).save(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka, times(1)).send(
                eq("engagement-created"),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        InteractionEvent capturedEvent = eventCaptor.getValue();
        assertEquals(InteractionType.DISLIKE, capturedEvent.interactionType());
    }

    @Test
    @DisplayName("Should save WATCH interaction and send event")
    void shouldSaveWatchInteraction_AndSendEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();

        Interaction interaction = new Interaction(
                3L,
                userId,
                mediaId,
                InteractionType.WATCH,
                0.5,
                timestamp
        );

        // Act
        createEngagementHandler.handler(interaction);

        // Assert
        verify(engagementRepository, times(1)).save(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka, times(1)).send(
                eq("engagement-created"),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        InteractionEvent capturedEvent = eventCaptor.getValue();
        assertEquals(InteractionType.WATCH, capturedEvent.interactionType());
        assertEquals(0.5, capturedEvent.interactionValue());
    }
}
