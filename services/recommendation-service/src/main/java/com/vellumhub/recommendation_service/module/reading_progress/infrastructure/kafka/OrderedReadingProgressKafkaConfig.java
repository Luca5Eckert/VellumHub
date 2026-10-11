package com.vellumhub.recommendation_service.module.reading_progress.infrastructure.kafka;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Blocking retries: never skip a revision and continue with the next message in the same partition. */
@Configuration
public class OrderedReadingProgressKafkaConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderedReadingProgressContainerFactory(
            ConsumerFactory<String,Object> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String,Object>();
        factory.setConsumerFactory(consumerFactory);
        // Deliberately do not recover to DLT: skipping a revision would violate ordered replay.
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(3000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }
}

