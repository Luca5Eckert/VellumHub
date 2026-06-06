package com.vellumhub.catalog_service.share.config;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.infrastructure.repository.genre.JpaGenreRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class InitialGenreSeeder implements ApplicationRunner {

    static final List<String> INITIAL_GENRES = List.of(
            "Science Fiction",
            "Fantasy",
            "Horror",
            "Romance",
            "Thriller",
            "Biography",
            "History",
            "Self-Help",
            "Dystopian",
            "Adventure"
    );

    private final JpaGenreRepository genreRepository;
    private final List<String> initialGenres;

    @Autowired
    public InitialGenreSeeder(JpaGenreRepository genreRepository) {
        this(genreRepository, INITIAL_GENRES);
    }

    InitialGenreSeeder(JpaGenreRepository genreRepository, List<String> initialGenres) {
        this.genreRepository = genreRepository;
        this.initialGenres = initialGenres;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        initialGenres.stream()
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .filter(name -> !genreRepository.existsByName(name))
                .map(Genre::new)
                .forEach(genreRepository::save);
    }
}
