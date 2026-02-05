package com.mrs.recommendation_service.domain.handler.media_feature;

import com.mrs.recommendation_service.application.mapper.MediaFeatureMapper;
import com.mrs.recommendation_service.domain.command.UpdateMediaFeatureCommand;
import com.mrs.recommendation_service.domain.exception.media_feature.MediaFeatureNotFoundException;
import com.mrs.recommendation_service.domain.model.MediaFeature;
import com.mrs.recommendation_service.domain.port.MediaFeatureRepository;
import org.springframework.stereotype.Component;

@Component
public class UpdateMediaFeatureHandler {

    private final MediaFeatureRepository mediaFeatureRepository;

    public UpdateMediaFeatureHandler(MediaFeatureRepository mediaFeatureRepository) {
        this.mediaFeatureRepository = mediaFeatureRepository;
    }

    public void execute(UpdateMediaFeatureCommand command){
        MediaFeature mediaFeature = mediaFeatureRepository.findById(command.mediaId())
                .orElseThrow(MediaFeatureNotFoundException::new);

        mediaFeature.update(command.genres());

        mediaFeatureRepository.save(mediaFeature);
    }

}
