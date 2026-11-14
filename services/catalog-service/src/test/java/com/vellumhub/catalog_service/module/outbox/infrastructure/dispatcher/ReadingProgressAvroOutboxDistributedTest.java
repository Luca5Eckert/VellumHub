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
class ReadingProgressAvroOutboxDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY = new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void approvedWirePublishesWithoutAutoRegisterAndPreservesJsonbIdentity() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        String registry = "http://" + REGISTRY.getHost() + ":" + REGISTRY.getMappedPort(8080) + "/apis/ccompat/v7";
        int approvedId = register(registry);
        UUID aggregate = UUID.randomUUID();
        var event = new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.parse("2026-11-13T13:00:00Z"),
                aggregate,UUID.randomUUID(),UUID.randomUUID(),"READING",2,10,0,
                ReadingProgressChangedEvent.Kind.CREATED);
        var config = new HashMap<String,Object>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        config.put("spring.json.type.mapping",
                "reading_progress_changed_event:com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent");
        var factory = new DefaultKafkaProducerFactory<String,Object>(config);
        try (var readingPublisher = new ReadingProgressAvroPublisher(factory,registry);
             var bookPublisher = new BookAvroPublisher(factory,registry);
             var probe = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.READING_PROGRESS_AVRO,"reading-avro-probe")) {
            var writer = new JdbcOutboxWriter(jdbc);
            writer.append(OutboxMessage.readingProgress(event,true));
            var transport = new KafkaOutboxTransport(new KafkaTemplate<>(factory),bookPublisher,readingPublisher);
            var dispatcher = new OutboxDispatcher(new JdbcOutboxStore(jdbc),transport,new SimpleMeterRegistry());
            dispatcher.dispatch();
            await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                        String.class,event.eventId())).isEqualTo("PUBLISHED"));
            var actual = probe.awaitRecord(KafkaTopics.READING_PROGRESS_AVRO, aggregate.toString(),
                    ASYNC_TIMEOUT, ASYNC_POLL_INTERVAL);
            assertThat(actual.value()[0]).isZero();
            assertThat(ByteBuffer.wrap(actual.value(),1,4).getInt()).isEqualTo(approvedId);
            try (var decoder = new ReadingProgressAvroDecoder(registry)) {
                assertThat(decoder.decode(actual.topic(),actual.key(),actual.value())).isEqualTo(event);
            }
            assertThat(jdbc.queryForMap("SELECT topic,wire_format,payload_schema_version FROM outbox_events WHERE event_id=?",
                    event.eventId())).containsEntry("topic",KafkaTopics.READING_PROGRESS_AVRO)
                    .containsEntry("wire_format","AVRO").containsEntry("payload_schema_version",1);
            assertThat(jdbc.queryForObject(
                    "SELECT payload->>'eventId' FROM outbox_events WHERE event_id=?",
                    String.class,event.eventId())).isEqualTo(event.eventId().toString());
        } finally {
            factory.destroy();
        }
    }

    @Test void distinctFreezeDoesNotAffectBookGateAndJsonHistoryRemainsJson() {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        var gate = new JdbcReadingProgressWriteGate(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        jdbc.update("UPDATE reading_progress_write_gate SET frozen=true WHERE id=1");
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> gate.requireWritable()))
                    .isInstanceOf(ReadingProgressWritesFrozenException.class);
            assertThat(jdbc.queryForObject("SELECT frozen FROM book_write_gate WHERE id=1",Boolean.class))
                    .isFalse();
        } finally {
            jdbc.update("UPDATE reading_progress_write_gate SET frozen=false WHERE id=1");
        }
        assertThatCode(() -> tx.executeWithoutResult(status -> gate.requireWritable())).doesNotThrowAnyException();
        assertThatThrownBy(gate::requireWritable).isInstanceOf(IllegalStateException.class);
        var event = new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.EPOCH,UUID.randomUUID(),
                UUID.randomUUID(),UUID.randomUUID(),"TO_READ",0,0,0,ReadingProgressChangedEvent.Kind.BASELINE);
        new JdbcOutboxWriter(jdbc).append(OutboxMessage.readingProgress(event));
        assertThat(jdbc.queryForMap("SELECT topic,wire_format FROM outbox_events WHERE event_id=?",event.eventId()))
                .containsEntry("topic",KafkaTopics.READING_PROGRESS)
                .containsEntry("wire_format","JSON");
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
