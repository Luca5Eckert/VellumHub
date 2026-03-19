package com.mrs.catalog_service.module.book.domain.port;

import com.mrs.catalog_service.module.book.domain.model.Genre;

import java.util.Optional;

public interface GenreRepository {
    Optional<Genre> findByName(String normalizedName);

    Genre save(Genre newGenre);

    boolean existsByName(String name);
}
