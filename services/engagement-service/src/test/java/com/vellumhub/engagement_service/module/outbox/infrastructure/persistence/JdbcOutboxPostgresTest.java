package com.vellumhub.engagement_service.module.outbox.infrastructure.persistence;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Instant;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@Tag("distributed")
class JdbcOutboxPostgresTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:15-alpine");
    static JdbcTemplate jdbc;
    static DriverManagerDataSource ds;
    static JdbcOutboxWriter writer;
    static JdbcOutboxStore store;
    @BeforeAll static void before() {
        ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
        writer=new JdbcOutboxWriter(jdbc);store=new JdbcOutboxStore(jdbc);
    }
    private OutboxMessage message(long ratingId,int stars,long revision) {
        var event=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),ratingId,
                UUID.randomUUID(),UUID.randomUUID(),stars,revision);
        return new OutboxMessage(event.eventId(),"RATING",Long.toString(ratingId),
                revision,"RatingCreatedEvent",KafkaTopics.ENGAGEMENT_RATINGS,event.userId().toString(),event);
    }
    @Test void rollbackAlsoRollsBackOutboxMessage() {
        var event=message(601L,5,0);
        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            jdbc.update("INSERT INTO rating(id,user_id,book_id,stars,version) VALUES(?,?,?,?,0)",
                    601L,UUID.randomUUID(),UUID.randomUUID(),5);
            writer.append(event);
            throw new IllegalArgumentException("forced rollback");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rating WHERE id=601",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE event_id=?",
                Integer.class,event.eventId())).isZero();
    }
    @Test void publisherRetriesWithStableEventAndRevisionFence() {
        var one=message(602L,4,0);var two=message(602L,5,1);
        writer.append(one);writer.append(two);
        var first=store.claimNext(Duration.ofSeconds(30)).orElseThrow();
        assertThat(first.eventId()).isEqualTo(one.eventId());
        // Later revisions must remain blocked until the first reaches PUBLISHED.
        assertThat(store.claimNext(Duration.ofSeconds(30))).isEmpty();
        store.failed(first.eventId(),first.claimToken(),"offline",Duration.ofSeconds(1));
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                String.class,one.eventId())).isEqualTo("PENDING");
        jdbc.update("UPDATE outbox_events SET next_attempt_at=now() WHERE event_id=?",one.eventId());
        var retry=store.claimNext(Duration.ofSeconds(30)).orElseThrow();
        assertThat(retry.eventId()).isEqualTo(one.eventId());
        store.published(retry.eventId(),retry.claimToken());
        var next=store.claimNext(Duration.ofSeconds(30)).orElseThrow();
        assertThat(next.eventId()).isEqualTo(two.eventId());
        store.published(next.eventId(),next.claimToken());
    }
    /** AFF-EVT-005: database failure while inserting Outbox cannot commit a rating. */
    @Test void outboxConstraintFailureRollsBackRating() {
        var existing = message(603L, 5, 0);
        writer.append(existing);
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO rating(id,user_id,book_id,stars,version) VALUES(?,?,?,?,0)",
                    603L, UUID.randomUUID(), UUID.randomUUID(), 5);
            writer.append(existing);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rating WHERE id=603",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_id=?",
                Integer.class, existing.eventId())).isEqualTo(1);
    }
    /** AFF-EVT-005: Reaction state and outbox intent share an atomic transaction. */
    @Test void outboxConstraintFailureRollsBackReactionMutation() {
        long reactionId = 899995L;
        UUID user = UUID.randomUUID(), book = UUID.randomUUID();
        var fact = new com.vellumhub.kafka.contracts.engagement.ReactionChangedEvent(
                UUID.randomUUID(), Instant.now(), reactionId, user, book,
                null, "POSITIVE", "POSITIVE", 0L);
        var duplicate = new OutboxMessage(fact.eventId(), "REACTION",
                Long.toString(reactionId), 0, "ReactionChangedEvent",
                KafkaTopics.ENGAGEMENT_REACTIONS, user.toString(), fact);
        writer.append(duplicate);

        var transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("""
                    INSERT INTO reactions(id,user_id,type_reaction) VALUES (?,?,?)
                    """, reactionId, user, "POSITIVE");
            writer.append(duplicate);
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM reactions WHERE id=?", Integer.class, reactionId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_id=?",
                Integer.class, duplicate.eventId())).isEqualTo(1);
    }

}

