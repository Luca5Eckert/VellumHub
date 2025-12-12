package com.mrs.catalog_service.service;

import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.handler.CreateMediaHandler;
import com.mrs.catalog_service.model.Media;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class MediaService {

    private final CreateMediaHandler createMediaHandler;

    public MediaService(CreateMediaHandler createMediaHandler) {
        this.createMediaHandler = createMediaHandler;
    }

    public void create(CreateMediaRequest createMediaRequest) {
        Media media = new Media.Builder()
                .title( createMediaRequest.title() )
                .description( createMediaRequest.description() )
                .mediaType( createMediaRequest.mediaType() )
                .releaseYear( createMediaRequest.releaseYear() )
                .createAt( Instant.now() )
                .updateAt( Instant.now() )
                .genres( createMediaRequest.genres() )
                .build();

        createMediaHandler.handler(media);
    }


}
