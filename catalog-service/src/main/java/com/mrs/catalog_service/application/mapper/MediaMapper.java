package com.mrs.catalog_service.application.mapper;

import com.mrs.catalog_service.application.dto.GetMediaResponse;
import com.mrs.catalog_service.application.dto.MediaFeatureResponse;
import com.mrs.catalog_service.domain.model.Book;
import org.springframework.stereotype.Component;

@Component
public class MediaMapper {

    public GetMediaResponse toGetResponse(Book media){
        return new GetMediaResponse(
                media.getId(),
                media.getTitle(),
                media.getDescription(),
                media.getReleaseYear(),
                media.getCoverUrl(),
                media.getAuthor(),
                media.getIsbn(),
                media.getPageCount(),
                media.getPublisher(),
                media.getGenres(),
                media.getCreatedAt(),
                media.getUpdatedAt()
        );

    }

    public MediaFeatureResponse toFeatureResponse(Book media) {
        return new MediaFeatureResponse(
                media.getId(),
                media.getTitle(),
                media.getDescription(),
                media.getReleaseYear(),
                media.getCoverUrl(),
                media.getGenres(),
                media.getCreatedAt(),
                media.getUpdatedAt()
        );
    }

}
