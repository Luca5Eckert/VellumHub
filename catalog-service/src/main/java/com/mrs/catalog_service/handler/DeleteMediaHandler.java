package com.mrs.catalog_service.handler;

import com.mrs.catalog_service.repository.MediaRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DeleteMediaHandler {

    private final MediaRepository mediaRepository;
    private final KafkaTemplate<String, UUID> kafka;

    public DeleteMediaHandler(MediaRepository mediaRepository, KafkaTemplate<String, UUID> kafka) {
        this.mediaRepository = mediaRepository;
        this.kafka = kafka;
    }

    @Transactional
    public void execute(UUID mediaId){
        if(!mediaRepository.existsById(mediaId)) throw new IllegalArgumentException("Media not exist");

        mediaRepository.deleteById(mediaId);

        kafka.send("delete_media", mediaId.toString(), mediaId);
    }



}
