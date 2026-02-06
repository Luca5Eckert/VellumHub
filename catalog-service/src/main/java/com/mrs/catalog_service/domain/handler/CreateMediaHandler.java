package com.mrs.catalog_service.domain.handler;

import com.mrs.catalog_service.domain.exception.InvalidMediaException;
import com.mrs.catalog_service.domain.model.Book;
import com.mrs.catalog_service.domain.port.EventProducer;
import com.mrs.catalog_service.domain.port.MediaRepository;
import com.mrs.catalog_service.domain.event.CreateMediaEvent;
import org.springframework.stereotype.Component;

@Component
public class CreateMediaHandler {

    private final MediaRepository mediaRepository;
    private final EventProducer<String, CreateMediaEvent> eventProducer;


    public CreateMediaHandler(MediaRepository mediaRepository, EventProducer<String, CreateMediaEvent> eventProducer) {
        this.mediaRepository = mediaRepository;
        this.eventProducer = eventProducer;
    }

    public void handler(Book media){
        if(media == null) throw new InvalidMediaException();

        mediaRepository.save(media);

        CreateMediaEvent createMediaEvent = new CreateMediaEvent(
                media.getId(),
                media.getGenres()
        );

        eventProducer.send("create-media", createMediaEvent.mediaId().toString(), createMediaEvent);
    }

}
