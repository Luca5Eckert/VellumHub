package com.mrs.catalog_service.infrastructure.persistence.repository;

import com.mrs.catalog_service.domain.model.Media;
import com.mrs.catalog_service.domain.port.MediaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

public class MediaRepositoryAdapter implements MediaRepository {

    private final JpaMediaRepository mediaRepositoryJpa;

    public MediaRepositoryAdapter(JpaMediaRepository mediaRepositoryJpa) {
        this.mediaRepositoryJpa = mediaRepositoryJpa;
    }

    @Override
    public void save(Media media) {
        mediaRepositoryJpa.save(media);
    }

    @Override
    public boolean existsById(UUID mediaId) {
        return mediaRepositoryJpa.existsById(mediaId);
    }

    @Override
    public Page<Media> findAll(PageRequest pageRequest) {
        return mediaRepositoryJpa.findAll(pageRequest);
    }

    @Override
    public Optional<Media> findById(UUID mediaId) {
        return mediaRepositoryJpa.findById(mediaId);
    }

}
