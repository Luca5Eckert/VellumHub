package com.vellumhub.catalog_service.module.book_progress.infrastructure.producer;

import com.vellumhub.catalog_service.module.book_progress.domain.port.BookProgressEventProducer;
import com.vellumhub.catalog_service.share.metrics.VellumHubMetrics;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class KafkaBookProgressEventProducer<K, V> implements BookProgressEventProducer<K, V> {

    private final KafkaTemplate<K, V> kafkaTemplate;
    private final VellumHubMetrics metrics;

    public KafkaBookProgressEventProducer(KafkaTemplate<K, V> kafkaTemplate, VellumHubMetrics metrics) {
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
    }

    @Override
    public void send(String topic, K key, V value) {
        log.debug("Attempting to send event to Kafka — topic: {}, key: {}", topic, key);

        CompletableFuture<SendResult<K, V>> future = kafkaTemplate.send(topic, key, value);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                metrics.recordKafkaPublishFailed(topic, value);
                log.error(
                        "Failed to send event to Kafka — topic: {}, key: {}, reason: {}",
                        topic, key, ex.getMessage(), ex
                );
                handleSendFailure(topic, key, value, ex);
            } else {
                RecordMetadata metadata = result.getRecordMetadata();
                metrics.recordKafkaPublished(topic, value);
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
    private void handleSendFailure(String topic, K key, V value, Throwable ex) {
        log.warn(
                "Event dropped — consider implementing a Dead Letter Queue. topic: {}, key: {}",
                topic, key
        );
    }
}
