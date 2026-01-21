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
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0,
                timestamp
        );
        interaction.setId(1L);

        // Act
        createEngagementHandler.handler(interaction);

        // Assert
        verify(engagementRepository, times(1)).save(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka, times(1))
                .send(eq("engagement-created"), eq(userId.toString()), eventCaptor.capture());

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
        assertThrows(InvalidInteractionException.class, () ->
                createEngagementHandler.handler(null)
        );

        verifyNoInteractions(engagementRepository);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("Should save interaction with WATCH type")
    void shouldSaveInteraction_WithWatchType() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();
        
        Interaction interaction = new Interaction(
                userId,
                mediaId,
                InteractionType.WATCH,
                0.8,
                timestamp
        );
        interaction.setId(2L);

        // Act
        createEngagementHandler.handler(interaction);

        // Assert
        verify(engagementRepository, times(1)).save(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka, times(1))
                .send(eq("engagement-created"), eq(userId.toString()), eventCaptor.capture());

        InteractionEvent capturedEvent = eventCaptor.getValue();
        assertEquals(InteractionType.WATCH, capturedEvent.interactionType());
        assertEquals(0.8, capturedEvent.interactionValue());
    }

    @Test
    @DisplayName("Should save interaction with DISLIKE type")
    void shouldSaveInteraction_WithDislikeType() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();
        
        Interaction interaction = new Interaction(
                userId,
                mediaId,
                InteractionType.DISLIKE,
                -1.0,
                timestamp
        );
        interaction.setId(3L);

        // Act
        createEngagementHandler.handler(interaction);

        // Assert
        verify(engagementRepository, times(1)).save(interaction);
        verify(kafka, times(1)).send(any(), any(), any());
    }
}
