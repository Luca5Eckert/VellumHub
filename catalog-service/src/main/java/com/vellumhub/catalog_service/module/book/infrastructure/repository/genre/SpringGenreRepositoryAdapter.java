package com.mrs.catalog_service.module.book.infrastructure.repository.genre;

import com.mrs.catalog_service.module.book.domain.model.Genre;
import com.mrs.catalog_service.module.book.domain.port.GenreRepository;

import java.util.Optional;

public class SpringGenreRepositoryAdapter implements GenreRepository {

    private final JpaGenreRepository jpaGenreRepository;

    public SpringGenreRepositoryAdapter(JpaGenreRepository jpaGenreRepository) {
        this.jpaGenreRepository = jpaGenreRepository;
    }

    @Override
    public Optional<Genre> findByName(String normalizedName) {
        return jpaGenreRepository.findByName(normalizedName);
    }

    @Override
    public Genre save(Genre newGenre) {
        return jpaGenreRepository.save(newGenre);
    }

    @Override
    public boolean existsByName(String name) {
        return jpaGenreRepository.existsByName(name);
    }
}
