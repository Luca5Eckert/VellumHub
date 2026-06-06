package com.vellumhub.catalog_service.share.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class VellumHubMetrics {

    public static final String KAFKA_EVENTS_PUBLISHED = "vellumhub.kafka.events.published";
    public static final String KAFKA_EVENTS_PUBLISH_FAILED = "vellumhub.kafka.events.publish.failed";
    public static final String KAFKA_EVENTS_CONSUMED = "vellumhub.kafka.events.consumed";
    public static final String KAFKA_EVENTS_CONSUME_FAILED = "vellumhub.kafka.events.consume.failed";
    public static final String KAFKA_EVENT_PROCESSING_DURATION = "vellumhub.kafka.event.processing.duration";
    public static final String KAFKA_DLT_EVENTS = "vellumhub.kafka.dlt.events";

    public static final String USERS_CREATED = "vellumhub.users.created";
    public static final String BOOKS_CREATED = "vellumhub.books.created";
    public static final String BOOKS_UPDATED = "vellumhub.books.updated";
    public static final String BOOKS_DELETED = "vellumhub.books.deleted";
    public static final String READING_PROGRESS_UPDATED = "vellumhub.reading.progress.updated";
    public static final String RATINGS_CREATED = "vellumhub.ratings.created";
    public static final String REACTIONS_CHANGED = "vellumhub.reactions.changed";
    public static final String RECOMMENDATIONS_REQUESTED = "vellumhub.recommendations.requested";
    public static final String RECOMMENDATIONS_GENERATED = "vellumhub.recommendations.generated";
    public static final String RECOMMENDATION_EMPTY_RESULTS = "vellumhub.recommendation.empty.results";
    public static final String RECOMMENDATION_GENERATION_DURATION = "vellumhub.recommendation.generation.duration";

    private final MeterRegistry meterRegistry;

    public VellumHubMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    public void recordKafkaPublished(String topic, Object event) {
        counter(KAFKA_EVENTS_PUBLISHED, "topic", topic, "event_type", eventType(event), "result", "success").increment();
    }

    public void recordKafkaPublishFailed(String topic, Object event) {
        counter(KAFKA_EVENTS_PUBLISH_FAILED, "topic", topic, "event_type", eventType(event), "result", "failure").increment();
    }

    public Timer.Sample startKafkaProcessing() {
        return Timer.start(meterRegistry);
    }

    public void recordKafkaConsumed(String topic, String eventType, String consumerGroup) {
        counter(KAFKA_EVENTS_CONSUMED, "topic", topic, "event_type", eventType, "consumer_group", consumerGroup, "result", "success").increment();
    }

    public void recordKafkaConsumeFailed(String topic, String eventType, String consumerGroup) {
        counter(KAFKA_EVENTS_CONSUME_FAILED, "topic", topic, "event_type", eventType, "consumer_group", consumerGroup, "result", "failure").increment();
    }

    public void recordKafkaProcessingDuration(Timer.Sample sample, String topic, String eventType, String consumerGroup, String result) {
        sample.stop(Timer.builder(KAFKA_EVENT_PROCESSING_DURATION)
                .tags("topic", topic, "event_type", eventType, "consumer_group", consumerGroup, "result", result)
                .register(meterRegistry));
    }

    public void recordKafkaDlt(String originalTopic, String consumerGroup) {
        counter(KAFKA_DLT_EVENTS, "topic", originalTopic, "event_type", "kafka_dlt", "consumer_group", consumerGroup, "operation", "kafka_dlt_quarantine", "result", "quarantined").increment();
    }

    public void recordBusinessCounter(String name, String operation, String result) {
        counter(name, "operation", operation, "result", result).increment();
    }

    public Timer.Sample startBusinessTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordRecommendationGenerationDuration(Timer.Sample sample, String result) {
        sample.stop(Timer.builder(RECOMMENDATION_GENERATION_DURATION)
                .tags("operation", "recommendation_generation", "result", result)
                .register(meterRegistry));
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private String eventType(Object event) {
        return event == null ? "unknown" : event.getClass().getSimpleName();
    }
}
