package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.UpdateMediaRequest;
import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.repository.MediaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class UpdateMediaHandler {

    private final MediaRepository mediaRepository;
    private final KafkaTemplate<String, UpdateMediaEvent> kafka;

    public UpdateMediaHandler(MediaRepository mediaRepository) {
        this.mediaRepository = mediaRepository;
    }

    @Transactional
    public void execute(UUID mediaId, UpdateMediaRequest request) {
        Objects.requireNonNull(request, "UpdateMediaRequest must not be null");

        Media media = mediaRepository.findById(mediaId)
                .orElseThrow(MediaNotFoundException::new);

        media.update(
                request.title(),
                request.description(),
                request.coverUrl(),
                request.releaseYear(),
                request.genres()
        );

        mediaRepository.save(media);
    }

}