package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.DeleteMediaEvent;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeleteMediaConsumerEvent {

    private final MediaFeatureRepository mediaFeatureRepository;

    public DeleteMediaConsumerEvent(MediaFeatureRepository mediaFeatureRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    @KafkaListener(topics = "delete-media", groupId = "recommendation-service")
    public void listen(DeleteMediaEvent deleteMediaEvent){
        mediaFeatureRepository.deleteById(deleteMediaEvent.mediaId());
    }
}
