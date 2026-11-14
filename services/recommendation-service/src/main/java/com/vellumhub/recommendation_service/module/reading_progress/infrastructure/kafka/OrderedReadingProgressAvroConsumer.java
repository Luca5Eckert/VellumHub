package com.vellumhub.recommendation_service.module.reading_progress.infrastructure.kafka;

import com.vellumhub.kafka.avro.ReadingProgressAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.recommendation_service.module.reading_progress.application.use_case.ProcessOrderedReadingProgressUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Reuse the exact same transactional projection and revision checkpoint as JSON. */
@Component
public class OrderedReadingProgressAvroConsumer {
    private final ReadingProgressAvroDecoder decoder;
    private final ProcessOrderedReadingProgressUseCase projection;

    public OrderedReadingProgressAvroConsumer(ReadingProgressAvroDecoder decoder,
            ProcessOrderedReadingProgressUseCase projection) {
        this.decoder = decoder;
        this.projection = projection;
    }

    @KafkaListener(topics=KafkaTopics.READING_PROGRESS_AVRO,
            groupId=KafkaConsumerGroups.RECOMMENDATION_READING_PROGRESS_AVRO,
            containerFactory="orderedReadingProgressAvroContainerFactory",
            autoStartup="${app.reading-progress.avro.consumer-enabled:false}")
    public void consume(ConsumerRecord<String,byte[]> record) {
        // Failure handler sees the unchanged byte[] (magic byte + schema ID).
        var event = decoder.decode(record.topic(),record.key(),record.value());
        projection.execute(event);
    }
}
