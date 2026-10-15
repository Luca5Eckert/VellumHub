package com.vellumhub.recommendation_service.module.reading_progress.infrastructure.kafka;

import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import com.vellumhub.recommendation_service.module.reading_progress.application.use_case.ProcessOrderedReadingProgressUseCase;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderedReadingProgressConsumer {
    private static final String EVENT_TYPE = "ReadingProgressChangedEvent";
    private static final String GROUP = KafkaConsumerGroups.RECOMMENDATION_SERVICE;

    private final ProcessOrderedReadingProgressUseCase process;
    private final VellumHubMetrics metrics;

    public OrderedReadingProgressConsumer(ProcessOrderedReadingProgressUseCase process,
                                          VellumHubMetrics metrics) {
        this.process = process;
        this.metrics = metrics;
    }

    @KafkaListener(topics = KafkaTopics.READING_PROGRESS, groupId = GROUP,
            containerFactory = "orderedReadingProgressContainerFactory")
    public void consume(ReadingProgressChangedEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        try {
            EventProcessingOutcome outcome = process.execute(event);
            metrics.recordKafkaConsumed(KafkaTopics.READING_PROGRESS, EVENT_TYPE, GROUP);
            if (outcome == EventProcessingOutcome.DUPLICATE) {
                metrics.recordKafkaDuplicate(KafkaTopics.READING_PROGRESS, EVENT_TYPE, GROUP);
                log.debug("operation=reading_progress_duplicate eventId={} aggregateId={} revision={}",
                        event.eventId(), event.bookProgressId(), event.revision());
            }
            metrics.recordKafkaProcessingDuration(sample, KafkaTopics.READING_PROGRESS,
                    EVENT_TYPE, GROUP, outcome == EventProcessingOutcome.DUPLICATE ? "duplicate" : "success");
        } catch (RuntimeException error) {
            metrics.recordKafkaConsumeFailed(KafkaTopics.READING_PROGRESS, EVENT_TYPE, GROUP);
            metrics.recordKafkaProcessingDuration(sample, KafkaTopics.READING_PROGRESS,
                    EVENT_TYPE, GROUP, "failure");
            log.error("operation=reading_progress_processing_failed eventId={} aggregateId={} revision={}",
                    event.eventId(), event.bookProgressId(), event.revision(), error);
            throw error;
        }
    }
}
