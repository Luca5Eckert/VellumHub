package com.mrs.engagement_service.module.rating.infrastructure.producer;

import com.mrs.engagement_service.module.rating.domain.port.EventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventProducer<K, V> implements EventProducer<K, V> {

    private final KafkaTemplate<K, V> kafkaTemplate;

    @Override
    public void send(String topic, K key, V value) {
        log.debug("Publishing event to topic: {} | Key: {}", topic, key);

        CompletableFuture<SendResult<K, V>> future = kafkaTemplate.send(topic, key, value);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                handleSuccess(topic, key, result);
            } else {
                handleFailure(topic, key, ex);
            }
        });
    }

    private void handleSuccess(String topic, K key, SendResult<K, V> result) {
        log.info("Event sent successfully. Topic: {} | Partition: {} | Offset: {} | Key: {}",
                topic,
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset(),
                key);
    }

    private void handleFailure(String topic, K key, Throwable ex) {
        log.error("Failed to send event. Topic: {} | Key: {} | Error: {}",
                topic,
                key,
                ex.getMessage());
    }
}