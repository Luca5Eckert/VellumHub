package com.vellumhub.recommendation_service.share.kafka.recovery;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/** The recovery producer carries raw bytes and a strict, sanitized header set. */
@Configuration
public class OrderedRecoveryPublisherConfig {
    @Bean("orderedRecoveryDltKafkaTemplate")
    KafkaTemplate<String, byte[]> orderedRecoveryDltKafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrap) {
        var properties = Map.<String, Object>of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap,
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                properties, new StringSerializer(), new ByteArraySerializer()));
    }
}
