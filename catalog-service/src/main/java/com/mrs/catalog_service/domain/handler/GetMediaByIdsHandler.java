package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class GetMediaByIdsHandler {

    private final MediaRepository mediaRepository;

    public GetMediaByIdsHandler(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    public List<Book> execute(List<UUID> uuids) {
        return mediaRepository.findAllById(uuids);
    }

}
