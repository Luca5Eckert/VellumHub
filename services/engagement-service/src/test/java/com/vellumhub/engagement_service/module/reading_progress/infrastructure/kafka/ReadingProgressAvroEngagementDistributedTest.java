package com.vellumhub.engagement_service.module.reading_progress.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.avro.ReadingProgressAvroMapper;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import com.vellumhub.testing.distributed.container.KafkaIntegrationTestSupport;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** Real Avro Kafka -> Engagement ReadingSessionEntry + transactional progress checkpoint. */
@Tag("distributed")
@Tag("reading-avro-e2e")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"app.reading-progress.avro.consumer-enabled=true",
                "app.kafka.recovery.max-retries=0","app.outbox.enabled=false",
                "spring.kafka.listener.concurrency=1"})
class ReadingProgressAvroEngagementDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY=new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON=new ObjectMapper();

    private static String registryUrl() {
        return "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
    }

    @DynamicPropertySource static void dependencies(DynamicPropertyRegistry props) {
        props.add("spring.datasource.url",POSTGRES::getJdbcUrl);
        props.add("spring.datasource.username",POSTGRES::getUsername);
        props.add("spring.datasource.password",POSTGRES::getPassword);
        props.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        props.add("spring.jpa.database-platform",()->"org.hibernate.dialect.PostgreSQLDialect");
        props.add("spring.jpa.properties.hibernate.dialect",()->"org.hibernate.dialect.PostgreSQLDialect");
        props.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        props.add("spring.flyway.enabled",()->"true");
        props.add("spring.kafka.bootstrap-servers",KAFKA::getBootstrapServers);
        props.add("spring.kafka.consumer.auto-offset-reset",()->"earliest");
        props.add("spring.kafka.admin.fail-fast",()->"true");
        props.add("jwt.public-key-location",()->"classpath:security/test-public.pem");
        props.add("management.health.kafka.enabled",()->"false");
        props.add("app.reading-progress.avro.registry-url",ReadingProgressAvroEngagementDistributedTest::registryUrl);
    }

    @BeforeAll static void registerSchema() throws Exception {
        try (InputStream in=ReadingProgressAvroEngagementDistributedTest.class
                .getResourceAsStream("/ReadingProgressChangedEvent.avsc")) {
            assertThat(in).isNotNull();
            String schema=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            String name=KafkaTopics.READING_PROGRESS_AVRO+"-com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent";
            String path=URLEncoder.encode(name,StandardCharsets.UTF_8);
            var client=HttpClient.newHttpClient();
            assertThat(client.send(HttpRequest.newBuilder(URI.create(registryUrl()+"/config/"+path))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                    .build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            String payload=JSON.writeValueAsString(Map.of("schemaType","AVRO",
                    "schema",JSON.writeValueAsString(JSON.readTree(schema))));
            assertThat(client.send(HttpRequest.newBuilder(URI.create(registryUrl()+"/subjects/"+path+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
    }

    @Autowired JdbcTemplate jdbc;

    @Test void persistsReadingSessionOnceAndBlocksGapAtOriginalAvroOffset() throws Exception {
        UUID aggregate=UUID.randomUUID(),book=UUID.randomUUID(),user=UUID.randomUUID();
        jdbc.update("INSERT INTO book_snapshot(book_id) VALUES(?)",book);
        String topic=KafkaTopics.READING_PROGRESS_AVRO;
        String dltTopic=topic+".engagement-service-dlt";
        try (var producer=producer();
             var dlt=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),dltTopic,"reading-engagement-dlt")) {
            var created=fact(aggregate,book,user,0,ReadingProgressChangedEvent.Kind.CREATED);
            send(producer,topic,aggregate,created);
            checkpoint(aggregate,0);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reading_session_entries WHERE event_id=?",
                    Long.class,created.eventId())).isEqualTo(1L);
            var updated=fact(aggregate,book,user,1,ReadingProgressChangedEvent.Kind.UPDATED);
            send(producer,topic,aggregate,updated);
            checkpoint(aggregate,1);
            send(producer,topic,aggregate,updated);
            await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject("SELECT count(*) FROM reading_session_entries WHERE event_id=?",
                            Long.class,updated.eventId())).isEqualTo(1L));
            var gap=fact(aggregate,book,user,3,ReadingProgressChangedEvent.Kind.UPDATED);
            send(producer,topic,aggregate,gap);
            var dead=dlt.awaitRecord(dltTopic,aggregate.toString(),Duration.ofSeconds(30),Duration.ofMillis(200));
            assertThat(dead.value()[0]).isEqualTo((byte)0);
            assertThat(ByteBuffer.wrap(dead.value(),1,4).getInt()).isPositive();
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-source-topic")).containsExactly(topic);
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-consumer-group"))
                    .containsExactly(KafkaConsumerGroups.ENGAGEMENT_READING_PROGRESS_AVRO);
            checkpoint(aggregate,1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM reading_session_entries WHERE event_id=?",
                    Long.class,gap.eventId())).isZero();
        }
    }

    private void checkpoint(UUID aggregate,long revision) {
        await().ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
                .atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                    assertThat(jdbc.queryForObject(
                            "SELECT last_revision FROM reading_progress_offsets WHERE aggregate_id=?",
                            Long.class,aggregate)).isEqualTo(revision));
    }

    private static ReadingProgressChangedEvent fact(UUID aggregate,UUID book,UUID user,long revision,
            ReadingProgressChangedEvent.Kind kind) {
        return new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),aggregate,user,book,
                "READING",Math.max(0,(int)revision-1),Math.max(0,(int)revision),revision,kind);
    }

    private static KafkaProducer<String,Object> producer() {
        var options=new HashMap<String,Object>();
        options.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        options.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        options.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,KafkaAvroSerializer.class);
        options.put("schema.registry.url",registryUrl());
        options.put("auto.register.schemas",false);
        options.put("use.latest.version",true);
        options.put("latest.compatibility.strict",true);
        options.put("avro.remove.java.properties",true);
        options.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return new KafkaProducer<>(options);
    }

    private static void send(KafkaProducer<String,Object> producer,String topic,UUID aggregate,
            ReadingProgressChangedEvent event) throws Exception {
        producer.send(new ProducerRecord<>(topic,aggregate.toString(),
                ReadingProgressAvroMapper.toAvro(event))).get(15,TimeUnit.SECONDS);
    }
}
