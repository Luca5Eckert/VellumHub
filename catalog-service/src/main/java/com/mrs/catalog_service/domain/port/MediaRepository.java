package com.mrs.catalog_service.domain.port;

import com.mrs.catalog_service.domain.model.Media;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {
    void save(Media media);

    boolean existsById(UUID mediaId);

    Page<Media> findAll(PageRequest pageRequest);

    Optional<Media> findById(UUID mediaId);

    void deleteById(UUID mediaId);
}
