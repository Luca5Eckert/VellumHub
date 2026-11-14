package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.ReadingProgressWritesFrozenException;
import com.vellumhub.catalog_service.module.book_progress.infrastructure.persistence.JdbcReadingProgressWriteGate;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.BookAvroPublisher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.ReadingProgressAvroPublisher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.kafka.avro.ReadingProgressAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@Tag("distributed")
@Tag("reading-avro-e2e")
class ReadingProgressAvroCutoverRehearsalDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB =
            new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * A controlled migration rehearsal with a real pre-V5 JSON row, PostgreSQL fence,
     * Registry-approved Avro bytes and an independent rollback to JSON. No production
     * cutover is claimed: consumer checkpoints are validated by their own distributed suites.
     */
    @Test void cutoverAndRollbackDrainLegacyRowsWithoutChangingEventIdentity() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        var jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target("4").load().migrate();

        UUID aggregate = UUID.randomUUID(),user = UUID.randomUUID(),book = UUID.randomUUID();
        var historic = new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),
                aggregate,user,book,"READING",0,1,0,ReadingProgressChangedEvent.Kind.CREATED);
        String historicJson = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules().build().writeValueAsString(historic);
        jdbc.update("""
                INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
                    event_type,topic,message_key,payload)
                VALUES (?,'BOOK_PROGRESS',?,0,'ReadingProgressChangedEvent',?,?,CAST(? AS jsonb))
                """,historic.eventId(),aggregate,KafkaTopics.READING_PROGRESS,
                aggregate.toString(),historicJson);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForMap("SELECT topic,wire_format,payload->>'eventId' AS event_id "
                +"FROM outbox_events WHERE event_id=?",historic.eventId()))
                .containsEntry("topic",KafkaTopics.READING_PROGRESS)
                .containsEntry("wire_format","JSON")
                .containsEntry("event_id",historic.eventId().toString());

        String registry = "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
        int approvedId = register(registry);
        var cfg = new HashMap<String,Object>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        cfg.put("spring.json.type.mapping",
                "reading_progress_changed_event:com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent");
        var factory = new DefaultKafkaProducerFactory<String,Object>(cfg);
        var gate = new JdbcReadingProgressWriteGate(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var writer = new JdbcOutboxWriter(jdbc);
        try (var readingPublisher = new ReadingProgressAvroPublisher(factory,registry);
             var bookPublisher = new BookAvroPublisher(factory,registry);
             var oldWire = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.READING_PROGRESS,"reading-cutover-legacy-"+aggregate);
             var newWire = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.READING_PROGRESS_AVRO,"reading-cutover-avro-"+aggregate)) {
            var dispatcher = new OutboxDispatcher(new JdbcOutboxStore(jdbc),
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory),
                            bookPublisher,readingPublisher),new SimpleMeterRegistry());
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=true WHERE id=1");
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> gate.requireWritable()))
                    .isInstanceOf(ReadingProgressWritesFrozenException.class);
            dispatcher.dispatch();
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject(
                            "SELECT status FROM outbox_events WHERE event_id=?",
                            String.class,historic.eventId())).isEqualTo("PUBLISHED"));
            var old = oldWire.awaitRecord(KafkaTopics.READING_PROGRESS,aggregate.toString(),
                    Duration.ofSeconds(25),Duration.ofMillis(100));
            assertThat(old.value()[0]).isEqualTo((byte)'{');
            assertThat(old.key()).isEqualTo(aggregate.toString());
            assertThat(pendingProgress(jdbc,aggregate)).isZero();

            var migrated = new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),
                    aggregate,user,book,"READING",1,2,1,ReadingProgressChangedEvent.Kind.UPDATED);
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=false WHERE id=1");
            tx.executeWithoutResult(s -> {
                gate.requireWritable();
                writer.append(OutboxMessage.readingProgress(migrated,true));
            });
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=true WHERE id=1");
            dispatcher.dispatch();
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject(
                            "SELECT status FROM outbox_events WHERE event_id=?",
                            String.class,migrated.eventId())).isEqualTo("PUBLISHED"));
            var avro = newWire.awaitRecord(KafkaTopics.READING_PROGRESS_AVRO,aggregate.toString(),
                    Duration.ofSeconds(25),Duration.ofMillis(100));
            assertThat(avro.value()[0]).isZero();
            assertThat(ByteBuffer.wrap(avro.value(),1,4).getInt()).isEqualTo(approvedId);
            try (var decoder = new ReadingProgressAvroDecoder(registry)) {
                assertThat(decoder.decode(avro.topic(),avro.key(),avro.value()))
                        .isEqualTo(migrated);
            }
            assertThat(pendingProgress(jdbc,aggregate)).isZero();

            var rollback = new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),
                    aggregate,user,book,"READING",2,3,2,ReadingProgressChangedEvent.Kind.UPDATED);
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=false WHERE id=1");
            tx.executeWithoutResult(s -> {
                gate.requireWritable();
                writer.append(OutboxMessage.readingProgress(rollback,false));
            });
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=true WHERE id=1");
            dispatcher.dispatch();
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject(
                            "SELECT status FROM outbox_events WHERE event_id=?",
                            String.class,rollback.eventId())).isEqualTo("PUBLISHED"));
            var restored = oldWire.awaitRecord(KafkaTopics.READING_PROGRESS,aggregate.toString(),
                    Duration.ofSeconds(25),Duration.ofMillis(100));
            assertThat(restored.value()[0]).isEqualTo((byte)'{');
            assertThat(restored.key()).isEqualTo(aggregate.toString());
            assertThat(pendingProgress(jdbc,aggregate)).isZero();
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbox_events
                    WHERE aggregate_type='BOOK_PROGRESS' AND aggregate_id=?
                    AND status='PUBLISHED'
                    """,Long.class,aggregate)).isEqualTo(3L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbox_events
                    WHERE aggregate_type='BOOK_PROGRESS' AND aggregate_id=?
                      AND wire_format='AVRO' AND topic=?
                    """,Long.class,aggregate,KafkaTopics.READING_PROGRESS_AVRO)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT COUNT(*) FROM outbox_events
                    WHERE aggregate_type='BOOK_PROGRESS' AND aggregate_id=?
                      AND wire_format='JSON' AND topic=?
                    """,Long.class,aggregate,KafkaTopics.READING_PROGRESS)).isEqualTo(2L);
        } finally {
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=false WHERE id=1");
            factory.destroy();
        }
    }

    private static long pendingProgress(JdbcTemplate jdbc,UUID aggregate) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_events
                WHERE aggregate_type='BOOK_PROGRESS' AND aggregate_id=?
                  AND status <> 'PUBLISHED'
                """,Long.class,aggregate);
    }

    private static int register(String url) throws Exception {
        try (InputStream stream = com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent.class
                .getResourceAsStream("/ReadingProgressChangedEvent.avsc")) {
            assertThat(stream).isNotNull();
            String approved = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String subject = KafkaTopics.READING_PROGRESS_AVRO + "-" +
                    com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent
                            .getClassSchema().getFullName();
            String path = URLEncoder.encode(subject,StandardCharsets.UTF_8);
            var client = HttpClient.newHttpClient();
            var policy = HttpRequest.newBuilder(URI.create(url+"/config/"+path))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                    .build();
            assertThat(client.send(policy,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            var payload = JSON.writeValueAsString(Map.of("schemaType","AVRO",
                    "schema",JSON.writeValueAsString(JSON.readTree(approved))));
            var request = HttpRequest.newBuilder(URI.create(url+"/subjects/"+path+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var result = client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
            return JSON.readTree(result.body()).path("id").asInt(-1);
        }
    }

}
