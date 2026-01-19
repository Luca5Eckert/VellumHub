package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.CreateMediaEvent;
import com.mrs.recommendation_service.domain.handler.media_feature.CreateMediaFeatureHandler;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaConsumerEvent {

    private final CreateMediaFeatureHandler createMediaFeatureHandler;

    public CreateMediaConsumerEvent(CreateMediaFeatureHandler createMediaFeatureHandler) {
        this.createMediaFeatureHandler = createMediaFeatureHandler;
    }


    @KafkaListener(topics = "create-media", groupId = "recommendation-service")
    public void listen(CreateMediaEvent createMediaEvent){
        MediaFeature mediaFeature = new MediaFeature(
                createMediaEvent.mediaId(),
                createMediaEvent.genres()
        );

        createMediaFeatureHandler.execute(mediaFeature);
    }

}
