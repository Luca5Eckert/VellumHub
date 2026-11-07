package com.vellumhub.catalog_service.module.outbox.infrastructure.kafka;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

/**
 * Provides a second producer without modifying the global JSON serializer.
 * The Registry only looks up IDs; services may not auto-register contracts.
 */
@Configuration
@ConditionalOnProperty(name = "app.book.avro.registry-url", matchIfMissing = false)
public class BookAvroProducerConfig {
    @Bean
    @Primary
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
    @Bean("bookAvroKafkaTemplate")
    KafkaTemplate<String, Object> bookAvroKafkaTemplate(KafkaProperties kafkaProperties,
           @Value("\${app.book.avro.registry-url}") String registryUrl) {
        if (registryUrl.isBlank() || !registryUrl.startsWith("http"))
            throw new IllegalArgumentException("Configured Book Avro Registry URL must be HTTP(S)");
        var props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                "org.apache.kafka.common.serialization.StringSerializer");
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put("schema.registry.url", registryUrl);
        props.put("auto.register.schemas", false);
        props.put("avro.remove.java.properties", true);
        props.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
