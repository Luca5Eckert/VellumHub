package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GetMediaHandler {

    private final MediaRepository mediaRepository;

    public GetMediaHandler(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public Book execute(UUID mediaId){
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException(mediaId.toString()));
    }

}
