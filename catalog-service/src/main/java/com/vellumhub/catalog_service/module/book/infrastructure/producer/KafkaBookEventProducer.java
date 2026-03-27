package com.mrs.catalog_service.module.book.infrastructure.producer;

import com.mrs.catalog_service.module.book.domain.port.BookEventProducer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaBookEventProducer<K, V> implements BookEventProducer<K, V> {

    private final KafkaTemplate<K, V> kafkaTemplate;

    public KafkaBookEventProducer(KafkaTemplate<K, V> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void send(String topic, K key, V value) {
        kafkaTemplate.send(topic, key, value);
    }
}