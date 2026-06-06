package com.vellumhub.catalog_service.module.book.infrastructure.repository.genre;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JpaGenreRepository extends JpaRepository<Genre, Long> {
    Optional<Genre> findByName(String name);

    boolean existsByName(String name);
}
