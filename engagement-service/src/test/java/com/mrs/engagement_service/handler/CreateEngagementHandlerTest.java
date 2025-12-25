package com.mrs.engagement_service.handler;

import com.mrs.engagement_service.event.InteractionEvent;
import com.mrs.engagement_service.model.Interaction;
import com.mrs.engagement_service.model.InteractionType;
import com.mrs.engagement_service.repository.EngagementRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateEngagementHandler Tests")
class CreateEngagementHandlerTest {

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private KafkaTemplate<String, InteractionEvent> kafka;

    @InjectMocks
    private CreateEngagementHandler createEngagementHandler;

    @Test
    @DisplayName("Should save interaction and publish Kafka event")
    void handler_WithValidInteraction_SavesAndPublishesEvent() {
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

        when(engagementRepository.save(any(Interaction.class))).thenReturn(interaction);

        createEngagementHandler.handler(interaction);

        verify(engagementRepository, times(1)).save(interaction);
        verify(kafka, times(1)).send(eq("engagement-created"), eq(userId.toString()), any(InteractionEvent.class));
    }

    @Test
    @DisplayName("Should throw exception when interaction is null")
    void handler_WithNullInteraction_ThrowsException() {
        assertThatThrownBy(() -> createEngagementHandler.handler(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Interaction can't be null");

        verifyNoInteractions(engagementRepository);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("Should publish event with correct interaction data")
    void handler_WithValidInteraction_PublishesEventWithCorrectData() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();
        
        Interaction interaction = new Interaction(
                userId,
                mediaId,
                InteractionType.WATCH,
                0.75,
                timestamp
        );
        interaction.setId(1L);

        when(engagementRepository.save(any(Interaction.class))).thenReturn(interaction);

        createEngagementHandler.handler(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka).send(eq("engagement-created"), eq(userId.toString()), eventCaptor.capture());

        InteractionEvent capturedEvent = eventCaptor.getValue();
        assertThat(capturedEvent.userId()).isEqualTo(userId);
        assertThat(capturedEvent.mediaId()).isEqualTo(mediaId);
        assertThat(capturedEvent.interactionType()).isEqualTo(InteractionType.WATCH);
        assertThat(capturedEvent.interactionValue()).isEqualTo(0.75);
        assertThat(capturedEvent.timestamp()).isEqualTo(timestamp);
    }

    @Test
    @DisplayName("Should use userId as Kafka key")
    void handler_WithValidInteraction_UsesUserIdAsKafkaKey() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        
        Interaction interaction = new Interaction(
                userId,
                mediaId,
                InteractionType.DISLIKE,
                -1.0,
                LocalDateTime.now()
        );
        interaction.setId(1L);

        when(engagementRepository.save(any(Interaction.class))).thenReturn(interaction);

        createEngagementHandler.handler(interaction);

        verify(kafka).send(eq("engagement-created"), eq(userId.toString()), any(InteractionEvent.class));
    }

    @Test
    @DisplayName("Should handle LIKE interaction type")
    void handler_WithLikeType_ProcessesCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        
        Interaction interaction = new Interaction(
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0,
                LocalDateTime.now()
        );
        interaction.setId(1L);

        when(engagementRepository.save(any(Interaction.class))).thenReturn(interaction);

        createEngagementHandler.handler(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka).send(anyString(), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue().interactionType()).isEqualTo(InteractionType.LIKE);
    }

    @Test
    @DisplayName("Should handle DISLIKE interaction type")
    void handler_WithDislikeType_ProcessesCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        
        Interaction interaction = new Interaction(
                userId,
                mediaId,
                InteractionType.DISLIKE,
                -1.0,
                LocalDateTime.now()
        );
        interaction.setId(1L);

        when(engagementRepository.save(any(Interaction.class))).thenReturn(interaction);

        createEngagementHandler.handler(interaction);

        ArgumentCaptor<InteractionEvent> eventCaptor = ArgumentCaptor.forClass(InteractionEvent.class);
        verify(kafka).send(anyString(), anyString(), eventCaptor.capture());

        assertThat(eventCaptor.getValue().interactionType()).isEqualTo(InteractionType.DISLIKE);
    }
}
