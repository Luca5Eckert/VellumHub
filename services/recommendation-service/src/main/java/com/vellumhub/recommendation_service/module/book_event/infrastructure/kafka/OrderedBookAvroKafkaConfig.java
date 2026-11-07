package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.avro.BookAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.recommendation_service.share.kafka.recovery.OrderedKafkaRecovery;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import java.util.HashMap;

@Configuration(proxyBeanMethods = false)
public class OrderedBookAvroKafkaConfig {
    @Bean
    BookAvroDecoder bookAvroDecoder(@Value("${app.book.avro.registry-url}") String registryUrl) {
        return new BookAvroDecoder(registryUrl);
    }

    @Bean("orderedBookAvroContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> orderedBookAvroContainerFactory(
            ConsumerFactory<String, Object> legacyFactory, OrderedKafkaRecovery recovery) {
        var properties = new HashMap<>(legacyFactory.getConfigurationProperties());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // The DLT/recovery must see the ORIGINAL Avro frame, including schema ID.
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG,
                KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.setConcurrency(1);
        recovery.configure(factory, KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO);
        return factory;
    }
}
