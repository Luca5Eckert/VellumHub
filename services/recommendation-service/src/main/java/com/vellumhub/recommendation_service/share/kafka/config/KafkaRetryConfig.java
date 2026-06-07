package com.vellumhub.recommendation_service.share.kafka.config;

import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
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

    private static final String DLT_CONSUMER_GROUP = "recommendation-service-dlt-group";

    private final VellumHubMetrics metrics;

    public KafkaRetryConfig(VellumHubMetrics metrics) {
        this.metrics = metrics;
    }

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
                        "created-user-preference",
                        "created-reading-progress",
                        "updated-reading-progress",
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
            groupId = "recommendation-service-dlt-group",
            containerFactory = "dltKafkaListenerContainerFactory"
    )
    public void consumeDlt(
            byte[] payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(name = KafkaHeaders.ORIGINAL_TOPIC, required = false) String originalTopic,
            @Header(name = KafkaHeaders.EXCEPTION_MESSAGE, required = false) String errorMessage
    ) {

        String quarantinedTopic = originalTopic(topic, originalTopic);
        metrics.recordKafkaDlt(quarantinedTopic, DLT_CONSUMER_GROUP);
        log.error(
                "Kafka DLT message quarantined. operation=kafka_dlt_quarantine, event_type=kafka_dlt, originalTopic={}, dltTopic={}, error={}, payloadBytes={}",
                quarantinedTopic,
                topic,
                errorMessage,
                payloadByteLength(payload)
        );

    }

    private String originalTopic(String dltTopic, String originalTopic) {
        if (originalTopic != null && !originalTopic.isBlank()) {
            return originalTopic;
        }
        if (dltTopic != null && dltTopic.endsWith("-dlt")) {
            return dltTopic.substring(0, dltTopic.length() - "-dlt".length());
        }
        return "unknown";
    }

    private int payloadByteLength(byte[] payload) {
        return payload == null ? 0 : payload.length;
    }

}
