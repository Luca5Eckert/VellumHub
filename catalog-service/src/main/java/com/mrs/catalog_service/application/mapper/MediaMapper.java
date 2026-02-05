package com.mrs.catalog_service.application.mapper;

import com.mrs.catalog_service.application.dto.GetMediaResponse;
import com.mrs.catalog_service.application.dto.MediaFeatureResponse;
import com.mrs.catalog_service.domain.model.Media;
import org.springframework.stereotype.Component;

@Component
public class MediaMapper {

    public GetMediaResponse toGetResponse(Media media){
        return new GetMediaResponse(
                media.getId(),
                media.getTitle(),
                media.getDescription(),
                media.getReleaseYear(),
                media.getMediaType(),
                media.getCoverUrl(),
                media.getGenres(),
                media.getCreatedAt(),
                media.getUpdatedAt()
        );

    }

    public MediaFeatureResponse toFeatureResponse(Media media) {
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
