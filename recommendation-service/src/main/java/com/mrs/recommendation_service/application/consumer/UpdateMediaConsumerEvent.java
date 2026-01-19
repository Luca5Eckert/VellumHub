package com.mrs.recommendation_service.application.consumer;

import com.mrs.recommendation_service.application.event.UpdateMediaEvent;
import com.mrs.recommendation_service.domain.command.UpdateMediaFeatureCommand;
import com.mrs.recommendation_service.domain.handler.media_feature.UpdateMediaFeatureHandler;
import org.springframework.kafka.annotation.KafkaListener;

public class UpdateMediaConsumerEvent {

    private final UpdateMediaFeatureHandler mediaFeatureHandler;

    public UpdateMediaConsumerEvent(UpdateMediaFeatureHandler mediaFeatureHandler) {
        this.mediaFeatureHandler = mediaFeatureHandler;
    }

    @KafkaListener(topics = "update-media", groupId = "recommendation-service")
    public void execute(UpdateMediaEvent updateMediaEvent){
        UpdateMediaFeatureCommand mediaFeatureCommand = new UpdateMediaFeatureCommand(
                updateMediaEvent.mediaId(),
                updateMediaEvent.genres()
        );

        mediaFeatureHandler.execute(mediaFeatureCommand);
    }

}
