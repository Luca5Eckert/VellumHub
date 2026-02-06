package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.DeleteBookEvent;
import com.mrs.recommendation_service.domain.handler.media_feature.DeleteMediaFeatureHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeleteBookConsumerEvent {

    private final DeleteMediaFeatureHandler deleteMediaFeatureHandler;

    public DeleteBookConsumerEvent(DeleteMediaFeatureHandler deleteMediaFeatureHandler) {
        this.deleteMediaFeatureHandler = deleteMediaFeatureHandler;
    }


    @KafkaListener(
            topics = "deleted-book",
            groupId = "recommendation-service"
    )
    public void listen(DeleteBookEvent deleteBookEvent){
        deleteMediaFeatureHandler.execute(deleteBookEvent.bookId());
    }

}
