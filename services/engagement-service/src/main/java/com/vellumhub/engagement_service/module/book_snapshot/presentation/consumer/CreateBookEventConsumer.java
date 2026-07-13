package com.vellumhub.engagement_service.module.book_snapshot.presentation.consumer;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.CreateBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.application.use_case.CreateBookSnapshotUseCase;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreateBookEventConsumer {

    private static final String TOPIC = KafkaTopics.CREATED_BOOK;
    private static final String EVENT_TYPE = "CreateBookEvent";
    private static final String CONSUMER_GROUP = KafkaConsumerGroups.ENGAGEMENT_SERVICE;

    private final CreateBookSnapshotUseCase createBookSnapshotUseCase;
    private final VellumHubMetrics metrics;

    public CreateBookEventConsumer(CreateBookSnapshotUseCase createBookSnapshotUseCase, VellumHubMetrics metrics) {
        this.createBookSnapshotUseCase = createBookSnapshotUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = KafkaTopics.CREATED_BOOK,
            groupId = KafkaConsumerGroups.ENGAGEMENT_SERVICE
    )
    public void consume(CreateBookEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info("CreateBookEvent received for bookId: {}", event.bookId());

        try {
            var command = new CreateBookSnapshotCommand(event.bookId());
            createBookSnapshotUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Book snapshot successfully created for bookId: {}", event.bookId());

    }

}
