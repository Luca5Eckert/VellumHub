package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Never advance a user-profile partition past a failed incremental adjustment. */
@Slf4j
@Configuration
public class OrderedEngagementKafkaConfig {
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String,Object> orderedEngagementContainerFactory(
            ConsumerFactory<String,Object> consumerFactory) {
        var f=new ConcurrentKafkaListenerContainerFactory<String,Object>();
        f.setConsumerFactory(consumerFactory);
        var errorHandler = new DefaultErrorHandler(
                new FixedBackOff(3000L,FixedBackOff.UNLIMITED_ATTEMPTS));
        // Emit one diagnostic per failed delivery without logging payloads or secrets.
        // Ordered replay must still block instead of skipping a missing revision.
        errorHandler.setRetryListeners((record, error, attempt) -> {
            if (attempt == 1) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                log.warn("operation=ordered_engagement_retry topic={} partition={} offset={} cause={}",
                        record.topic(), record.partition(), record.offset(),
                        cause.getClass().getName());
            }
        });
        f.setCommonErrorHandler(errorHandler);
        return f;
    }
}

