package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.consumer;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.event.CreateBookProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateReadingProgressEventConsumer {

    private final CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase;

    public CreateReadingProgressEventConsumer(CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase) {
        this.createReadingSessionEntryUseCase = createReadingSessionEntryUseCase;
    }

    @KafkaListener(
            topics = "create-reading-progress",
            groupId = "engagement-service"
    )
    public void consume(CreateBookProgressEvent event){
        log.info("Received CreateBookProgressEvent: {}", event);

        var command = CreateReadingSessionEntryCommand.create(
                event.userId(),
                event.bookId(),
                event.progress(),
                event.initPage()
        );

        log.info("Executing CreateReadingSessionEntryUseCase with command: {}", command);

        createReadingSessionEntryUseCase.execute(command);

        log.info("Finished processing CreateBookProgressEvent for userId: {}, bookId: {}", event.userId(), event.bookId());
    }

}
