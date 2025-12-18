package com.mrs.catalog_service.service;

import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.handler.CreateMediaHandler;
import com.mrs.catalog_service.handler.DeleteMediaHandler;
import com.mrs.catalog_service.model.Media;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class MediaService {

    private final CreateMediaHandler createMediaHandler;
    private final DeleteMediaHandler deleteMediaHandler;

    public MediaService(CreateMediaHandler createMediaHandler, DeleteMediaHandler deleteMediaHandler) {
        this.createMediaHandler = createMediaHandler;
        this.deleteMediaHandler = deleteMediaHandler;
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

    public void delete(UUID mediaId){
        deleteMediaHandler.execute(mediaId);
    }


}
