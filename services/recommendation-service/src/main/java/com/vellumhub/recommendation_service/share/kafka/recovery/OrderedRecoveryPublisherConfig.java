package com.vellumhub.recommendation_service.share.kafka.recovery;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.Map;

/** The recovery producer carries raw bytes and a strict, sanitized header set. */
@Configuration
public class OrderedRecoveryPublisherConfig {
    // Supplying a special-purpose KafkaTemplate suppresses Spring Boot's
    // default template. Restore the ordinary, typed application producer.
    @Bean
    @Primary
    KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean("orderedRecoveryDltKafkaTemplate")
    KafkaTemplate<String, byte[]> orderedRecoveryDltKafkaTemplate(
            KafkaProperties kafkaProperties) {
        var properties = kafkaProperties.buildProducerProperties();
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                properties, new StringSerializer(), new ByteArraySerializer()));
    }
}
