package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.UpdateBookEvent;
import com.mrs.recommendation_service.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.domain.handler.book_feature.UpdateMediaFeatureHandler;
import org.springframework.kafka.annotation.KafkaListener;

public class UpdateBookConsumerEvent {

    private final UpdateMediaFeatureHandler mediaFeatureHandler;

    public UpdateBookConsumerEvent(UpdateMediaFeatureHandler mediaFeatureHandler) {
        this.mediaFeatureHandler = mediaFeatureHandler;
    }

    @KafkaListener(
            topics = "updated-book",
            groupId = "recommendation-service"
    )
    public void execute(UpdateBookEvent updateBookEvent){
        UpdateBookFeatureCommand mediaFeatureCommand = new UpdateBookFeatureCommand(
                updateBookEvent.bookId(),
                updateBookEvent.genres()
        );

        mediaFeatureHandler.execute(mediaFeatureCommand);
    }

}
