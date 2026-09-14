package com.vellumhub.engagement_service.module.rating.domain.producer;

import com.vellumhub.engagement_service.module.rating.domain.model.Rating;
import com.vellumhub.engagement_service.module.rating.domain.port.EventProducer;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreatedRatingEventProducerTest {

    @Mock
    private EventProducer<String, CreatedRatingEvent> eventProducer;

    @InjectMocks
    private CreatedRatingEventProducer createdRatingEventProducer;

    @Test
    @DisplayName("Should publish a self-contained created-rating event")
    void shouldPublishCreatedRatingEvent() {
        UUID userId = UUID.randomUUID();
        UUID bookId = UUID.randomUUID();
        Rating rating = Rating.builder()
                .id(42L)
                .userId(userId)
                .bookId(bookId)
                .stars(5)
                .review("Excellent")
                .build();
        ArgumentCaptor<CreatedRatingEvent> eventCaptor = ArgumentCaptor.forClass(CreatedRatingEvent.class);

        createdRatingEventProducer.produce(rating);

        verify(eventProducer).send(
                org.mockito.ArgumentMatchers.eq(KafkaTopics.CREATED_RATING),
                org.mockito.ArgumentMatchers.eq(userId.toString()),
                eventCaptor.capture()
        );

        CreatedRatingEvent event = eventCaptor.getValue();
        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.ratingId()).isEqualTo(42L);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.bookId()).isEqualTo(bookId);
        assertThat(event.oldStars()).isNull();
        assertThat(event.newStars()).isEqualTo(5);
        assertThat(event.reviewChanged()).isFalse();
    }
}
