package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.event;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaReadingSessionEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    @DisplayName("Should send event using kafka template")
    void shouldSendEventUsingKafkaTemplate() {
        KafkaReadingSessionEventPublisher<String, Object> publisher = new KafkaReadingSessionEventPublisher<>(kafkaTemplate);
        SendResult<String, Object> sendResult = mock(SendResult.class);
        RecordMetadata recordMetadata = mock(RecordMetadata.class);

        when(sendResult.getRecordMetadata()).thenReturn(recordMetadata);
        when(recordMetadata.topic()).thenReturn("reading-session-started");
        when(recordMetadata.partition()).thenReturn(0);
        when(recordMetadata.offset()).thenReturn(10L);
        when(kafkaTemplate.send("reading-session-started", "book", "payload"))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        publisher.publish("reading-session-started", "book", "payload");

        verify(kafkaTemplate).send("reading-session-started", "book", "payload");
    }

    @Test
    @DisplayName("Should swallow asynchronous send failures without throwing")
    void shouldSwallowAsyncFailure() {
        KafkaReadingSessionEventPublisher<String, Object> publisher = new KafkaReadingSessionEventPublisher<>(kafkaTemplate);

        when(kafkaTemplate.send("reading-session-started", "book", "payload"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        assertThatCode(() -> publisher.publish("reading-session-started", "book", "payload"))
                .doesNotThrowAnyException();
    }
}
