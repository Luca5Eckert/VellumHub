package com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.catalog_service.module.book_progress.domain.exception.ReadingProgressWritesFrozenException;
import com.vellumhub.catalog_service.module.book_progress.infrastructure.persistence.JdbcReadingProgressWriteGate;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.*;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.*;
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

/** Real V4->V5 JSONB migration, writer fence, drain, JSON->Avro->JSON rollback. */
@Tag("distributed")
@Tag("reading-avro-e2e")
class ReadingProgressAvroCutoverRehearsalDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void legacyPendingRowDrainsBeforeAvroAndRollbackRetainsImmutableWireIdentity() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("4").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        UUID progress = UUID.randomUUID(), user = UUID.randomUUID(), book = UUID.randomUUID();
        var before = fact(progress, user, book, 0, ReadingProgressChangedEvent.Kind.CREATED);
        var jsonMapper = JsonMapper.builder().findAndAddModules().build();
        // Simulate an old binary: omit wire metadata from its PostgreSQL Outbox INSERT.
        jdbc.update("""
                INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
                    event_type,topic,message_key,payload)
                VALUES (?,'BOOK_PROGRESS',?,0,'ReadingProgressChangedEvent',?,?,CAST(? AS jsonb))
                """, before.eventId(), progress, KafkaTopics.READING_PROGRESS, progress.toString(),
                jsonMapper.writeValueAsString(before));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForMap("SELECT wire_format,payload_schema_version FROM outbox_events WHERE event_id=?",
                before.eventId())).containsEntry("wire_format","JSON")
                .containsEntry("payload_schema_version",1);
        var gate = new JdbcReadingProgressWriteGate(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        var writer = new JdbcOutboxWriter(jdbc);
        var cfg = new HashMap<String,Object>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        cfg.put("spring.json.type.mapping",
                "reading_progress_changed_event:com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent");
        var factory = new DefaultKafkaProducerFactory<String,Object>(cfg);
        String registry = registryUrl();
        int schemaId = register(registry);
        try (var avro = new ReadingProgressAvroPublisher(factory,registry);
             var bookPublisher = new BookAvroPublisher(factory,registry);
             var legacy = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.READING_PROGRESS,"progress-cutover-json");
             var newer = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.READING_PROGRESS_AVRO,"progress-cutover-avro")) {
            var dispatcher = new OutboxDispatcher(new JdbcOutboxStore(jdbc),
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory),bookPublisher,avro),
                    new SimpleMeterRegistry());

            freeze(jdbc,true);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> gate.requireWritable()))
                    .isInstanceOf(ReadingProgressWritesFrozenException.class);
            assertThat(pending(jdbc)).isEqualTo(1L);
            dispatcher.dispatch();
            published(jdbc,before.eventId());
            var oldBytes = legacy.awaitRecord(KafkaTopics.READING_PROGRESS,progress.toString(),
                    Duration.ofSeconds(20),Duration.ofMillis(150));
            assertThat(oldBytes.value()[0]).isEqualTo((byte)'{');
            assertThat(pending(jdbc)).as("do not switch while JSON Outbox is undrained").isZero();

            freeze(jdbc,false);
            var changed = fact(progress,user,book,1,ReadingProgressChangedEvent.Kind.UPDATED);
            tx.executeWithoutResult(status -> {
                gate.requireWritable();
                writer.append(OutboxMessage.readingProgress(changed,true));
            });
            assertThat(jdbc.queryForObject(
                    "SELECT wire_format FROM outbox_events WHERE event_id=?",
                    String.class,changed.eventId())).isEqualTo("AVRO");
            freeze(jdbc,true);
            dispatcher.dispatch();
            published(jdbc,changed.eventId());
            var avroBytes = newer.awaitRecord(KafkaTopics.READING_PROGRESS_AVRO,
                    progress.toString(),Duration.ofSeconds(20),Duration.ofMillis(150));
            assertThat(avroBytes.value()[0]).isZero();
            assertThat(ByteBuffer.wrap(avroBytes.value(),1,4).getInt()).isEqualTo(schemaId);
            try(var decoder = new ReadingProgressAvroDecoder(registry)) {
                assertThat(decoder.decode(avroBytes.topic(),avroBytes.key(),avroBytes.value()))
                        .isEqualTo(changed);
            }
            assertThat(pending(jdbc)).as("drain Avro before restoring JSON route").isZero();

            freeze(jdbc,false);
            var restored = fact(progress,user,book,2,ReadingProgressChangedEvent.Kind.UPDATED);
            tx.executeWithoutResult(status -> {
                gate.requireWritable();
                writer.append(OutboxMessage.readingProgress(restored,false));
            });
            freeze(jdbc,true);
            dispatcher.dispatch();
            published(jdbc,restored.eventId());
            var rollback = legacy.awaitRecord(KafkaTopics.READING_PROGRESS,progress.toString(),
                    Duration.ofSeconds(20),Duration.ofMillis(150));
            assertThat(rollback.value()[0]).isEqualTo((byte)'{');
            assertThat(rollback.key()).isEqualTo(progress.toString());
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM outbox_events
                    WHERE aggregate_type='BOOK_PROGRESS' AND status='PUBLISHED'
                    """,Long.class)).isEqualTo(3L);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM outbox_events
                    WHERE aggregate_type='BOOK_PROGRESS' AND wire_format='AVRO'
                        AND topic=? AND status='PUBLISHED'
                    """,Long.class,KafkaTopics.READING_PROGRESS_AVRO)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM outbox_events
                    WHERE aggregate_type='BOOK_PROGRESS' AND wire_format='JSON'
                        AND topic=? AND status='PUBLISHED'
                    """,Long.class,KafkaTopics.READING_PROGRESS)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("""
                    SELECT payload->>'eventId' FROM outbox_events WHERE event_id=?
                    """,String.class,changed.eventId())).isEqualTo(changed.eventId().toString());
            assertThat(pending(jdbc)).isZero();
        } finally {
            freeze(jdbc,false);
            factory.destroy();
        }
    }

    private static ReadingProgressChangedEvent fact(UUID progress,UUID user,UUID book,
            long revision,ReadingProgressChangedEvent.Kind kind) {
        return new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),progress,user,book,
                "READING",(int)Math.max(0,revision),(int)(revision+1),revision,kind);
    }
    private static void freeze(JdbcTemplate jdbc,boolean value) {
        jdbc.update("UPDATE reading_progress_write_gate SET frozen=?,updated_at=now() WHERE id=1",value);
    }
    private static long pending(JdbcTemplate jdbc) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events
                WHERE aggregate_type='BOOK_PROGRESS' AND status<>'PUBLISHED'
                """,Long.class);
    }
    private static void published(JdbcTemplate jdbc,UUID eventId) {
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(
                jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                        String.class,eventId)).isEqualTo("PUBLISHED"));
    }
    private static String registryUrl() {
        return "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
    }
    private static int register(String registry) throws Exception {
        try (InputStream stream=ReadingProgressAvroCutoverRehearsalDistributedTest.class
                .getResourceAsStream("/ReadingProgressChangedEvent.avsc")) {
            assertThat(stream).isNotNull();
            String subject=KafkaTopics.READING_PROGRESS_AVRO+
                    "-com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent";
            String path=URLEncoder.encode(subject,StandardCharsets.UTF_8);
            var http=HttpClient.newHttpClient();
            var policy=HttpRequest.newBuilder(URI.create(registry+"/config/"+path))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                    .build();
            assertThat(http.send(policy,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            String schema=JSON.writeValueAsString(JSON.readTree(stream.readAllBytes()));
            String payload=JSON.writeValueAsString(Map.of("schemaType","AVRO","schema",schema));
            var request=HttpRequest.newBuilder(URI.create(registry+"/subjects/"+path+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return JSON.readTree(response.body()).path("id").asInt(-1);
        }
    }
}
