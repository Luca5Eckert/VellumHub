package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.DeleteMediaEvent;
import com.mrs.recommendation_service.domain.handler.media_feature.DeleteMediaFeatureHandler;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DeleteMediaConsumerEvent {

    private final DeleteMediaFeatureHandler deleteMediaFeatureHandler;

    public DeleteMediaConsumerEvent(DeleteMediaFeatureHandler deleteMediaFeatureHandler) {
        this.deleteMediaFeatureHandler = deleteMediaFeatureHandler;
    }


    @KafkaListener(topics = "delete-media", groupId = "recommendation-service")
    public void listen(DeleteMediaEvent deleteMediaEvent){
        deleteMediaFeatureHandler.execute(deleteMediaEvent.mediaId());
    }

}
