package com.vellumhub.catalog_service.module.book_progress.infrastructure.producer;

import com.vellumhub.catalog_service.module.book.domain.port.BookEventProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class KafkaBookProgressEventProducer<K, V> implements BookEventProducer<K, V> {

    private final KafkaTemplate<K, V> kafkaTemplate;

    public KafkaBookProgressEventProducer(KafkaTemplate<K, V> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, K key, V value) {
        log.debug("Attempting to send event to Kafka — topic: {}, key: {}", topic, key);

        CompletableFuture<SendResult<K, V>> future = kafkaTemplate.send(topic, key, value);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error(
                        "Failed to send event to Kafka — topic: {}, key: {}, reason: {}",
                        topic, key, ex.getMessage(), ex
                );
                handleSendFailure(topic, key, value, ex);
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
    private void handleSendFailure(String topic, K key, V value, Throwable ex) {
        log.warn(
                "Event dropped — consider implementing a Dead Letter Queue. topic: {}, key: {}",
                topic, key
        );
    }
}