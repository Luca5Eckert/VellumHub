package com.mrs.recommendation_service.share.consumer;

import com.mrs.recommendation_service.share.event.CreateBookEvent;
import com.mrs.recommendation_service.module.book_feature.application.mapper.BookFeatureMapper;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.mrs.recommendation_service.module.book_feature.application.use_case.CreateBookFeatureUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CreateBookConsumerEvent {

    private final CreateBookFeatureUseCase createBookFeatureUseCase;

    public CreateBookConsumerEvent(CreateBookFeatureUseCase createBookFeatureUseCase, BookFeatureMapper mapper) {
        this.createBookFeatureUseCase = createBookFeatureUseCase;
        this.mapper = mapper;
    }


    @KafkaListener(
            topics = "created-book",
            groupId = "recommendation-service"
    )
    public void listen(CreateBookEvent createBookEvent){
        log.info("Event received: Book creation. BookId={}, Genres={}",
                createBookEvent.bookId(),
                createBookEvent.genres());

        try {
            createBookFeatureUseCase.execute(createBookEvent);

            log.info("Book creation event processed successfully. BookId={}",
                    createBookEvent.bookId());

        } catch (Exception e) {
            log.error("Error processing book creation event. BookId={}, Genres={}",
                    createBookEvent.bookId(),
                    createBookEvent.genres(),
                    e);
            throw e;
        }
    }

}
