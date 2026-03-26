package com.mrs.engagement_service.module.book_snapshot.presentation.consumer;

import com.mrs.engagement_service.module.book_snapshot.application.command.CreateBookSnapshotCommand;
import com.mrs.engagement_service.module.book_snapshot.application.use_case.CreateBookSnapshotUseCase;
import com.mrs.engagement_service.module.book_snapshot.presentation.event.CreateBookEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateBookEventConsumer {

    private final CreateBookSnapshotUseCase createBookSnapshotUseCase;

    public CreateBookEventConsumer(CreateBookSnapshotUseCase createBookSnapshotUseCase) {
        this.createBookSnapshotUseCase = createBookSnapshotUseCase;
    }

    @KafkaListener(topics = "created-book", groupId = "engagement-service" )
    public void consume(CreateBookEvent event) {
        var command = new CreateBookSnapshotCommand(event.bookId());

        createBookSnapshotUseCase.execute(command);
    }

}
