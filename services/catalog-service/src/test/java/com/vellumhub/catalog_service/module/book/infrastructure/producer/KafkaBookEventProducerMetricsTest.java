package com.vellumhub.catalog_service.module.book.infrastructure.producer;

import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaBookEventProducerMetricsTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private SimpleMeterRegistry meterRegistry;
    private KafkaBookEventProducer<String, Object> producer;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        producer = new KafkaBookEventProducer<>(kafkaTemplate, new VellumHubMetrics(meterRegistry));
    }

    @Test
    @DisplayName("Should count successful Kafka publications")
    void shouldCountSuccessfulPublications() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);

        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("created-book");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(1L);
        when(kafkaTemplate.send("created-book", "book-id", "payload"))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.send("created-book", "book-id", "payload");

        assertThat(meterRegistry.get("vellumhub.kafka.events.published")
                .tag("topic", "created-book")
                .tag("event_type", "String")
                .counter()
                .count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should count failed Kafka publications")
    void shouldCountFailedPublications() {
        when(kafkaTemplate.send("created-book", "book-id", "payload"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));

        producer.send("created-book", "book-id", "payload");

        assertThat(meterRegistry.get("vellumhub.kafka.events.publish.failed")
                .tag("topic", "created-book")
                .tag("event_type", "String")
                .counter()
                .count()).isEqualTo(1.0);
    }
}
