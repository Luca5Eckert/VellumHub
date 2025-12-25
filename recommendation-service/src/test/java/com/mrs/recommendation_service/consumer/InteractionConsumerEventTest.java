package com.mrs.recommendation_service.consumer;

import com.mrs.recommendation_service.event.InteractionEvent;
import com.mrs.recommendation_service.model.InteractionType;
import com.mrs.recommendation_service.service.UserProfileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InteractionConsumerEvent Tests")
class InteractionConsumerEventTest {

    @Mock
    private UserProfileService userProfileService;

    @InjectMocks
    private InteractionConsumerEvent interactionConsumerEvent;

    @Test
    @DisplayName("Should process interaction event successfully")
    void consume_WithValidEvent_ProcessesSuccessfully() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        
        InteractionEvent event = new InteractionEvent(
                1L,
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0,
                LocalDateTime.now()
        );

        doNothing().when(userProfileService).update(any(InteractionEvent.class));

        interactionConsumerEvent.consume(event);

        verify(userProfileService, times(1)).update(event);
    }

    @Test
    @DisplayName("Should process DISLIKE interaction type")
    void consume_WithDislikeType_ProcessesSuccessfully() {
        InteractionEvent event = new InteractionEvent(
                1L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.DISLIKE,
                -1.0,
                LocalDateTime.now()
        );

        doNothing().when(userProfileService).update(any(InteractionEvent.class));

        interactionConsumerEvent.consume(event);

        verify(userProfileService, times(1)).update(event);
    }

    @Test
    @DisplayName("Should process WATCH interaction type")
    void consume_WithWatchType_ProcessesSuccessfully() {
        InteractionEvent event = new InteractionEvent(
                1L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.WATCH,
                0.75,
                LocalDateTime.now()
        );

        doNothing().when(userProfileService).update(any(InteractionEvent.class));

        interactionConsumerEvent.consume(event);

        verify(userProfileService, times(1)).update(event);
    }

    @Test
    @DisplayName("Should propagate RuntimeException from service")
    void consume_WhenServiceThrowsRuntimeException_PropagatesException() {
        InteractionEvent event = new InteractionEvent(
                1L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.LIKE,
                1.0,
                LocalDateTime.now()
        );

        doThrow(new RuntimeException("Media not found"))
                .when(userProfileService).update(any(InteractionEvent.class));

        assertThatThrownBy(() -> interactionConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Media not found");
    }

    @Test
    @DisplayName("Should wrap generic Exception in RuntimeException")
    void consume_WhenServiceThrowsException_WrapsInRuntimeException() {
        InteractionEvent event = new InteractionEvent(
                1L,
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.LIKE,
                1.0,
                LocalDateTime.now()
        );

        doThrow(new RuntimeException("Database connection failed"))
                .when(userProfileService).update(any(InteractionEvent.class));

        assertThatThrownBy(() -> interactionConsumerEvent.consume(event))
                .isInstanceOf(RuntimeException.class);
    }
}
