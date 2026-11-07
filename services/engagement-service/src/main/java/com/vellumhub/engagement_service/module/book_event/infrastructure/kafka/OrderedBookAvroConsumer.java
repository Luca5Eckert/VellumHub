package com.vellumhub.engagement_service.module.book_event.infrastructure.kafka;

import com.vellumhub.kafka.avro.BookAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.engagement_service.module.book_event.application.use_case.ApplyBookEventUseCase;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Raw Kafka wire is preserved for OrderedKafkaRecovery/DLT and original-offset replay. */
@Component
public class OrderedBookAvroConsumer {
    private final BookAvroDecoder decoder;
    private final ApplyBookEventUseCase projection;
    public OrderedBookAvroConsumer(BookAvroDecoder decoder, ApplyBookEventUseCase projection) {
        this.decoder = decoder; this.projection = projection;
    }
    @KafkaListener(topics = KafkaTopics.BOOK_EVENTS_AVRO,
            groupId = KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO,
            containerFactory = "orderedBookAvroContainerFactory",
            autoStartup = "${app.book.avro.consumer-enabled:false}")
    public void consume(ConsumerRecord<String, byte[]> record) {
        var event = decoder.decode(record.topic(), record.key(), record.value());
        // Existing @Transactional method owns the read-model mutation and revision fence.
        projection.execute(event);
    }
}
