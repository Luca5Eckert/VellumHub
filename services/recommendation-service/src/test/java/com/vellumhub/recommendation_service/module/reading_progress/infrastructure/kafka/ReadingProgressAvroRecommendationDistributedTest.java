package com.vellumhub.recommendation_service.module.reading_progress.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.avro.ReadingProgressAvroMapper;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.readingprogress.ReadingProgressChangedEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.model.BookFeature;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.BookFeatureRepository;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.UserProfileRepository;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.*;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual Reading Progress Avro listener updates the same UserProfile and DB revision fence. */
@Tag("distributed")
@Tag("reading-avro-e2e")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,
        properties={"app.kafka.legacy-consumers.enabled=false",
                "app.reading-progress.avro.consumer-enabled=true",
                "app.kafka.recovery.max-retries=0","spring.kafka.listener.concurrency=1"})
class ReadingProgressAvroRecommendationDistributedTest extends DistributedIntegrationTestSupport {
    @Container static final GenericContainer<?> REGISTRY=
            new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON=new ObjectMapper();

    private static String registryUrl() {
        return "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
    }

    @DynamicPropertySource static void registry(DynamicPropertyRegistry props) {
        props.add("app.reading-progress.avro.registry-url",
                ReadingProgressAvroRecommendationDistributedTest::registryUrl);
    }

    @BeforeAll static void registerSchema() throws Exception {
        try (InputStream in=ReadingProgressAvroRecommendationDistributedTest.class
                .getResourceAsStream("/ReadingProgressChangedEvent.avsc")) {
            assertThat(in).isNotNull();
            String raw=new String(in.readAllBytes(),StandardCharsets.UTF_8);
            String subject=KafkaTopics.READING_PROGRESS_AVRO+
                    "-com.vellumhub.kafka.avro.readingprogress.ReadingProgressChangedEvent";
            String id=URLEncoder.encode(subject,StandardCharsets.UTF_8);
            var http=HttpClient.newHttpClient();
            var policy=HttpRequest.newBuilder(URI.create(registryUrl()+"/config/"+id))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                    .build();
            assertThat(http.send(policy,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            String body=JSON.writeValueAsString(Map.of("schemaType","AVRO",
                    "schema",JSON.writeValueAsString(JSON.readTree(raw))));
            var request=HttpRequest.newBuilder(URI.create(registryUrl()+"/subjects/"+id+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            assertThat(http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BookFeatureRepository features;
    @Autowired UserProfileRepository profiles;
    @MockitoBean EmbeddingBookProvider embeddings;

    @Test void originalCheckpointSurvivesDuplicateAndGapRetainsRawSchemaId() throws Exception {
        float[] vector=new float[384]; vector[0]=1.0f;
        when(embeddings.of(anyString(),anyString(),anyString(),anyList())).thenReturn(vector);
        UUID progress=UUID.randomUUID(),user=UUID.randomUUID(),book=UUID.randomUUID();
        features.save(BookFeature.create(book,vector,1.0));
        String topic=KafkaTopics.READING_PROGRESS_AVRO;
        String dltTopic=topic+".recommendation-service-dlt";
        try (var producer=producer();
             var dlt=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),dltTopic,"reading-recommendation-dlt")) {
            var created=fact(progress,user,book,0,ReadingProgressChangedEvent.Kind.CREATED);
            send(producer,created);
            checkpoint(progress,0);
            double score=profiles.findById(user).orElseThrow().getTotalEngagementScore();
            send(producer,created);
            await().during(Duration.ofMillis(300)).atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(
                            profiles.findById(user).orElseThrow().getTotalEngagementScore())
                            .isEqualTo(score));
            var gap=fact(progress,user,book,2,ReadingProgressChangedEvent.Kind.UPDATED);
            send(producer,gap);
            var dead=dlt.awaitRecord(dltTopic,progress.toString(),Duration.ofSeconds(30),Duration.ofMillis(200));
            assertThat(dead.value()[0]).isEqualTo((byte)0);
            assertThat(ByteBuffer.wrap(dead.value(),1,4).getInt()).isPositive();
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-source-topic")).containsExactly(topic);
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-consumer-group"))
                    .containsExactly(KafkaConsumerGroups.RECOMMENDATION_READING_PROGRESS_AVRO);
            checkpoint(progress,0);
            assertThat(profiles.findById(user).orElseThrow().getTotalEngagementScore()).isEqualTo(score);
        }
    }

    private void checkpoint(UUID aggregate,long revision) {
        await().ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
                .atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(
                    jdbc.queryForObject("SELECT last_revision FROM reading_progress_offsets WHERE aggregate_id=?",
                            Long.class,aggregate)).isEqualTo(revision));
    }

    private static ReadingProgressChangedEvent fact(UUID progress,UUID user,UUID book,long revision,
            ReadingProgressChangedEvent.Kind kind) {
        return new ReadingProgressChangedEvent(UUID.randomUUID(),Instant.now(),progress,user,book,
                "READING",Math.max(0,(int)revision-1),Math.max(0,(int)revision+1),revision,kind);
    }

    private static KafkaProducer<String,Object> producer() {
        var cfg=new HashMap<String,Object>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,KafkaAvroSerializer.class);
        cfg.put("schema.registry.url",registryUrl());
        cfg.put("auto.register.schemas",false);
        cfg.put("use.latest.version",true);
        cfg.put("latest.compatibility.strict",true);
        cfg.put("avro.remove.java.properties",true);
        cfg.put("value.subject.name.strategy","io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return new KafkaProducer<>(cfg);
    }

    private static void send(KafkaProducer<String,Object> producer,ReadingProgressChangedEvent fact)
            throws Exception {
        producer.send(new ProducerRecord<>(KafkaTopics.READING_PROGRESS_AVRO,
                fact.bookProgressId().toString(),ReadingProgressAvroMapper.toAvro(fact)))
                .get(15,TimeUnit.SECONDS);
    }
}
