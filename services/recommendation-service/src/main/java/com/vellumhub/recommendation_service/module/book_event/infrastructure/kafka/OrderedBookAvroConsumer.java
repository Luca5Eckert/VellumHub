package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.avro.BookAvroMapper;
import com.vellumhub.kafka.avro.book.BookChangedEvent;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.recommendation_service.module.book_event.application.use_case.ApplyBookEventUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import java.util.HashMap;

/**
 * Receives untouched Avro wire bytes, preserving raw payloads for DLT/quarantine
 * if deserialization fails. The application uses the existing shared checkpoint.
 */
@Component
@ConditionalOnProperty(name="app.book.avro.consumer-enabled", havingValue="true")
public class OrderedBookAvroConsumer {
    private final ApplyBookEventUseCase projection;
    private final VellumHubMetrics metrics;
    private final KafkaAvroDeserializer decoder;

    public OrderedBookAvroConsumer(ApplyBookEventUseCase projection, VellumHubMetrics metrics,
                                  @Value("${app.book.avro.registry-url:}") String registryUrl) {
        if (registryUrl.isBlank()) throw new IllegalArgumentException("Missing Book Avro Registry URL");
        this.projection = projection;
        this.metrics = metrics;
        decoder = new KafkaAvroDeserializer();
        var config = new HashMap<String, Object>();
        config.put("schema.registry.url", registryUrl);
        config.put("specific.avro.reader", true);
        decoder.configure(config, false);
    }

    @KafkaListener(topics=KafkaTopics.BOOK_EVENTS_AVRO,
                   groupId=KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO,
                   containerFactory="bookAvroContainerFactory")
    public void consume(ConsumerRecord<String, byte[]> record) {
        var sample = metrics.startKafkaProcessing();
        try {
            Object deserialized = decoder.deserialize(record.topic(), record.value());
            if (!(deserialized instanceof BookChangedEvent wire))
                throw new IllegalArgumentException("Unexpected Avro Book wire record");
            var event = BookAvroMapper.decodeForKey(wire, record.key());
            var outcome = projection.execute(event);
            if (outcome == com.vellumhub.recommendation_service.share.application.EventProcessingOutcome.DUPLICATE) {
                metrics.recordKafkaDuplicate(KafkaTopics.BOOK_EVENTS_AVRO, "BookChangedEvent",
                        KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO);
            }
            metrics.recordKafkaConsumed(KafkaTopics.BOOK_EVENTS_AVRO, "BookChangedEvent",
                    KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO);
            metrics.recordKafkaProcessingDuration(sample, KafkaTopics.BOOK_EVENTS_AVRO,
                    "BookChangedEvent", KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO, "success");
        } catch (RuntimeException failure) {
            metrics.recordKafkaConsumeFailed(KafkaTopics.BOOK_EVENTS_AVRO, "BookChangedEvent",
                    KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO);
            metrics.recordKafkaProcessingDuration(sample, KafkaTopics.BOOK_EVENTS_AVRO,
                    "BookChangedEvent", KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO, "failure");
            throw failure;
        }
    }
}
