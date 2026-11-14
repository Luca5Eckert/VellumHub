package com.vellumhub.recommendation_service.module.recommendation.infrastructure.persistence.repository;

import com.vellumhub.recommendation_service.module.recommendation.domain.model.Recommendation;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the real SQL count, not merely a Mockito expectation. The recommendation
 * IDs have already been ranked/paginated by PostgreSQL; this is page hydration only.
 */
@Tag("distributed-benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RecommendationBatchHydrationIT extends DistributedIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired RecommendationRepositoryJpa repository;
    @Autowired EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void reset() {
        jdbc.update("delete from recommendation_genres");
        jdbc.update("delete from user_profiles");
        jdbc.update("delete from recommendations");
        jdbc.update("delete from book_features");
    }

    @Test
    void hydratesTenBooksAndAllGenresWithExactlyOneSqlStatement() {
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            insert(id, "Book " + index);
            jdbc.update("insert into recommendation_genres (recommendation_book_id, genres) values (?, ?)",
                    id, "Fantasy");
            jdbc.update("insert into recommendation_genres (recommendation_book_id, genres) values (?, ?)",
                    id, "Adventure");
        }

        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Recommendation> books = repository.findPageWithGenres(ids);
        assertThat(books).hasSize(10);
        assertThat(books).extracting(Recommendation::getBookId)
                .containsExactlyInAnyOrderElementsOf(ids);
        books.forEach(book -> assertThat(book.getGenres())
                .containsExactlyInAnyOrder("Fantasy", "Adventure"));
        assertThat(stats.getPrepareStatementCount())
                .as("Ten books with lazy genres used to run 11 hydration SQL statements")
                .isEqualTo(1L);
    }

    @Test
    void leftJoinPreservesBooksWithNoGenresOrMissingIds() {
        UUID withGenres = UUID.randomUUID(), without = UUID.randomUUID();
        insert(withGenres, "With genres");
        insert(without, "Without genres");
        jdbc.update("insert into recommendation_genres (recommendation_book_id, genres) values (?, ?)",
                withGenres, "History");
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        List<Recommendation> books = repository.findPageWithGenres(
                List.of(without, UUID.randomUUID(), withGenres));
        assertThat(books).hasSize(2);
        assertThat(books).extracting(Recommendation::getBookId)
                .containsExactlyInAnyOrder(withGenres, without);
        assertThat(books.stream().filter(r -> r.getBookId().equals(without))
                .findFirst().orElseThrow().getGenres()).isEmpty();
        assertThat(stats.getPrepareStatementCount()).isEqualTo(1L);
    }

    private void insert(UUID id, String title) {
        jdbc.update("insert into recommendations (book_id, title, description, release_year, cover_url, author) values (?, ?, ?, ?, ?, ?)",
                id, title, "N+1 reproducible fixture", 2026, "https://example.test/books/" + id + ".jpg", "Test");
    }
}
