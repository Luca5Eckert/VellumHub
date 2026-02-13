package com.mrs.engagement_service.domain.handler;

import com.mrs.engagement_service.module.rating.domain.event.RatingEvent;
import com.mrs.engagement_service.module.rating.domain.exception.InvalidRatingException;
import com.mrs.engagement_service.module.rating.domain.handler.CreateRatingHandler;
import com.mrs.engagement_service.module.rating.domain.model.Rating;
import com.mrs.engagement_service.module.rating.domain.port.EngagementRepository;
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
class CreateRatingHandlerTest {

    @Mock
    private EngagementRepository engagementRepository;

    @Mock
    private KafkaTemplate<String, RatingEvent> kafka;

    @InjectMocks
    private CreateRatingHandler createRatingHandler;

    @Test
    @DisplayName("Should save rating and send event when rating is valid")
    void shouldSaveRatingAndSendEvent_WhenRatingIsValid() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();

        Rating rating = new Rating(
                1L,
                userId,
                mediaId,
                4,
                "Great movie!",
                timestamp
        );

        // Act
        createRatingHandler.handler(rating);

        // Assert
        verify(engagementRepository, times(1)).save(rating);

        ArgumentCaptor<RatingEvent> eventCaptor = ArgumentCaptor.forClass(RatingEvent.class);
        verify(kafka, times(1)).send(
                eq("engagement-created"),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        RatingEvent capturedEvent = eventCaptor.getValue();
        assertEquals(1L, capturedEvent.id());
        assertEquals(userId, capturedEvent.userId());
        assertEquals(mediaId, capturedEvent.mediaId());
        assertEquals(4, capturedEvent.stars());
        assertEquals("Great movie!", capturedEvent.review());
        assertEquals(timestamp, capturedEvent.timestamp());
    }

    @Test
    @DisplayName("Should throw InvalidRatingException when rating is null")
    void shouldThrowException_WhenRatingIsNull() {
        // Act & Assert
        InvalidRatingException exception = assertThrows(InvalidRatingException.class, () ->
                createRatingHandler.handler(null)
        );

        assertEquals("Rating cannot be null", exception.getMessage());
        verifyNoInteractions(engagementRepository);
        verifyNoInteractions(kafka);
    }

    @Test
    @DisplayName("Should save rating with 5 stars and send event")
    void shouldSaveFiveStarRating_AndSendEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();

        Rating rating = new Rating(
                2L,
                userId,
                mediaId,
                5,
                "Perfect!",
                timestamp
        );

        // Act
        createRatingHandler.handler(rating);

        // Assert
        verify(engagementRepository, times(1)).save(rating);

        ArgumentCaptor<RatingEvent> eventCaptor = ArgumentCaptor.forClass(RatingEvent.class);
        verify(kafka, times(1)).send(
                eq("engagement-created"),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        RatingEvent capturedEvent = eventCaptor.getValue();
        assertEquals(5, capturedEvent.stars());
    }

    @Test
    @DisplayName("Should save rating with 0 stars and send event")
    void shouldSaveZeroStarRating_AndSendEvent() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        LocalDateTime timestamp = LocalDateTime.now();

        Rating rating = new Rating(
                3L,
                userId,
                mediaId,
                0,
                "Terrible",
                timestamp
        );

        // Act
        createRatingHandler.handler(rating);

        // Assert
        verify(engagementRepository, times(1)).save(rating);

        ArgumentCaptor<RatingEvent> eventCaptor = ArgumentCaptor.forClass(RatingEvent.class);
        verify(kafka, times(1)).send(
                eq("engagement-created"),
                eq(userId.toString()),
                eventCaptor.capture()
        );

        RatingEvent capturedEvent = eventCaptor.getValue();
        assertEquals(0, capturedEvent.stars());
        assertEquals("Terrible", capturedEvent.review());
    }
}
