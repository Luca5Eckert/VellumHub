package com.vellumhub.recommendation_service.module.user_profile.presentation.consumer;

import com.vellumhub.recommendation_service.module.user_profile.application.command.UpdateBookProgressCommand;
import com.vellumhub.recommendation_service.module.user_profile.application.use_case.UpdateBookProgressUseCase;
import com.vellumhub.recommendation_service.module.user_profile.presentation.event.UpdateBookProgressEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateBookProgressConsumerEvent {

    private final UpdateBookProgressUseCase updateBookProgressUseCase;

    public CreateBookProgressConsumerEvent(UpdateBookProgressUseCase updateBookProgressUseCase) {
        this.updateBookProgressUseCase = updateBookProgressUseCase;
    }

    @KafkaListener(
            topics = "created-reading-progress",
            groupId = "recommendation-service"
    )
    public void consume(
            UpdateBookProgressEvent event
    ) {

        log.info("Event received: Create book progress. UserId={}, BookId={}, Progress={}",
                event.userId(),
                event.bookId(),
                event.progress()
        );

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
    }

}
