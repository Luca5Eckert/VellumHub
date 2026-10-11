package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Never skip a failed revision. Unbounded blocking retry protects order, but stalls a
 * partition on poison messages; operators must alert on lag and repair the dependency.
 */
@Configuration
public class OrderedBookKafkaConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderedBookContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(
                new DefaultErrorHandler(new FixedBackOff(3000L, FixedBackOff.UNLIMITED_ATTEMPTS)));
        return factory;
    }
}

