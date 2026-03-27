package com.vellumhub.engagement_service.module.book_snapshot.presentation.consumer;

import com.vellumhub.engagement_service.module.book_snapshot.application.command.CreateBookSnapshotCommand;
import com.vellumhub.engagement_service.module.book_snapshot.application.use_case.CreateBookSnapshotUseCase;
import com.vellumhub.engagement_service.module.book_snapshot.presentation.event.CreateBookEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CreateBookEventConsumer {

    private final CreateBookSnapshotUseCase createBookSnapshotUseCase;

    public CreateBookEventConsumer(CreateBookSnapshotUseCase createBookSnapshotUseCase) {
        this.createBookSnapshotUseCase = createBookSnapshotUseCase;
    }

    @KafkaListener(
            topics = "${kafka.topics.created-book}",
            groupId = "${kafka.group-id}"
    )
    public void consume(CreateBookEvent event) {
        log.info("CreateBookEvent received for bookId: {}", event.bookId());

        try {
            var command = new CreateBookSnapshotCommand(event.bookId());
            createBookSnapshotUseCase.execute(command);
            log.info("Book snapshot successfully created for bookId: {}", event.bookId());
        } catch (IllegalArgumentException e) {
            log.error("Invalid event received, discarding. bookId: {}. Reason: {}", event.bookId(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error processing CreateBookEvent for bookId: {}", event.bookId(), e);
            throw e;
        }
    }
}