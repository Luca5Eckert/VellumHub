package com.mrs.catalog_service.service;

import com.mrs.catalog_service.dto.CreateMediaRequest;
import com.mrs.catalog_service.dto.GetMediaResponse;
import com.mrs.catalog_service.dto.PageMedia;
import com.mrs.catalog_service.handler.CreateMediaHandler;
import com.mrs.catalog_service.handler.DeleteMediaHandler;
import com.mrs.catalog_service.handler.GetAllMediaHandler;
import com.mrs.catalog_service.handler.GetMediaHandler;
import com.mrs.catalog_service.mapper.MediaMapper;
import com.mrs.catalog_service.model.Media;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private final CreateMediaHandler createMediaHandler;
    private final DeleteMediaHandler deleteMediaHandler;
    private final GetMediaHandler getMediaHandler;
    private final GetAllMediaHandler getAllMediaHandler;

    private final MediaMapper mediaMapper;

    public MediaService(CreateMediaHandler createMediaHandler, DeleteMediaHandler deleteMediaHandler, GetMediaHandler getMediaHandler, GetAllMediaHandler getAllMediaHandler, MediaMapper mediaMapper) {
        this.createMediaHandler = createMediaHandler;
        this.deleteMediaHandler = deleteMediaHandler;
        this.getMediaHandler = getMediaHandler;
        this.getAllMediaHandler = getAllMediaHandler;
        this.mediaMapper = mediaMapper;
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

    public GetMediaResponse get(UUID mediaId){
        Media media = getMediaHandler.execute(mediaId);

        return mediaMapper.toGetResponse(media);
    }

    public List<GetMediaResponse> getAll(int pageNumber, int pageSize){
        PageMedia pageMedia = new PageMedia(pageSize, pageNumber);

        Page<Media> mediaPage = getAllMediaHandler.execute(pageMedia);

        return mediaPage.stream().map(mediaMapper::toGetResponse).toList();
    }


}
