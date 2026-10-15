package com.vellumhub.recommendation_service.module.book_event.infrastructure.kafka;

import com.vellumhub.recommendation_service.module.book_event.application.use_case.ApplyBookEventUseCase;
import com.vellumhub.recommendation_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import com.vellumhub.recommendation_service.share.application.EventProcessingOutcome;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Ordered, at-least-once transport with a durable per-book checkpoint. */
@Slf4j
@Component
public class OrderedBookEventConsumer {
    private final ApplyBookEventUseCase projection;
    private final VellumHubMetrics metrics;
    public OrderedBookEventConsumer(ApplyBookEventUseCase projection, VellumHubMetrics metrics) {
        this.projection=projection;this.metrics=metrics;
    }
    @KafkaListener(topics=KafkaTopics.BOOK_EVENTS,groupId=KafkaConsumerGroups.RECOMMENDATION_SERVICE,
                   containerFactory="orderedBookContainerFactory")
    public void consume(BookChangedEvent event) {
        Timer.Sample sample=metrics.startKafkaProcessing();
        try {
            EventProcessingOutcome outcome = projection.execute(event);
            if (outcome == EventProcessingOutcome.DUPLICATE) {
                metrics.recordKafkaDuplicate(KafkaTopics.BOOK_EVENTS,"BookChangedEvent",KafkaConsumerGroups.RECOMMENDATION_SERVICE);
                log.debug("operation=book_event_duplicate, bookId={}, eventId={}, revision={}",
                        event.bookId(),event.eventId(),event.revision());
            }
            metrics.recordKafkaConsumed(KafkaTopics.BOOK_EVENTS,"BookChangedEvent",KafkaConsumerGroups.RECOMMENDATION_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,KafkaTopics.BOOK_EVENTS,"BookChangedEvent",
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE,
                    outcome == EventProcessingOutcome.DUPLICATE ? "duplicate" : "success");
            if (outcome == EventProcessingOutcome.APPLIED)
                log.debug("operation=book_projection_applied, bookId={}, eventId={}, kind={}, revision={}",
                    event.bookId(),event.eventId(),event.kind(),event.revision());
        } catch(RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(KafkaTopics.BOOK_EVENTS,"BookChangedEvent",
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,KafkaTopics.BOOK_EVENTS,"BookChangedEvent",
                    KafkaConsumerGroups.RECOMMENDATION_SERVICE,"failure");
            log.error("operation=book_projection_failed, bookId={}, eventId={}, kind={}, revision={}",
                    event.bookId(),event.eventId(),event.kind(),event.revision(),ex);
            throw ex;
        }
    }
}

