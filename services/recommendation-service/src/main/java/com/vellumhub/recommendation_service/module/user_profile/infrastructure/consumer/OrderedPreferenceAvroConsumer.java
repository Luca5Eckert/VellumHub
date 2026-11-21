package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.vellumhub.kafka.avro.UserPreferenceAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderedPreferenceAvroConsumer {
    private static final String TOPIC = KafkaTopics.USER_PREFERENCE_EVENTS_AVRO;
    private static final String GROUP = KafkaConsumerGroups.RECOMMENDATION_PREFERENCES_AVRO;
    private static final String EVENT_TYPE = "UserPreferenceChangedEvent";

    private final UserPreferenceAvroDecoder decoder;
    private final OrderedPreferenceProjection projection;
    private final VellumHubMetrics metrics;

    public OrderedPreferenceAvroConsumer(UserPreferenceAvroDecoder decoder, OrderedPreferenceProjection projection,
                                         VellumHubMetrics metrics) {
        this.decoder = decoder;
        this.projection = projection;
        this.metrics = metrics;
    }

    @KafkaListener(topics = TOPIC,
            groupId = GROUP,
            containerFactory = "orderedPreferenceAvroContainerFactory",
            autoStartup = "${app.user-preference.avro.consumer-enabled:false}")
    public void consume(ConsumerRecord<String, byte[]> record) {
        var timer = metrics.startKafkaProcessing();
        try {
            boolean applied = projection.apply(decoder.decode(record.topic(), record.key(), record.value()));
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, GROUP);
            if (!applied) {
                metrics.recordKafkaDuplicate(TOPIC, EVENT_TYPE, GROUP);
            }
            metrics.recordKafkaProcessingDuration(timer, TOPIC, EVENT_TYPE, GROUP, "success");
        } catch (RuntimeException failure) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, GROUP);
            metrics.recordKafkaProcessingDuration(timer, TOPIC, EVENT_TYPE, GROUP, "failure");
            throw failure;
        }
    }
}
