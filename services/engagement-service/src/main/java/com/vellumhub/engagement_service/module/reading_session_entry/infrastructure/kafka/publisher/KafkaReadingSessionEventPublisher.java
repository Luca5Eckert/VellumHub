package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.publisher;

import com.vellumhub.engagement_service.module.reading_session_entry.domain.port.ReadingSessionEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class KafkaReadingSessionEventPublisher<K, E> implements ReadingSessionEventPublisher<K, E> {

    private final KafkaTemplate<K, E> kafkaTemplate;

    public KafkaReadingSessionEventPublisher(KafkaTemplate<K, E> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String topic, K key, E event) {
        log.debug("Attempting to send event to Kafka — topic: {}, key: {}", topic, key);

        CompletableFuture<SendResult<K, E>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                        "Failed to send event to Kafka — topic: {}, key: {}, reason: {}",
                        topic, key, ex.getMessage(), ex
                );
                handleSendFailure(topic, key, event, ex);
            } else {
                RecordMetadata metadata = result.getRecordMetadata();
                log.info(
                        "Event successfully sent to Kafka — topic: {}, partition: {}, offset: {}, key: {}",
                        metadata.topic(), metadata.partition(), metadata.offset(), key
                );
            }
        });
    }

    /**
     * Hook for failure handling strategy.
     * Can be extended to support DLQ, retry queues, or alerting.
     */
    private void handleSendFailure(String topic, K key, E event, Throwable ex) {
        log.warn(
                "Event dropped — consider implementing a Dead Letter Queue. topic: {}, key: {}",
                topic, key
        );
    }
}
