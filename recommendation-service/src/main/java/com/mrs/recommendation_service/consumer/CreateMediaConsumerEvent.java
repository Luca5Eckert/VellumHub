package com.mrs.recommendation_service.consumer;

import com.mrs.recommendation_service.event.CreateMediaEvent;
import com.mrs.recommendation_service.model.MediaFeature;
import com.mrs.recommendation_service.repository.MediaFeatureRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaConsumerEvent {

    private final MediaFeatureRepository mediaFeatureRepository;

    public CreateMediaConsumerEvent(MediaFeatureRepository mediaFeatureRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    @KafkaListener(topics = "create_media")
    public void listen(CreateMediaEvent createMediaEvent){
        MediaFeature mediaFeature = new MediaFeature(
                createMediaEvent.mediaId(),
                createMediaEvent.genres()
        );

        mediaFeatureRepository.save(mediaFeature);
    }

}
