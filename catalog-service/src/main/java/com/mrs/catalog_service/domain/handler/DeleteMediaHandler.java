package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.event.DeleteMediaEvent;
import com.mrs.catalog_service.domain.exception.MediaNotExistException;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteMediaHandler {

    private final MediaRepository mediaRepository;
    private final EventProducer<String, DeleteMediaEvent> eventProducer;

    public DeleteMediaHandler(MediaRepository mediaRepository, EventProducer<String, DeleteMediaEvent> eventProducer) {
        this.mediaRepository = mediaRepository;
        this.eventProducer = eventProducer;
    }

    @Transactional
    public void execute(UUID mediaId){
        if(!mediaRepository.existsById(mediaId)) throw new MediaNotExistException(mediaId.toString());

        mediaRepository.deleteById(mediaId);

        DeleteMediaEvent deleteMediaEvent = new DeleteMediaEvent(mediaId);

        eventProducer.send("delete-media", mediaId.toString(), deleteMediaEvent);
    }



}
