package com.vellumhub.e2e;

import com.vellumhub.catalog_service.module.book.infrastructure.persistence.JdbcBookWriteGate;
import com.vellumhub.catalog_service.module.book.domain.exception.BookWritesFrozenException;
import com.vellumhub.catalog_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.catalog_service.module.outbox.infrastructure.dispatcher.OutboxDispatcher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.BookAvroPublisher;
import com.vellumhub.catalog_service.module.outbox.infrastructure.kafka.KafkaOutboxTransport;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxStore;
import com.vellumhub.catalog_service.module.outbox.infrastructure.persistence.JdbcOutboxWriter;
import com.vellumhub.e2e.support.RecommendationE2ETestSupport;
import com.vellumhub.engagement_service.EngagementServiceApplication;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.BookChangedEvent;
import com.vellumhub.recommendation_service.RecommendationServiceApplication;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/** One real Catalog relay, Kafka and Registry feed both actual consumer applications and SQL DBs. */
@Tag("distributed")
@Tag("book-avro-e2e")
@SpringBootTest(classes=RecommendationServiceApplication.class,
        webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"app.book.avro.consumer-enabled=true", "app.kafka.recovery.max-retries=0"})
class BookAvroCutoverE2ETest extends RecommendationE2ETestSupport {
    @Container static final PostgreSQLContainer<?> CATALOG = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final PostgreSQLContainer<?> ENGAGEMENT = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY = new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    @DynamicPropertySource static void avro(DynamicPropertyRegistry props) {
        props.add("app.book.avro.registry-url", () -> BookAvroFixture.registryUrl(REGISTRY));
    }
    @Autowired JdbcTemplate recommendation;
    @Autowired KafkaListenerEndpointRegistry recommendationListeners;
    @MockitoBean EmbeddingBookProvider embeddings;

    @Test void drainsBothReadModelsBeforeCutoverAndRollbackWithoutChangingPendingAvroIdentity() throws Exception {
        float[] vector = new float[384]; Arrays.fill(vector, 0.25f);
        when(embeddings.of(anyString(), anyString(), anyString(), anyList())).thenReturn(vector);
        String registry = BookAvroFixture.registryUrl(REGISTRY);
        BookAvroFixture.register(registry);
        String broker = KAFKA.getBootstrapServers();
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", broker))) {
            // Explicit partitions and retention, rather than relying on broker auto-creation.
            var known = admin.listTopics().names().get(15, TimeUnit.SECONDS);
            for (String topic : List.of(KafkaTopics.BOOK_EVENTS, KafkaTopics.BOOK_EVENTS_AVRO)) {
                if (!known.contains(topic)) admin.createTopics(List.of(new NewTopic(topic, 1, (short)1)
                        .configs(Map.of("retention.ms", "86400000")))).all().get(15, TimeUnit.SECONDS);
            }
            try (var engagementApp = new SpringApplicationBuilder(EngagementServiceApplication.class)
                    .web(WebApplicationType.SERVLET).run(
                        "--spring.config.location=classpath:engagement/application.properties",
                        "--spring.application.name=engagement-service", "--server.port=0",
                        "--spring.datasource.url="+ENGAGEMENT.getJdbcUrl(),
                        "--spring.datasource.username="+ENGAGEMENT.getUsername(),
                        "--spring.datasource.password="+ENGAGEMENT.getPassword(),
                        "--spring.datasource.driver-class-name=org.postgresql.Driver",
                        "--spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
                        "--spring.jpa.hibernate.ddl-auto=validate", "--spring.flyway.enabled=true",
                        "--spring.flyway.locations=classpath:engagement/db/migration",
                        "--spring.kafka.bootstrap-servers="+broker, "--spring.kafka.listener.concurrency=1",
                        "--app.book.avro.consumer-enabled=true", "--app.book.avro.registry-url="+registry,
                        "--app.outbox.enabled=false", "--jwt.public-key-location=classpath:security/test-public.pem")) {
                JdbcTemplate engagement = engagementApp.getBean(JdbcTemplate.class);
                var ds = new DriverManagerDataSource(CATALOG.getJdbcUrl(), CATALOG.getUsername(), CATALOG.getPassword());
                var catalog = new JdbcTemplate(ds);
                Flyway.configure().dataSource(ds).locations("classpath:catalog/db/migration").target("3").load().migrate();
                var book = UUID.randomUUID();
                var old = BookAvroFixture.event(book, 0, BookChangedEvent.Kind.CREATED);
                var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
                catalog.update("""
                        INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,event_type,topic,message_key,payload)
                        VALUES(?,'BOOK',?,0,'BookChangedEvent',?,?,CAST(? AS jsonb))
                        """, old.eventId(), book, KafkaTopics.BOOK_EVENTS, book.toString(), mapper.writeValueAsString(old));
                Flyway.configure().dataSource(ds).locations("classpath:catalog/db/migration").load().migrate();
                var cfg = new HashMap<String,Object>();
                cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker);
                cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
                cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
                cfg.put("spring.json.type.mapping", "book_changed_event:com.vellumhub.kafka.contracts.book.BookChangedEvent");
                var factory = new DefaultKafkaProducerFactory<String,Object>(cfg);
                try (var avro = new BookAvroPublisher(factory, registry)) {
                    var dispatcher = new OutboxDispatcher(new JdbcOutboxStore(catalog),
                            new KafkaOutboxTransport(new KafkaTemplate<>(factory), avro), new SimpleMeterRegistry());
                    var writer = new JdbcOutboxWriter(catalog);
                    var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
                    var gate = new JdbcBookWriteGate(catalog);
                    freeze(catalog, true);
                    assertThatThrownBy(() -> tx.executeWithoutResult(s -> gate.requireWritable()))
                            .isInstanceOf(BookWritesFrozenException.class);
                    dispatcher.dispatch();
                    reconciled(catalog, engagement, book, 0, false);
                    drained(admin, KafkaTopics.BOOK_EVENTS, KafkaConsumerGroups.RECOMMENDATION_SERVICE, KafkaConsumerGroups.ENGAGEMENT_SERVICE);
                    freeze(catalog, false);
                    var updated = BookAvroFixture.event(book, 1, BookChangedEvent.Kind.UPDATED);
                    tx.executeWithoutResult(s -> {gate.requireWritable(); writer.append(OutboxMessage.book(updated, true));});
                    freeze(catalog, true);
                    // An Avro row pending before rollback must drain as Avro, retaining its ID.
                    dispatcher.dispatch();
                    reconciled(catalog, engagement, book, 1, false);
                    drained(admin, KafkaTopics.BOOK_EVENTS_AVRO, KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO, KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO);
                    assertThat(catalog.queryForObject("SELECT wire_format FROM outbox_events WHERE event_id=?", String.class, updated.eventId())).isEqualTo("AVRO");
                    // Same physical fact through the old JSON topic cannot change either projection again.
                    new KafkaTemplate<>(factory).send(KafkaTopics.BOOK_EVENTS, book.toString(), updated).get(15,TimeUnit.SECONDS);
                    drained(admin, KafkaTopics.BOOK_EVENTS, KafkaConsumerGroups.RECOMMENDATION_SERVICE, KafkaConsumerGroups.ENGAGEMENT_SERVICE);
                    reconciled(catalog, engagement, book, 1, false);
                    freeze(catalog, false);
                    var deleted = BookAvroFixture.event(book, 2, BookChangedEvent.Kind.DELETED);
                    tx.executeWithoutResult(s -> {gate.requireWritable(); writer.append(OutboxMessage.book(deleted, false));});
                    freeze(catalog, true);
                    dispatcher.dispatch();
                    reconciled(catalog, engagement, book, 2, true);
                    drained(admin, KafkaTopics.BOOK_EVENTS, KafkaConsumerGroups.RECOMMENDATION_SERVICE, KafkaConsumerGroups.ENGAGEMENT_SERVICE);
                    assertThat(recommendation.queryForObject("SELECT count(*) FROM processed_events WHERE event_id=?", Long.class, updated.eventId())).isEqualTo(1L);
                    assertThat(catalog.queryForObject("SELECT count(*) FROM outbox_events WHERE status='PUBLISHED'", Long.class)).isEqualTo(3L);

                    // Gap must block both groups at its original Avro offset, also after a rebalance.
                    try (var producer = BookAvroFixture.producer(broker,registry);
                         var rdlt = KafkaProbe.subscribe(broker,KafkaTopics.BOOK_EVENTS_AVRO+".recommendation-service-dlt","e2e-r-dlt");
                         var edlt = KafkaProbe.subscribe(broker,KafkaTopics.BOOK_EVENTS_AVRO+".engagement-service-dlt","e2e-e-dlt")) {
                        var gap = BookAvroFixture.event(book,4,BookChangedEvent.Kind.UPDATED);
                        var metadata = producer.send(new org.apache.kafka.clients.producer.ProducerRecord<>(
                                KafkaTopics.BOOK_EVENTS_AVRO,book.toString(),BookAvroFixture.encoded(gap))).get(15,TimeUnit.SECONDS);
                        for (var probe : List.of(rdlt,edlt)) {
                            String suffix = probe == rdlt ? "recommendation-service" : "engagement-service";
                            var dead = probe.awaitRecord(KafkaTopics.BOOK_EVENTS_AVRO+"."+suffix+"-dlt",book.toString(),Duration.ofSeconds(30),Duration.ofMillis(100));
                            assertThat(dead.value()[0]).isZero();
                            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-source-offset")).containsExactly(Long.toString(metadata.offset()));
                            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-source-partition")).containsExactly(Integer.toString(metadata.partition()));
                        }
                        var engagementListeners = engagementApp.getBean(KafkaListenerEndpointRegistry.class);
                        for (var listeners : List.of(recommendationListeners,engagementListeners)) {
                            for (var listener : listeners.getListenerContainers()) {
                                if (listener.getGroupId().endsWith("-book-avro")) { listener.stop(); listener.start(); }
                            }
                        }
                        await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                            for (var db : List.of(recommendation,engagement)) {
                                assertThat(db.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",Long.class,book)).isEqualTo(2L);
                                assertThat(db.queryForObject("SELECT count(*) FROM kafka_consumer_quarantine WHERE source_topic=? AND source_offset=? AND status='BLOCKED'",Long.class,KafkaTopics.BOOK_EVENTS_AVRO,metadata.offset())).isEqualTo(1L);
                            }
                        });
                        // Restore the missing business fact via its original JSON route; never edit checkpoints.
                        var missing = BookAvroFixture.event(book,3,BookChangedEvent.Kind.UPDATED);
                        new KafkaTemplate<>(factory).send(KafkaTopics.BOOK_EVENTS,book.toString(),missing).get(15,TimeUnit.SECONDS);
                        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                            for (var db : List.of(recommendation,engagement))
                                assertThat(db.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?",Long.class,book)).isEqualTo(3L);
                        });
                        for (var db : List.of(recommendation,engagement)) {
                            assertThat(db.update("UPDATE kafka_consumer_quarantine SET status='RETRY_REQUESTED' WHERE source_topic=? AND source_offset=? AND status='BLOCKED'",KafkaTopics.BOOK_EVENTS_AVRO,metadata.offset())).isEqualTo(1);
                        }
                        for (var listeners : List.of(recommendationListeners,engagementListeners)) {
                            for (var listener : listeners.getListenerContainers()) {
                                if (listener.getGroupId().endsWith("-book-avro")) {
                                    listener.stop();
                                    listener.resumePartition(new TopicPartition(KafkaTopics.BOOK_EVENTS_AVRO,metadata.partition()));
                                    listener.start();
                                }
                            }
                        }
                        reconciled(catalog,engagement,book,4,false);
                        drained(admin,KafkaTopics.BOOK_EVENTS_AVRO,KafkaConsumerGroups.RECOMMENDATION_BOOK_AVRO,KafkaConsumerGroups.ENGAGEMENT_BOOK_AVRO);
                        for (var db : List.of(recommendation,engagement))
                            assertThat(db.queryForObject("SELECT count(*) FROM kafka_consumer_quarantine WHERE source_topic=? AND source_offset=? AND status='RESOLVED'",Long.class,KafkaTopics.BOOK_EVENTS_AVRO,metadata.offset())).isEqualTo(1L);
                    }

                } finally {factory.destroy();}
            }
        }
    }
    private void reconciled(JdbcTemplate catalog, JdbcTemplate engagement, UUID book, long revision, boolean deleted) {
        await().ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
                .atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(catalog.queryForObject("SELECT count(*) FROM outbox_events WHERE status<>'PUBLISHED'", Long.class)).isZero();
            for (var db : List.of(recommendation, engagement)) {
                assertThat(db.queryForObject("SELECT last_revision FROM book_event_offsets WHERE book_id=?", Long.class, book)).isEqualTo(revision);
                assertThat(db.queryForObject("SELECT count(*) FROM kafka_consumer_quarantine WHERE status<>'RESOLVED'", Long.class)).isZero();
            }
            assertThat(recommendation.queryForObject("SELECT count(*) FROM recommendations WHERE book_id=?", Long.class, book)).isEqualTo(deleted?0L:1L);
            if (!deleted) assertThat(recommendation.queryForObject("SELECT title FROM recommendations WHERE book_id=?", String.class, book)).isEqualTo("Book-"+revision);
            assertThat(engagement.queryForObject("SELECT active FROM book_snapshot WHERE book_id=?", Boolean.class, book)).isEqualTo(!deleted);
        });
        System.out.println("BOOK_AVRO_EVIDENCE book="+book+" revision="+revision+" both_read_models=reconciled");
    }
    private static void freeze(JdbcTemplate catalog, boolean frozen) {
        assertThat(catalog.update("UPDATE book_write_gate SET frozen=?,updated_at=now() WHERE id=1", frozen)).isEqualTo(1);
    }
    private static void drained(AdminClient admin, String topic, String... groups) throws Exception {
        var partitions = admin.describeTopics(List.of(topic)).allTopicNames().get(15,TimeUnit.SECONDS).get(topic).partitions();
        var request = new HashMap<TopicPartition,OffsetSpec>();
        partitions.forEach(p -> request.put(new TopicPartition(topic,p.partition()),OffsetSpec.latest()));
        var ends = admin.listOffsets(request).all().get(15,TimeUnit.SECONDS);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            for (String group : groups) {
                var offsets = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata().get(15,TimeUnit.SECONDS);
                ends.forEach((tp,end) -> {assertThat(offsets).containsKey(tp); assertThat(offsets.get(tp).offset()).isGreaterThanOrEqualTo(end.offset());});
            }
        });
        System.out.println("BOOK_AVRO_EVIDENCE topic="+topic+" groups="+List.of(groups)+" end_offsets="+ends+" lag=0");
    }
}
