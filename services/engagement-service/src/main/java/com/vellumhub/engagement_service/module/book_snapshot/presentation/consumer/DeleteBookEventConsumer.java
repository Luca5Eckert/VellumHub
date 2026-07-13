package com.vellumhub.engagement_service.module.book_snapshot.presentation.consumer;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.DeleteBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.application.use_case.DeleteBookSnapshotUseCase;
import com.vellumhub.engagement_service.share.metrics.VellumHubMetrics;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.DeleteBookEvent;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeleteBookEventConsumer {

    private static final String TOPIC = KafkaTopics.DELETED_BOOK;
    private static final String EVENT_TYPE = "DeleteBookEvent";
    private static final String CONSUMER_GROUP = KafkaConsumerGroups.ENGAGEMENT_SERVICE;

    private final DeleteBookSnapshotUseCase deleteBookSnapshotUseCase;
    private final VellumHubMetrics metrics;

    public DeleteBookEventConsumer(DeleteBookSnapshotUseCase deleteBookSnapshotUseCase, VellumHubMetrics metrics) {
        this.deleteBookSnapshotUseCase = deleteBookSnapshotUseCase;
        this.metrics = metrics;
    }

    @KafkaListener(
            topics = KafkaTopics.DELETED_BOOK,
            groupId = KafkaConsumerGroups.ENGAGEMENT_SERVICE
    )
    public void consume(DeleteBookEvent event) {
        Timer.Sample sample = metrics.startKafkaProcessing();
        log.info("DeleteBookEvent received for bookId: {}", event.bookId());

        try {
            var command = new DeleteBookSnapshotCommand(event.bookId());
            deleteBookSnapshotUseCase.execute(command);
            metrics.recordKafkaConsumed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "success");
        } catch (RuntimeException ex) {
            metrics.recordKafkaConsumeFailed(TOPIC, EVENT_TYPE, CONSUMER_GROUP);
            metrics.recordKafkaProcessingDuration(sample, TOPIC, EVENT_TYPE, CONSUMER_GROUP, "failure");
            throw ex;
        }

        log.info("Book snapshot successfully deleted for bookId: {}", event.bookId());

    }

}
