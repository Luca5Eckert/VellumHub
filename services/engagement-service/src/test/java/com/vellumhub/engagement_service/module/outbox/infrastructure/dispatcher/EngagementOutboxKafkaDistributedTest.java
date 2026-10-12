package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.engagement_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.engagement_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import java.time.Instant;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real Kafka and PostgreSQL: a message becomes PUBLISHED only after broker acknowledgement. */
@Tag("distributed")
class EngagementOutboxKafkaDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    static JdbcTemplate jdbc;

    @BeforeAll static void setupDatabase() {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
    }

    @Test void publishesPersistedFactWithStableIdentityTypeHeaderAndAck() {
        var props=new java.util.HashMap<String,Object>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        props.put("spring.json.add.type.headers",true);
        props.put("spring.json.type.mapping",
          "rating_created_event:com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent");
        var factory=new DefaultKafkaProducerFactory<String,Object>(props);
        try {
            var id=UUID.randomUUID();
            var userId=UUID.randomUUID();
            var event=new RatingCreatedEvent(id,Instant.now(),47009L,userId,UUID.randomUUID(),5,0L);
            var message=new OutboxMessage(id,"RATING",event.ratingId().toString(),0L,
                    "RatingCreatedEvent",KafkaTopics.ENGAGEMENT_RATINGS,userId.toString(),event);
            var store=new JdbcOutboxStore(jdbc);
            var transport=new KafkaOutboxTransport(new KafkaTemplate<>(factory));
            var dispatcher=new OutboxDispatcher(store,transport,new SimpleMeterRegistry());
            try(var probe=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                    KafkaTopics.ENGAGEMENT_RATINGS,"engagement-outbox")) {
                new JdbcOutboxWriter(jdbc).append(message);
                dispatcher.dispatch();
                var record=probe.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS,userId.toString(),
                        ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
                assertThat(new String(record.value(),StandardCharsets.UTF_8))
                        .contains(id.toString()).contains("\"revision\":0");
                assertThat(KafkaProbe.utf8HeaderValues(record,"__TypeId__"))
                        .contains("rating_created_event");
                await().atMost(Duration.ofSeconds(15)).untilAsserted(()->assertThat(
                        jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                                String.class,id)).isEqualTo("PUBLISHED"));
            }
        } finally {factory.destroy();}
    }
}

