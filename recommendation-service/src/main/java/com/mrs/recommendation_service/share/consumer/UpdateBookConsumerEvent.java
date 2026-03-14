package com.mrs.recommendation_service.share.consumer;

import com.mrs.recommendation_service.share.event.UpdateBookEvent;
import com.mrs.recommendation_service.module.book_feature.application.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.application.use_case.UpdateMediaFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class UpdateBookConsumerEvent {

    private final UpdateMediaFeatureUseCase updateMediaFeatureUseCase;

    public UpdateBookConsumerEvent(UpdateMediaFeatureUseCase updateMediaFeatureUseCase) {
        this.updateMediaFeatureUseCase = updateMediaFeatureUseCase;
    }

    @KafkaListener(
            topics = "updated-book",
            groupId = "recommendation-service"
    )
    public void execute(UpdateBookEvent event){
        log.info("Event received: Book update. BookId={}, Genres={}",
                event.bookId(),
                event.genres());

        try {
            UpdateBookFeatureCommand mediaFeatureCommand = UpdateBookFeatureCommand.of(event.bookId(), event.genres());
            updateMediaFeatureUseCase.execute(mediaFeatureCommand);

            log.info("Book update event processed successfully. BookId={}",
                    event.bookId());

        } catch (Exception e) {
            log.error("Error processing book update event. BookId={}, Genres={}",
                    event.bookId(),
                    event.genres(),
                    e);
            throw e;
        }
    }

}
