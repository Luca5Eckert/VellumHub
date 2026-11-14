package com.vellumhub.engagement_service.module.reading_progress.infrastructure.kafka;

import com.vellumhub.kafka.avro.ReadingProgressAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.engagement_service.share.kafka.recovery.OrderedKafkaRecovery;
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

/** Separate read group and raw byte boundary prevent JSON consumers from seeing Avro. */
@Configuration(proxyBeanMethods = false)
public class OrderedReadingProgressAvroKafkaConfig {
    @Bean
    ReadingProgressAvroDecoder readingProgressAvroDecoder(
            @Value("${app.reading-progress.avro.registry-url:http://127.0.0.1:18081/apis/ccompat/v7}") String url) {
        return new ReadingProgressAvroDecoder(url);
    }

    @Bean("orderedReadingProgressAvroContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String,Object> orderedReadingProgressAvroContainerFactory(
            ConsumerFactory<String,Object> legacyFactory, OrderedKafkaRecovery recovery) {
        var config = new HashMap<>(legacyFactory.getConfigurationProperties());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, KafkaConsumerGroups.ENGAGEMENT_READING_PROGRESS_AVRO);
        var factory = new ConcurrentKafkaListenerContainerFactory<String,Object>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(config));
        factory.setConcurrency(1);
        recovery.configure(factory, KafkaConsumerGroups.ENGAGEMENT_READING_PROGRESS_AVRO);
        return factory;
    }
}
