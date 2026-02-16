package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.DeleteBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.handler.book_feature.DeleteBookFeatureHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeleteBookConsumerEvent {

    private final DeleteBookFeatureHandler deleteBookFeatureHandler;

    public DeleteBookConsumerEvent(DeleteBookFeatureHandler deleteBookFeatureHandler) {
        this.deleteBookFeatureHandler = deleteBookFeatureHandler;
    }


    @KafkaListener(
            topics = "deleted-book",
            groupId = "recommendation-service"
    )
    public void listen(DeleteBookEvent deleteBookEvent){
        deleteBookFeatureHandler.execute(deleteBookEvent.bookId());
    }

}
