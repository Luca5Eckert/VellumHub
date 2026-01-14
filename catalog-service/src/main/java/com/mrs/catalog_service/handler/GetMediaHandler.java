package com.mrs.catalog_service.handler;

import com.mrs.catalog_service.exception.domain.media.MediaNotFoundException;
import com.mrs.catalog_service.model.Media;
import com.mrs.catalog_service.repository.MediaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetMediaHandler {

    private final MediaRepository mediaRepository;

    public GetMediaHandler(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public Media execute(UUID mediaId){
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId.toString()));
    }

}
