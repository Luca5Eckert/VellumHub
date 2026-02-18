package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.UpdateBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.UpdateMediaFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookConsumerEvent {

    private final UpdateMediaFeatureUseCase mediaFeatureHandler;

    public UpdateBookConsumerEvent(UpdateMediaFeatureUseCase mediaFeatureHandler) {
        this.mediaFeatureHandler = mediaFeatureHandler;
    }

    @KafkaListener(
            topics = "updated-book",
            groupId = "recommendation-service"
    )
    public void execute(UpdateBookEvent updateBookEvent){
        log.info("Event received: Book update. BookId={}, Genres={}",
                updateBookEvent.bookId(),
                updateBookEvent.genres());

        try {
            UpdateBookFeatureCommand mediaFeatureCommand = new UpdateBookFeatureCommand(
                    updateBookEvent.bookId(),
                    updateBookEvent.genres()
            );

            mediaFeatureHandler.execute(mediaFeatureCommand);

            log.info("Book update event processed successfully. BookId={}",
                    updateBookEvent.bookId());

        } catch (Exception e) {
            log.error("Error processing book update event. BookId={}, Genres={}",
                    updateBookEvent.bookId(),
                    updateBookEvent.genres(),
                    e);
            throw e;
        }
    }

}
