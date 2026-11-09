package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.BookAvroPublisher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
@Tag("book-avro-e2e")
class BookAvroRegistryFailureDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY = new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));

    @Test void coldRegistryFailureRetriesWithoutPublishingOrReleasingTheNextRevision() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        var book = UUID.randomUUID();
        var first = BookAvroFixture.event(book,0,BookChangedEvent.Kind.CREATED);
        var next = BookAvroFixture.event(book,1,BookChangedEvent.Kind.UPDATED);
        var writer = new JdbcOutboxWriter(jdbc);
        writer.append(OutboxMessage.book(first,true)); writer.append(OutboxMessage.book(next,true));
        String registry = BookAvroFixture.registryUrl(REGISTRY);
        BookAvroFixture.register(registry);
        var factory = new DefaultKafkaProducerFactory<String,Object>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class,
                "http.connect.timeout.ms",500,"http.read.timeout.ms",500,"max.retries",0));
        // No serializer/schema cache exists yet; pause the real Registry before its first lookup.
        REGISTRY.getDockerClient().pauseContainerCmd(REGISTRY.getContainerId()).exec();
        try (var avro = new BookAvroPublisher(factory,registry)) {
            var dispatcher = new OutboxDispatcher(new JdbcOutboxStore(jdbc),
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory),avro),new SimpleMeterRegistry());
            dispatcher.dispatch();
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                var row=jdbc.queryForMap("SELECT status,attempts,last_error FROM outbox_events WHERE event_id=?",first.eventId());
                assertThat(row.get("status")).isEqualTo("PENDING");
                assertThat(((Number)row.get("attempts")).intValue()).isEqualTo(1);
                assertThat((String)row.get("last_error")).isNotBlank();
                assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_events WHERE event_id=?",Integer.class,next.eventId())).isZero();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status='PUBLISHED'",Long.class)).isZero();
            });
            REGISTRY.getDockerClient().unpauseContainerCmd(REGISTRY.getContainerId()).exec();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                dispatcher.dispatch();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status='PUBLISHED'",Long.class)).isEqualTo(2L);
            });
            assertThat(jdbc.queryForObject("SELECT wire_format FROM outbox_events WHERE event_id=?",String.class,first.eventId())).isEqualTo("AVRO");
        } finally {
            if (Boolean.TRUE.equals(REGISTRY.getDockerClient().inspectContainerCmd(REGISTRY.getContainerId()).exec().getState().getPaused()))
                REGISTRY.getDockerClient().unpauseContainerCmd(REGISTRY.getContainerId()).exec();
            factory.destroy();
        }
    }
}
