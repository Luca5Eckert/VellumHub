package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.CreateBookEvent;
import com.mrs.recommendation_service.application.mapper.MediaFeatureMapper;
import com.mrs.recommendation_service.domain.handler.media_feature.CreateMediaFeatureHandler;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateBookConsumerEvent {

    private final CreateMediaFeatureHandler createMediaFeatureHandler;
    private final MediaFeatureMapper mapper;

    public CreateBookConsumerEvent(CreateMediaFeatureHandler createMediaFeatureHandler, MediaFeatureMapper mapper) {
        this.createMediaFeatureHandler = createMediaFeatureHandler;
        this.mapper = mapper;
    }


    @KafkaListener(
            topics = "created-book",
            groupId = "recommendation-service"
    )
    public void listen(CreateBookEvent createBookEvent){
        float[] genresVector = mapper.mapToFeatureVector(createBookEvent.genres());

        MediaFeature mediaFeature = new MediaFeature(
                createBookEvent.bookId(),
                genresVector
        );

        createMediaFeatureHandler.execute(mediaFeature);
    }

}
