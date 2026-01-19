package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.application.dto.UpdateMediaRequest;
import com.mrs.catalog_service.domain.event.UpdateMediaEvent;
import com.mrs.catalog_service.domain.exception.MediaNotFoundException;
import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.repository.MediaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Component
public class UpdateMediaHandler {

    private final MediaRepository mediaRepository;
    private final EventProducer<String, UpdateMediaEvent> eventProducer;

    public UpdateMediaHandler(MediaRepository mediaRepository, EventProducer<String, UpdateMediaEvent> eventProducer) {
        this.mediaRepository = mediaRepository;
        this.eventProducer = eventProducer;
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

        if(request.genres() == null) return;

        UpdateMediaEvent updateMediaEvent = new UpdateMediaEvent(
                media.getId().toString(),
                request.genres().stream().map(Objects::toString).toList()
        );

        eventProducer.send("update-media", media.getId().toString(), updateMediaEvent);

    }


}