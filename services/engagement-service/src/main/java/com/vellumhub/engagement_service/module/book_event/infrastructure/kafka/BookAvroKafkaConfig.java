package com.vellumhub.engagement_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.engagement_service.share.kafka.recovery.OrderedKafkaRecovery;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import java.util.HashMap;

/** Isolated byte-preserving Book Avro listener and canonical ordered-recovery policy. */
@Configuration
@ConditionalOnProperty(name="app.book.avro.consumer-enabled", havingValue="true")
public class BookAvroKafkaConfig {
    @Bean("bookAvroContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> bookAvroContainerFactory(
            KafkaProperties kafkaProperties, OrderedKafkaRecovery recovery) {
        var properties = new HashMap<String, Object>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        var result = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        result.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        recovery.configure(result, KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO);
        return result;
    }
}
