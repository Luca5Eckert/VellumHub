package com.vellumhub.catalog_service.module.outbox.infrastructure.persistence;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@Tag("distributed")
class JdbcOutboxStoreDistributedTest {
    @Container static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:15-alpine");
    static DriverManagerDataSource dataSource;
    static JdbcTemplate jdbc;
    static JdbcOutboxStore store;
    static JdbcOutboxWriter writer;

    @BeforeAll static void setup() {
        dataSource = new DriverManagerDataSource(PG.getJdbcUrl(),PG.getUsername(),PG.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        store = new JdbcOutboxStore(jdbc);
        writer = new JdbcOutboxWriter(jdbc,JsonMapper.builder().findAndAddModules().build());
    }
    private OutboxMessage event(UUID aggregate, long revision) {
        var e = new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),aggregate,
                UUID.randomUUID(),UUID.randomUUID(),"READING",0,10,(long)revision,
                revision==0?ReadingProgressChangedEvent.Kind.CREATED:ReadingProgressChangedEvent.Kind.UPDATED);
        return OutboxMessage.readingProgress(e);
    }
    @Test void claimEnforcesRevisionOrderingAndRecovery() {
        UUID aggregate = UUID.randomUUID();
        writer.append(event(aggregate,0));
        writer.append(event(aggregate,1));
        var first = store.claimNext(Duration.ofMinutes(2)).orElseThrow();
        assertThat(store.claimNext(Duration.ofMinutes(2))).isEmpty();
        store.failed(first.eventId(),first.claimToken(),"offline",Duration.ofSeconds(3));
        assertThat(store.claimNext(Duration.ofMinutes(2))).isEmpty();
        jdbc.update("UPDATE outbox_events SET next_attempt_at=now()-interval '1 second' WHERE event_id=?",
                first.eventId());
        var recovered=store.claimNext(Duration.ofMinutes(2)).orElseThrow();
        assertThat(recovered.eventId()).isEqualTo(first.eventId());
        store.published(recovered.eventId(),recovered.claimToken());
        var second=store.claimNext(Duration.ofMinutes(2)).orElseThrow();
        assertThat(second.eventId()).isNotEqualTo(first.eventId());
        store.published(second.eventId(),second.claimToken());
    }
    @Test void stateAndOutboxRollbackTogether() {
        UUID aggregate=UUID.randomUUID();
        var tx=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            writer.append(event(aggregate,0));
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id=?",
                Integer.class,aggregate)).isZero();
    }
}

