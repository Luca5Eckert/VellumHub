package com.vellumhub.recommendation_service.share.kafka.config;

import org.springframework.messaging.handler.annotation.Header;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.support.KafkaHeaders;

import java.util.List;

@Configuration
@Slf4j
public class KafkaRetryConfig {

    /**
     * Defines a default retry configuration for Kafka listeners in the application.
     * @param template the KafkaTemplate used to send messages to retry topics
     * @return a RetryTopicConfiguration that applies to all specified topics with a fixed backoff strategy and a maximum of 3 attempts.
     */
    @Bean
    public RetryTopicConfiguration defaultRetryConfig(KafkaTemplate<String, Object> template) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(3)
                .fixedBackOff(3000)
                .includeTopics(List.of(
                        "created-book",
                        "deleted-book",
                        "updated-book",
                        "created-rating",
                        "create_user_preference",
                        "updated-book-progress",
                        "user-reaction-changed"
                ))
                .create(template);
    }


    /**
     * Listens to all Dead Letter Topics across the microservice.
     * The topicPattern ".*-dlt" ensures any topic ending with "-dlt" is captured here.
     */
    @KafkaListener(
            topicPattern = ".*-dlt",
            groupId = "recommendation-service-dlt-group"
    )
    public void consumeDlt(
            String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.ORIGINAL_TOPIC) String originalTopic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage
    ) {

        log.error("CRITICAL FAILURE: Unrecoverable message routed to DLT.");
        log.error("Original Topic  : {}", originalTopic);
        log.error("DLT Topic       : {}", topic);
        log.error("Exception Reason: {}", errorMessage);
        log.error("Message Payload : {}", payload);
        log.error("=====================================================");

    }


}