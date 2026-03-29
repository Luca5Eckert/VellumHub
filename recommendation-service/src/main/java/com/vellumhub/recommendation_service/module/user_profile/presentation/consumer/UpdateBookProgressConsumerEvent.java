package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.UpdateBookProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookProgressConsumerEvent {

    private static final Logger log = LoggerFactory.getLogger(UpdateBookProgressConsumerEvent.class);
    private final UpdateBookProgressUseCase updateBookProgressUseCase;

    public UpdateBookProgressConsumerEvent(UpdateBookProgressUseCase updateBookProgressUseCase) {
        this.updateBookProgressUseCase = updateBookProgressUseCase;
    }

    @KafkaListener(
            topics = "updated-book-progress",
            groupId = "recommendation-service"
    )
    public void consume(
            UpdateBookProgressEvent event
    ) {

        log.info("Event received: Update book progress. UserId={}, BookId={}, Progress={}",
                event.userId(),
                event.bookId(),
                event.progress()
        );

        try {

            var command = UpdateBookProgressCommand.of(
                    event.userId(),
                    event.bookId(),
                    event.progress(),
                    event.oldPage(),
                    event.newPage()
            );

            updateBookProgressUseCase.execute(command);

            log.info("Book progress update event processed successfully. UserId={}, BookId={}",
                    event.userId(),
                    event.bookId()
            );

        } catch (Exception e) {
            log.error("Error processing book progress update event. UserId={}, BookId={}, Progress={}",
                    event.userId(),
                    event.bookId(),
                    event.progress(),
                    e
            );
            throw e;
        }

    }

}
