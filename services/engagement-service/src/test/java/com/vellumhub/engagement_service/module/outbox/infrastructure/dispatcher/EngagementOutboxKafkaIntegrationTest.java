package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.engagement_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.engagement_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
@Testcontainers
@EmbeddedKafka(partitions=1,topics={KafkaTopics.ENGAGEMENT_RATINGS})
class EngagementOutboxKafkaIntegrationTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:15-alpine");
    static JdbcTemplate jdbc;
    @BeforeAll static void setup() {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        jdbc=new JdbcTemplate(ds);
    }

    @Test
    void pendingEventRecoversAndIsPublishedWithOriginalIdentity(EmbeddedKafkaBroker broker) {
        var event=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),9403L,
                UUID.randomUUID(),UUID.randomUUID(),5,0L);
        var store=new JdbcOutboxStore(jdbc);
        new JdbcOutboxWriter(jdbc).append(new OutboxMessage(
                event.eventId(),"RATING",event.ratingId().toString(),event.revision(),
                "RatingCreatedEvent",KafkaTopics.ENGAGEMENT_RATINGS,event.userId().toString(),event));

        // Persist a failed attempt (broker unavailable), retaining the same eventId and payload.
        var first=store.claimNext(Duration.ofSeconds(30)).orElseThrow();
        store.failed(first.eventId(),first.claimToken(),"broker unavailable",Duration.ofSeconds(1));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM outbox_events WHERE event_id=?",String.class,event.eventId()))
                .isEqualTo("PENDING");
        jdbc.update("UPDATE outbox_events SET next_attempt_at=now() WHERE event_id=?",event.eventId());

        Map<String,Object> props=KafkaTestUtils.producerProps(broker);
        props.put("spring.json.type.mapping",
                "rating_created_event:com.vellumhub.kafka.contracts.engagement.RatingCreatedEvent");
        var factory=new DefaultKafkaProducerFactory<String,Object>(props,
                new StringSerializer(),new JsonSerializer<>());
        try {
            var dispatcher=new OutboxDispatcher(store,
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory)),new SimpleMeterRegistry());
            var consumerProps=KafkaTestUtils.consumerProps("outbox-engagement-test","false",broker);
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,"earliest");
            try(var consumer=new DefaultKafkaConsumerFactory<String,String>(consumerProps,
                    new StringDeserializer(),new StringDeserializer()).createConsumer()) {
                consumer.subscribe(java.util.List.of(KafkaTopics.ENGAGEMENT_RATINGS));
                dispatcher.dispatch();
                await().atMost(Duration.ofSeconds(25)).untilAsserted(()->{
                    assertThat(jdbc.queryForObject(
                            "SELECT status FROM outbox_events WHERE event_id=?",
                            String.class,event.eventId())).isEqualTo("PUBLISHED");
                });
                boolean found=false;
                for(int i=0;i<10 && !found;i++) {
                    var records=consumer.poll(Duration.ofSeconds(1));
                    for(var record:records) {
                        if(record.value().contains(event.eventId().toString())) {
                            assertThat(record.key()).isEqualTo(event.userId().toString());
                            assertThat(record.value()).contains("\"ratingId\":9403");
                            found=true;
                        }
                    }
                }
                assertThat(found).as("Published immutable business fact found on Kafka").isTrue();
            }
        } finally { factory.destroy(); }
    }
}
