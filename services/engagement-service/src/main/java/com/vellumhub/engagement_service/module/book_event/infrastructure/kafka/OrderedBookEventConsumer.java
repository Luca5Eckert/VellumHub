package com.vellumhub.engagement_service.module.book_event.infrastructure.kafka;

import com.vellumhub.engagement_service.module.book_event.application.use_case.ApplyBookEventUseCase;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
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
    @KafkaListener(topics=KafkaTopics.BOOK_EVENTS,groupId=KafkaConsumerGroups.ENGAGEMENT_SERVICE,
                   containerFactory="orderedBookContainerFactory")
    public void consume(BookChangedEvent event) {
        Timer.Sample sample=metrics.startKafkaProcessing();
        try {
            projection.execute(event);
            metrics.recordKafkaConsumed(KafkaTopics.BOOK_EVENTS,"BookChangedEvent",KafkaConsumerGroups.ENGAGEMENT_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,KafkaTopics.BOOK_EVENTS,"BookChangedEvent",
                    KafkaConsumerGroups.ENGAGEMENT_SERVICE,"success");
            log.debug("operation=book_projection_applied, bookId={}, eventId={}, kind={}, revision={}",
                    event.bookId(),event.eventId(),event.kind(),event.revision());
        } catch(RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(KafkaTopics.BOOK_EVENTS,"BookChangedEvent",
                    KafkaConsumerGroups.ENGAGEMENT_SERVICE);
            metrics.recordKafkaProcessingDuration(sample,KafkaTopics.BOOK_EVENTS,"BookChangedEvent",
                    KafkaConsumerGroups.ENGAGEMENT_SERVICE,"failure");
            log.error("operation=book_projection_failed, bookId={}, eventId={}, kind={}, revision={}",
                    event.bookId(),event.eventId(),event.kind(),event.revision(),ex);
            throw ex;
        }
    }
}

