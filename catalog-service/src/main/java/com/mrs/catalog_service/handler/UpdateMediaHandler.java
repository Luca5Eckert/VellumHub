package com.mrs.catalog_service.handler;

import com.mrs.catalog_service.dto.UpdateMediaRequest;
import com.mrs.catalog_service.exception.domain.media.MediaNotFoundException;
import com.mrs.catalog_service.model.Media;
import com.mrs.catalog_service.repository.MediaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class UpdateMediaHandler {

    private final MediaRepository mediaRepository;

    public UpdateMediaHandler(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public void execute(UUID mediaId, UpdateMediaRequest request) {
        Objects.requireNonNull(request, "UpdateMediaRequest must not be null");

        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(() -> {
                    log.error("Media not found for ID: {}", mediaId);
                    return new MediaNotFoundException();
                });

        media.update(
                request.title(),
                request.description(),
                request.coverUrl(),
                request.releaseYear(),
                request.genres()
        );

        mediaRepository.save(media);

        log.info("Media with ID: {} updated successfully", mediaId);
    }
}