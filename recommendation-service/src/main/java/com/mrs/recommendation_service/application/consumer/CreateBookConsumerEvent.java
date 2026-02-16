package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.CreateBookEvent;
import com.mrs.recommendation_service.application.mapper.MediaFeatureMapper;
import com.mrs.recommendation_service.domain.handler.book_feature.CreateBookFeatureHandler;
import com.mrs.recommendation_service.domain.model.BookFeature;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateBookConsumerEvent {

    private final CreateBookFeatureHandler createBookFeatureHandler;
    private final MediaFeatureMapper mapper;

    public CreateBookConsumerEvent(CreateBookFeatureHandler createBookFeatureHandler, MediaFeatureMapper mapper) {
        this.createBookFeatureHandler = createBookFeatureHandler;
        this.mapper = mapper;
    }


    @KafkaListener(
            topics = "created-book",
            groupId = "recommendation-service"
    )
    public void listen(CreateBookEvent createBookEvent){
        float[] genresVector = mapper.mapToFeatureVector(createBookEvent.genres());

        BookFeature bookFeature = new BookFeature(
                createBookEvent.bookId(),
                genresVector
        );

        createBookFeatureHandler.execute(bookFeature);
    }

}
