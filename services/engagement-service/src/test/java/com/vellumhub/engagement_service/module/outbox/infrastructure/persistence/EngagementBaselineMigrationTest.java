package com.vellumhub.engagement_service.module.outbox.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Tag("distributed")
@Testcontainers
class EngagementBaselineMigrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:15-alpine");

    @Test void existingRatingAndReactionBecomeCheckpointsWithoutSyntheticUserActions() {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("7").load().migrate();
        var jdbc=new JdbcTemplate(ds);
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        jdbc.update("INSERT INTO book_snapshot(book_id) VALUES(?)",book);
        jdbc.update("INSERT INTO rating(id,user_id,book_id,stars) VALUES(?,?,?,?)",9401L,user,book,5);
        jdbc.update("""
                INSERT INTO reactions(id,user_id,book_snapshot_id,type_reaction,created_at,updated_at)
                VALUES(?,?,?,'POSITIVE',now(),now())
                """,9402L,user,book);

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("""
              SELECT count(*) FROM outbox_events
              WHERE event_type IN ('RatingBaselineEvent','ReactionBaselineEvent')
              """,Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
              SELECT aggregate_version FROM outbox_events
              WHERE aggregate_type='RATING' AND aggregate_id='9401'
              """,Long.class)).isZero();
        assertThat(jdbc.queryForObject("""
              SELECT topic FROM outbox_events
              WHERE aggregate_type='REACTION' AND aggregate_id='9402'
              """,String.class)).isEqualTo("engagement.reactions.v1");
        assertThat(jdbc.queryForObject("""
              SELECT payload->>'revision' FROM outbox_events
              WHERE aggregate_type='RATING' AND aggregate_id='9401'
              """,String.class)).isEqualTo("0");
        assertThat(jdbc.queryForObject("""
              SELECT status FROM outbox_events
              WHERE aggregate_type='RATING' AND aggregate_id='9401'
              """,String.class)).isEqualTo("PENDING");
    }
}
