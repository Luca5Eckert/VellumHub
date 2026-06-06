package com.vellumhub.catalog_service.share.config;

import com.vellumhub.catalog_service.module.book.domain.model.Genre;
import com.vellumhub.catalog_service.module.book.infrastructure.repository.genre.JpaGenreRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialGenreSeederTest {

    private final JpaGenreRepository genreRepository = org.mockito.Mockito.mock(JpaGenreRepository.class);
    private final InitialGenreSeeder seeder = new InitialGenreSeeder(genreRepository);

    @Test
    void shouldDefineTenInitialGenres() {
        assertThat(InitialGenreSeeder.INITIAL_GENRES)
                .containsExactly(
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
    }

    @Test
    void shouldCreateOnlyMissingGenres() throws Exception {
        when(genreRepository.existsByName("Fantasy")).thenReturn(true);
        when(genreRepository.existsByName("Adventure")).thenReturn(false);
        InitialGenreSeeder seeder = new InitialGenreSeeder(genreRepository, java.util.List.of("Fantasy", "Adventure"));

        seeder.run(null);

        verify(genreRepository, never()).save(new Genre("Fantasy"));
        verify(genreRepository).save(new Genre("Adventure"));
    }

    @Test
    void shouldSkipBlankGenreNames() throws Exception {
        InitialGenreSeeder seeder = new InitialGenreSeeder(genreRepository, java.util.List.of(" ", ""));

        seeder.run(null);

        verify(genreRepository, never()).existsByName(any());
        verify(genreRepository, never()).save(any());
    }
}
