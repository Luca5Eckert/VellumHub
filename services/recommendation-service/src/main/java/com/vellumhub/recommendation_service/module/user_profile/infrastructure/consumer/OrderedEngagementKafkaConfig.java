package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Never advance a user-profile partition past a failed incremental adjustment. */
@Configuration
public class OrderedEngagementKafkaConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,Object> orderedEngagementContainerFactory(
            ConsumerFactory<String,Object> consumerFactory) {
        var f=new ConcurrentKafkaListenerContainerFactory<String,Object>();
        f.setConsumerFactory(consumerFactory);
        f.setCommonErrorHandler(new DefaultErrorHandler(
                new FixedBackOff(3000L,FixedBackOff.UNLIMITED_ATTEMPTS)));
        return f;
    }
}

