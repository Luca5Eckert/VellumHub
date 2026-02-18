package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.DeleteBookEvent;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.DeleteBookFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
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
        log.info("Event received: Book deletion. BookId={}",
                deleteBookEvent.bookId());

        try {
            deleteBookFeatureUseCase.execute(deleteBookEvent.bookId());

            log.info("Book deletion event processed successfully. BookId={}",
                    deleteBookEvent.bookId());

        } catch (Exception e) {
            log.error("Error processing book deletion event. BookId={}",
                    deleteBookEvent.bookId(),
                    e);
            throw e;
        }
    }

}
