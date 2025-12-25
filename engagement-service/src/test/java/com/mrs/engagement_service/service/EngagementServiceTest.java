package com.mrs.engagement_service.service;

import com.mrs.engagement_service.dto.InteractionCreateRequest;
import com.mrs.engagement_service.handler.CreateEngagementHandler;
import com.mrs.engagement_service.model.Interaction;
import com.mrs.engagement_service.model.InteractionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EngagementService Tests")
class EngagementServiceTest {

    @Mock
    private CreateEngagementHandler createEngagementHandler;

    @InjectMocks
    private EngagementService engagementService;

    @Test
    @DisplayName("Should create interaction with correct data")
    void create_WithValidRequest_CreatesInteraction() {
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        InteractionCreateRequest request = new InteractionCreateRequest(
                userId,
                mediaId,
                InteractionType.LIKE,
                1.0
        );

        doNothing().when(createEngagementHandler).handler(any(Interaction.class));

        engagementService.create(request);

        ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
        verify(createEngagementHandler, times(1)).handler(interactionCaptor.capture());

        Interaction capturedInteraction = interactionCaptor.getValue();
        assertThat(capturedInteraction.getUserId()).isEqualTo(userId);
        assertThat(capturedInteraction.getMediaId()).isEqualTo(mediaId);
        assertThat(capturedInteraction.getType()).isEqualTo(InteractionType.LIKE);
        assertThat(capturedInteraction.getInteractionValue()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should set timestamp when creating interaction")
    void create_SetsTimestamp() {
        InteractionCreateRequest request = new InteractionCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.WATCH,
                0.5
        );

        doNothing().when(createEngagementHandler).handler(any(Interaction.class));

        engagementService.create(request);

        ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
        verify(createEngagementHandler).handler(interactionCaptor.capture());

        assertThat(interactionCaptor.getValue().getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Should handle DISLIKE interaction type")
    void create_WithDislikeType_CreatesInteraction() {
        InteractionCreateRequest request = new InteractionCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.DISLIKE,
                -1.0
        );

        doNothing().when(createEngagementHandler).handler(any(Interaction.class));

        engagementService.create(request);

        ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
        verify(createEngagementHandler).handler(interactionCaptor.capture());

        assertThat(interactionCaptor.getValue().getType()).isEqualTo(InteractionType.DISLIKE);
    }

    @Test
    @DisplayName("Should propagate exception from handler")
    void create_WhenHandlerThrows_PropagatesException() {
        InteractionCreateRequest request = new InteractionCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.LIKE,
                1.0
        );

        doThrow(new RuntimeException("Database error"))
                .when(createEngagementHandler).handler(any(Interaction.class));

        assertThatThrownBy(() -> engagementService.create(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Database error");
    }

    @Test
    @DisplayName("Should handle different interaction values")
    void create_WithDifferentInteractionValues_CreatesCorrectly() {
        InteractionCreateRequest request = new InteractionCreateRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                InteractionType.WATCH,
                0.75
        );

        doNothing().when(createEngagementHandler).handler(any(Interaction.class));

        engagementService.create(request);

        ArgumentCaptor<Interaction> interactionCaptor = ArgumentCaptor.forClass(Interaction.class);
        verify(createEngagementHandler).handler(interactionCaptor.capture());

        assertThat(interactionCaptor.getValue().getInteractionValue()).isEqualTo(0.75);
    }
}
