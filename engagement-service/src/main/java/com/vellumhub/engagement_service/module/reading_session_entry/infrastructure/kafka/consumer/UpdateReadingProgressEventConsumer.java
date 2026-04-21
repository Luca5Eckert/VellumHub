package com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.consumer;

import com.vellumhub.engagement_service.module.reading_session_entry.application.command.CreateReadingSessionEntryCommand;
import com.vellumhub.engagement_service.module.reading_session_entry.application.use_case.CreateReadingSessionEntryUseCase;
import com.vellumhub.engagement_service.module.reading_session_entry.infrastructure.kafka.event.UpdateBookProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateReadingProgressEventConsumer {

    private final CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase;

    public UpdateReadingProgressEventConsumer(CreateReadingSessionEntryUseCase createReadingSessionEntryUseCase) {
        this.createReadingSessionEntryUseCase = createReadingSessionEntryUseCase;
    }

    @KafkaListener(
            topics = "update-reading-progress",
            groupId = "engagement-service"
    )
    public void consume(UpdateBookProgressEvent event){
        log.info("Received UpdateBookProgressEvent: {}", event);

        var command = CreateReadingSessionEntryCommand.create(
                event.userId(),
                event.bookId(),
                event.progress(),
                event.newPage()
        );

        log.info("Executing CreateReadingSessionEntryCommand with command: {}", command);

        createReadingSessionEntryUseCase.execute(command);

        log.info("Finished processing UpdateBookProgressEvent for userId: {}, bookId: {}", event.userId(), event.bookId());
    }

}
