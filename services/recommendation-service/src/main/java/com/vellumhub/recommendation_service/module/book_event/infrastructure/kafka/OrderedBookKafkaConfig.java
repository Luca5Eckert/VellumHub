package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.recommendation_service.share.kafka.recovery.OrderedKafkaRecovery;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;

/** A failed ordered revision must never be skipped or acknowledged. */
@Configuration
public class OrderedBookKafkaConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderedBookContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            OrderedKafkaRecovery recovery) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        recovery.configure(factory, KafkaConsumerGroups.RECOMMENDATION_SERVICE);
        return factory;
    }
}
