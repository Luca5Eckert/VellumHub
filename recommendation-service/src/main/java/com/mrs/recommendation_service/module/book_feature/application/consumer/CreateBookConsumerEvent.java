package com.mrs.recommendation_service.module.book_feature.application.consumer;

import com.mrs.recommendation_service.module.book_feature.application.event.CreateBookEvent;
import com.mrs.recommendation_service.module.book_feature.application.mapper.MediaFeatureMapper;
import com.mrs.recommendation_service.module.book_feature.domain.use_case.CreateBookFeatureUseCase;
import com.mrs.recommendation_service.module.book_feature.domain.model.BookFeature;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateBookConsumerEvent {

    private final CreateBookFeatureUseCase createBookFeatureUseCase;
    private final MediaFeatureMapper mapper;

    public CreateBookConsumerEvent(CreateBookFeatureUseCase createBookFeatureUseCase, MediaFeatureMapper mapper) {
        this.createBookFeatureUseCase = createBookFeatureUseCase;
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

        createBookFeatureUseCase.execute(bookFeature);
    }

}
