package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.PageMedia;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class GetAllMediaHandler {

    private final MediaRepository mediaRepository;

    public GetAllMediaHandler(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public Page<Media> execute(PageMedia pageMedia){
        PageRequest pageRequest = PageRequest.of(
                pageMedia.pageNumber(),
                pageMedia.pageSize()
        );

        return mediaRepository.findAll(pageRequest);
    }

}
