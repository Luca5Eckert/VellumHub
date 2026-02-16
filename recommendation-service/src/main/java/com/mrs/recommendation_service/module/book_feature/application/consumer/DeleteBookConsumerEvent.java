package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.DeleteBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.DeleteBookFeatureUseCase;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeleteBookConsumerEvent {

    private final DeleteBookFeatureUseCase deleteBookFeatureUseCase;

    public DeleteBookConsumerEvent(DeleteBookFeatureUseCase deleteBookFeatureUseCase) {
        this.deleteBookFeatureUseCase = deleteBookFeatureUseCase;
    }


    @KafkaListener(
            topics = "deleted-book",
            groupId = "recommendation-service"
    )
    public void listen(DeleteBookEvent deleteBookEvent){
        deleteBookFeatureUseCase.execute(deleteBookEvent.bookId());
    }

}
