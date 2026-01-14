package com.mrs.catalog_service.handler;

import com.mrs.catalog_service.exception.domain.media.InvalidMediaException;
import com.mrs.catalog_service.model.Media;
import com.mrs.catalog_service.repository.MediaRepository;
import com.mrs.catalog_service.event.CreateMediaEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaHandler {

    private final MediaRepository mediaRepository;

    private final  KafkaTemplate<String, CreateMediaEvent> kafka;


    public CreateMediaHandler(MediaRepository mediaRepository, KafkaTemplate<String, CreateMediaEvent> kafka) {
        this.mediaRepository = mediaRepository;
        this.kafka = kafka;
    }

    public void handler(Media media){
        if(media == null) throw new InvalidMediaException();

        mediaRepository.save(media);

        CreateMediaEvent createMediaEvent = new CreateMediaEvent(
                media.getId(),
                media.getGenres().stream().map(Enum::toString).toList()
        );

        kafka.send("create-media", createMediaEvent.mediaId().toString(), createMediaEvent);
    }

}
