package com.vellumhub.engagement_service.module.book_snapshot.presentation.consumer;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.DeleteBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.application.use_case.DeleteBookSnapshotUseCase;
import com.vellumhub.engagement_service.module.book_snapshot.presentation.event.DeleteBookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeleteBookEventConsumer {

    private final DeleteBookSnapshotUseCase deleteBookSnapshotUseCase;

    public DeleteBookEventConsumer(DeleteBookSnapshotUseCase deleteBookSnapshotUseCase) {
        this.deleteBookSnapshotUseCase = deleteBookSnapshotUseCase;
    }

    @KafkaListener(
            topics = "${kafka.topics.deleted-book}",
            groupId = "${kafka.group-id}"
    )
    public void consume(DeleteBookEvent event) {
        log.info("DeleteBookEvent received for bookId: {}", event.bookId());

        try {
            var command = new DeleteBookSnapshotCommand(event.bookId());
            deleteBookSnapshotUseCase.execute(command);
            log.info("Book snapshot successfully deleted for bookId: {}", event.bookId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid event received, discarding. bookId: {}. Reason: {}", event.bookId(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing DeleteBookEvent for bookId: {}", event.bookId(), e);
            throw e;
        }
    }
    
}