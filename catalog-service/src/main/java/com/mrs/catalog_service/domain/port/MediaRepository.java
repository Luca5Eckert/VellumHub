package com.mrs.catalog_service.domain.port;

import com.mrs.catalog_service.domain.model.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRepository {
    void save(Book media);

    boolean existsById(UUID mediaId);

    Page<Book> findAll(PageRequest pageRequest);

    Optional<Book> findById(UUID mediaId);

    void deleteById(UUID mediaId);

    List<Book> findAllById(List<UUID> uuids);
}
