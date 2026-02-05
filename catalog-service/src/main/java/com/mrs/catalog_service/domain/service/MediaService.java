package com.mrs.catalog_service.domain.service;

import com.mrs.catalog_service.application.dto.*;
import com.mrs.catalog_service.domain.handler.*;
import com.mrs.catalog_service.application.mapper.MediaMapper;
import com.mrs.catalog_service.domain.model.Media;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MediaService {

    private final CreateMediaHandler createMediaHandler;
    private final DeleteMediaHandler deleteMediaHandler;
    private final GetMediaHandler getMediaHandler;
    private final GetAllMediaHandler getAllMediaHandler;
    private final UpdateMediaHandler updateMediaHandler;
    private final GetMediaByIdsHandler getMediaByIdsHandler;

    private final MediaMapper mediaMapper;

    public MediaService(CreateMediaHandler createMediaHandler, DeleteMediaHandler deleteMediaHandler, GetMediaHandler getMediaHandler, GetAllMediaHandler getAllMediaHandler, UpdateMediaHandler updateMediaHandler, GetMediaByIdsHandler getMediaByIdsHandler, MediaMapper mediaMapper) {
        this.createMediaHandler = createMediaHandler;
        this.deleteMediaHandler = deleteMediaHandler;
        this.getMediaHandler = getMediaHandler;
        this.getAllMediaHandler = getAllMediaHandler;
        this.updateMediaHandler = updateMediaHandler;
        this.getMediaByIdsHandler = getMediaByIdsHandler;
        this.mediaMapper = mediaMapper;
    }

    public void create(CreateMediaRequest createMediaRequest) {
        Media media = Media.builder()
                .title( createMediaRequest.title() )
                .description( createMediaRequest.description() )
                .mediaType( createMediaRequest.mediaType() )
                .releaseYear( createMediaRequest.releaseYear() )
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

    public void update(UUID mediaId, UpdateMediaRequest updateMediaRequest) {
        updateMediaHandler.execute(mediaId, updateMediaRequest);
    }


    public List<MediaFeatureResponse> getByIds(List<UUID> mediaIds) {
        List<Media> mediaList = getMediaByIdsHandler.execute(mediaIds);

        return mediaList.stream().map(mediaMapper::toFeatureResponse).toList();
    }

}
