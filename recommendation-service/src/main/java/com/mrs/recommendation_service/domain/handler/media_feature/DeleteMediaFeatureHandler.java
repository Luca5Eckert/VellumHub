package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DeleteMediaFeatureHandler {

    private final MediaFeatureRepository mediaFeatureRepository;

    public DeleteMediaFeatureHandler(MediaFeatureRepository mediaFeatureRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    public void execute(UUID mediaId){
        mediaFeatureRepository.deleteById(mediaId);
    }

}
