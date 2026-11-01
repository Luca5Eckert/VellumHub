package com.vellumhub.user_service.share.config;

import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import com.vellumhub.kafka.contracts.user.CreateUserPreferenceEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import java.util.Map;

@Configuration
public class KafkaConfig {

    private final KafkaProperties kafkaProperties;

    public KafkaConfig(KafkaProperties kafkaProperties) {
        this.kafkaProperties = kafkaProperties;
    }

    @Bean
    public ProducerFactory<String, CreateUserPreferenceEvent> producerFactory() {
        // Include broker identity, SASL, TLS, acks, and all common Spring Kafka properties.
        Map<String, Object> configProps = kafkaProperties.buildProducerProperties();
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.springframework.kafka.support.serializer.JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, CreateUserPreferenceEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

}
