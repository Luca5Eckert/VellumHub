package com.mrs.recommendation_service.domain.handler;

import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaFeatureHandler {

    private final MediaFeatureRepository mediaFeatureRepository;

    public CreateMediaFeatureHandler(MediaFeatureRepository mediaFeatureRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    public void execute(MediaFeature mediaFeature){
        mediaFeatureRepository.save(mediaFeature);
    }

}
