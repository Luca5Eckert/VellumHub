package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.UpdateBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.command.UpdateBookFeatureCommand;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.UpdateMediaFeatureUseCase;
import org.springframework.kafka.annotation.KafkaListener;

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
        UpdateBookFeatureCommand mediaFeatureCommand = new UpdateBookFeatureCommand(
                updateBookEvent.bookId(),
                updateBookEvent.genres()
        );

        mediaFeatureHandler.execute(mediaFeatureCommand);
    }

}
