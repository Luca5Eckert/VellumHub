package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.consumer;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.CreateBookProgressEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateReadingProgressEventConsumer {

    private static final String TOPIC = KafkaTopics.CREATED_READING_PROGRESS;
    private static final String EVENT_TYPE = "CreateBookProgressEvent";
    private static final String CONSUMER_GROUP = KafkaConsumerGroups.ENGAGEMENT_SERVICE;

    private final CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase;
    private final VellumHubMetrics metrics;

    public CreateReadingProgressEventConsumer(CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase, VellumHubMetrics metrics) {
        this.createReadingSessionEntryUseCase = createReadingSessionEntryUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = KafkaTopics.CREATED_READING_PROGRESS,
            groupId = KafkaConsumerGroups.ENGAGEMENT_SERVICE
    )
    public void consume(CreateBookProgressEvent event){
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info(
                "Received CreateBookProgressEvent. operation=kafka_consume, topic=created-reading-progress, event_type=CreateBookProgressEvent, userId={}, bookId={}, progress={}, page={}",
                event.userId(),
                event.bookId(),
                event.progress(),
                event.initPage()
        );

        var command = CreateReadingSessionEntryCommand.create(
                event.bookId(),
                event.bookProgressId(),
                event.userId(),
                event.progress(),
                event.initPage()
        );

        log.info(
                "Executing CreateReadingSessionEntryUseCase. operation=create_reading_session_entry, event_type=CreateBookProgressEvent, userId={}, bookId={}, page={}",
                event.userId(),
                event.bookId(),
                event.initPage()
        );

        try {
            createReadingSessionEntryUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Finished processing CreateBookProgressEvent for userId: {}, bookId: {}", event.userId(), event.bookId());
    }

}
