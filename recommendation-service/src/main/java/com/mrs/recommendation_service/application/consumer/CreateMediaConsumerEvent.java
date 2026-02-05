package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.CreateMediaEvent;
import com.mrs.recommendation_service.application.mapper.MediaFeatureMapper;
import com.mrs.recommendation_service.domain.handler.media_feature.CreateMediaFeatureHandler;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaConsumerEvent {

    private final CreateMediaFeatureHandler createMediaFeatureHandler;
    private final MediaFeatureMapper mapper;

    public CreateMediaConsumerEvent(CreateMediaFeatureHandler createMediaFeatureHandler, MediaFeatureMapper mapper) {
        this.createMediaFeatureHandler = createMediaFeatureHandler;
        this.mapper = mapper;
    }


    @KafkaListener(topics = "create-media", groupId = "recommendation-service")
    public void listen(CreateMediaEvent createMediaEvent){
        float[] genresVector = mapper.mapToFeatureVector(createMediaEvent.genres());

        MediaFeature mediaFeature = new MediaFeature(
                createMediaEvent.mediaId(),
                genresVector
        );

        createMediaFeatureHandler.execute(mediaFeature);
    }

}
