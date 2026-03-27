package com.vellumhub.recommendation_service.share.consumer;

import com.vellumhub.recommendation_service.module.recommendation.application.command.DeleteRecommendationCommand;
import com.vellumhub.recommendation_service.module.recommendation.application.use_case.DeleteRecommendationUseCase;
import com.vellumhub.recommendation_service.share.event.DeleteBookEvent;
import com.vellumhub.recommendation_service.module.book_feature.application.use_case.DeleteBookFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class DeleteBookConsumerEvent {

    private final DeleteBookFeatureUseCase deleteBookFeatureUseCase;
    private final DeleteRecommendationUseCase deleteRecommendationUseCase;

    public DeleteBookConsumerEvent(DeleteBookFeatureUseCase deleteBookFeatureUseCase, DeleteRecommendationUseCase deleteRecommendationUseCase) {
        this.deleteBookFeatureUseCase = deleteBookFeatureUseCase;
        this.deleteRecommendationUseCase = deleteRecommendationUseCase;
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

            var command = DeleteRecommendationCommand.of(deleteBookEvent.bookId());
            deleteRecommendationUseCase.execute(command);

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
