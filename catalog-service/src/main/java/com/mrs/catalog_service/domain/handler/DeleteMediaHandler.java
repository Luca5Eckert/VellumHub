package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.DeleteMediaEvent;
import com.mrs.catalog_service.domain.exception.MediaNotExistException;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteMediaHandler {

    private final MediaRepository mediaRepository;
    private final KafkaTemplate<String, DeleteMediaEvent> kafka;

    public DeleteMediaHandler(MediaRepository mediaRepository, KafkaTemplate<String, DeleteMediaEvent> kafka) {
        this.mediaRepository = mediaRepository;
        this.kafka = kafka;
    }

    @Transactional
    public void execute(UUID mediaId){
        if(!mediaRepository.existsById(mediaId)) throw new MediaNotExistException(mediaId.toString());

        mediaRepository.deleteById(mediaId);

        DeleteMediaEvent deleteMediaEvent = new DeleteMediaEvent(mediaId);

        kafka.send("delete-media", mediaId.toString(), deleteMediaEvent);
    }



}
