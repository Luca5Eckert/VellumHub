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
            topics = "updated-reading-progress",
            groupId = "engagement-service"
    )
    public void consume(UpdateBookProgressEvent event){
        log.info(
                "Received UpdateBookProgressEvent. operation=kafka_consume, topic=updated-reading-progress, event_type=UpdateBookProgressEvent, userId={}, bookId={}, progress={}, page={}",
                event.userId(),
                event.bookId(),
                event.progress(),
                event.newPage()
        );

        var command = CreateReadingSessionEntryCommand.create(
                event.userId(),
                event.bookId(),
                event.progress(),
                event.newPage()
        );

        log.info(
                "Executing CreateReadingSessionEntryCommand. operation=create_reading_session_entry, event_type=UpdateBookProgressEvent, userId={}, bookId={}, page={}",
                event.userId(),
                event.bookId(),
                event.newPage()
        );

        createReadingSessionEntryUseCase.execute(command);

        log.info("Finished processing UpdateBookProgressEvent for userId: {}, bookId: {}", event.userId(), event.bookId());
    }

}
