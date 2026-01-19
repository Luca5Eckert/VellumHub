package com.mrs.catalog_service.infrastructure.producer;

import com.mrs.catalog_service.domain.port.EventProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventProducer<K, V> implements EventProducer<K, V> {

    private final KafkaTemplate<K, V> kafkaTemplate;

    public KafkaEventProducer(KafkaTemplate<K, V> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, K key, V value) {
        kafkaTemplate.send(topic, key, value);
    }
}