package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.repository.MediaRepository;
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
