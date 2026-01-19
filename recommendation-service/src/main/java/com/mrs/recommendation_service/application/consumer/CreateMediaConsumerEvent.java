package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.CreateMediaEvent;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaConsumerEvent {

    private final MediaFeatureRepository mediaFeatureRepository;

    public CreateMediaConsumerEvent(MediaFeatureRepository mediaFeatureRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    @KafkaListener(topics = "create-media", groupId = "recommendation-service")
    public void listen(CreateMediaEvent createMediaEvent){
        MediaFeature mediaFeature = new MediaFeature(
                createMediaEvent.mediaId(),
                createMediaEvent.genres()
        );

        mediaFeatureRepository.save(mediaFeature);
    }

}
