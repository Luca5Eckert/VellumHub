package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.avro.UserPreferenceAvroDecoder;
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
public class OrderedPreferenceAvroKafkaConfig {
    @Bean
    UserPreferenceAvroDecoder userPreferenceAvroDecoder(@Value("${app.user-preference.avro.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}") String registryUrl) {
        return new UserPreferenceAvroDecoder(registryUrl);
    }

    @Bean("orderedPreferenceAvroContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, Object> orderedPreferenceAvroContainerFactory(
            ConsumerFactory<String, Object> legacyFactory, OrderedKafkaRecovery recovery) {
        var properties = new HashMap<>(legacyFactory.getConfigurationProperties());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        // The DLT/recovery must see the ORIGINAL Avro frame, including schema ID.
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG,
                KafkaConsumerGroups.RECOMMENDATION_PREFERENCES_AVRO);
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        factory.setConcurrency(1);
        recovery.configure(factory, KafkaConsumerGroups.RECOMMENDATION_PREFERENCES_AVRO);
        return factory;
    }
}
