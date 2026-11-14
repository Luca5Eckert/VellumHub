package com.vellumhub.recommendation_service.module.user_profile.infrastructure.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.specific.SpecificRecord;
import com.vellumhub.kafka.avro.EngagementAvroMapper;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
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

/** Real Kafka + Apicurio + PostgreSQL UserProfile and unchanged transaction checkpoints. */
@Tag("distributed")
@Tag("engagement-avro-e2e")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK, properties={
        "app.kafka.legacy-consumers.enabled=false",
        "app.engagement.ratings.avro.consumer-enabled=true",
        "app.engagement.reactions.avro.consumer-enabled=true",
        "app.kafka.recovery.max-retries=0",
        "spring.kafka.listener.concurrency=1"})
class EngagementAvroRecommendationDistributedTest extends DistributedIntegrationTestSupport {
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON=new ObjectMapper();

    @DynamicPropertySource static void registry(DynamicPropertyRegistry registry) {
        registry.add("app.engagement.avro.registry-url",EngagementAvroRecommendationDistributedTest::url);
    }

    private static String url() {
        return "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
    }

    @BeforeAll static void registerContracts() throws Exception {
        for (String name : List.of("RatingCreatedEvent","RatingUpdatedEvent","RatingDeletedEvent",
                "RatingBaselineEvent","ReactionChangedEvent","ReactionBaselineEvent")) {
            register(name,name.startsWith("Rating")?KafkaTopics.ENGAGEMENT_RATINGS_AVRO:
                    KafkaTopics.ENGAGEMENT_REACTIONS_AVRO);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired BookFeatureRepository features;
    @Autowired UserProfileRepository profiles;
    @MockitoBean EmbeddingBookProvider embeddings;

    @Test void allSixFactsShareOriginalCheckpointsAndGapRetainsRawWireInDlt() throws Exception {
        var vector=new float[384]; vector[0]=1.0f;
        when(embeddings.of(anyString(),anyString(),anyString(),anyList())).thenReturn(vector);
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        features.save(BookFeature.create(book,vector,1.0));
        try (var producer=producer();
             var dlt=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_RATINGS_AVRO+".recommendation-service-dlt",
                     "engagement-avro-dlt-test")) {

            var ratingBaseline=new RatingBaselineEvent(UUID.randomUUID(),Instant.now(),
                    820001L,user,book,7L);
            send(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user,ratingBaseline);
            checkpoint("RATING",820001L,7L);
            assertThat(profiles.findById(user)).isEmpty();

            var reactionBaseline=new ReactionBaselineEvent(UUID.randomUUID(),Instant.now(),
                    820002L,user,book,4L);
            send(producer,KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,user,reactionBaseline);
            checkpoint("REACTION",820002L,4L);
            assertThat(profiles.findById(user)).isEmpty();

            long ratingId=820003L;
            var created=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),ratingId,
                    user,book,4,0L);
            send(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user,created);
            checkpoint("RATING",ratingId,0L);
            double initial=profiles.findById(user).orElseThrow().getTotalEngagementScore();

            var updated=new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),ratingId,
                    user,book,4,5,false,1L);
            send(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user,updated);
            checkpoint("RATING",ratingId,1L);

            long reactionId=820004L;
            var reaction=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),
                    reactionId,user,book,null,"POSITIVE","POSITIVE",0L);
            send(producer,KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,user,reaction);
            checkpoint("REACTION",reactionId,0L);
            var transition=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),
                    reactionId,user,book,"POSITIVE","VERY_POSITIVE","VERY_POSITIVE",1L);
            send(producer,KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,user,transition);
            checkpoint("REACTION",reactionId,1L);

            var deleted=new RatingDeletedEvent(UUID.randomUUID(),Instant.now(),
                    ratingId,user,book,5,2L);
            send(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user,deleted);
            checkpoint("RATING",ratingId,2L);
            double finalScore=profiles.findById(user).orElseThrow().getTotalEngagementScore();
            assertThat(finalScore).isNotEqualTo(initial);

            send(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user,deleted);
            await().during(Duration.ofMillis(250)).atMost(Duration.ofSeconds(3))
                    .untilAsserted(() -> assertThat(
                            profiles.findById(user).orElseThrow().getTotalEngagementScore())
                            .isEqualTo(finalScore));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_engagement_events WHERE event_id=?",
                    Long.class,deleted.eventId())).isEqualTo(1L);

            // Real v2 Avro schema IDs are resolved into the deployed v1 generated reader.
            UUID v2RatingUser=UUID.randomUUID(),v2ReactionUser=UUID.randomUUID();
            assertThat(profiles.findById(v2RatingUser)).isEmpty();
            var ratingV2=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),820005L,
                    v2RatingUser,book,4,0L);
            sendCompatibleV2(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,v2RatingUser,ratingV2);
            checkpoint("RATING",820005L,0L);
            assertThat(profiles.findById(v2RatingUser)).isPresent();

            assertThat(profiles.findById(v2ReactionUser)).isEmpty();
            var reactionV2=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),
                    820006L,v2ReactionUser,book,null,"POSITIVE","POSITIVE",0L);
            sendCompatibleV2(producer,KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,v2ReactionUser,reactionV2);
            checkpoint("REACTION",820006L,0L);
            assertThat(profiles.findById(v2ReactionUser)).isPresent();

            var gap=new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),ratingId,
                    user,book,2,1,false,4L);
            send(producer,KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user,gap);
            var dead=dlt.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS_AVRO+
                    ".recommendation-service-dlt",user.toString(),Duration.ofSeconds(30),Duration.ofMillis(200));
            assertThat(dead.value()[0]).isEqualTo((byte)0);
            assertThat(ByteBuffer.wrap(dead.value(),1,4).getInt()).isPositive();
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-source-topic"))
                    .containsExactly(KafkaTopics.ENGAGEMENT_RATINGS_AVRO);
            assertThat(KafkaProbe.utf8HeaderValues(dead,"x-vellum-consumer-group"))
                    .containsExactly(KafkaConsumerGroups.RECOMMENDATION_RATINGS_AVRO);
            checkpoint("RATING",ratingId,2L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_engagement_events WHERE event_id=?",
                    Long.class,gap.eventId())).isZero();
        }
    }

    private void checkpoint(String aggregate,long id,long revision) {
        await().ignoreException(org.springframework.dao.EmptyResultDataAccessException.class)
                .atMost(Duration.ofSeconds(25)).untilAsserted(() -> assertThat(
                        jdbc.queryForObject("SELECT last_revision FROM engagement_event_offsets WHERE aggregate_type=? AND aggregate_id=?",
                                Long.class,aggregate,Long.toString(id))).isEqualTo(revision));
    }

    /** Compatible v2 GenericRecord bytes must update the original v1 consumer's read model. */
    private static void sendCompatibleV2(KafkaProducer<String,Object> producer,
            String topic,UUID user,Object fact) throws Exception {
        SpecificRecord v1=(SpecificRecord)EngagementAvroMapper.toAvro(fact);
        ObjectNode evolved=(ObjectNode)JSON.readTree(v1.getSchema().toString());
        var added=JSON.createObjectNode();
        added.put("name","contractNote");
        var alternatives=JSON.createArrayNode();
        alternatives.add("null");
        alternatives.add("string");
        added.set("type",alternatives);
        added.putNull("default");
        ((ArrayNode)evolved.get("fields")).add(added);
        Schema v2=new Schema.Parser().parse(evolved.toString());
        String subject=topic+"-"+v2.getFullName();
        String id=URLEncoder.encode(subject,StandardCharsets.UTF_8);
        String body=JSON.writeValueAsString(Map.of("schemaType","AVRO",
                "schema",JSON.writeValueAsString(JSON.readTree(v2.toString()))));
        var response=HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url()+"/subjects/"+id+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("v1 -> v2 registration: "+response.body()).isEqualTo(200);
        var wire=new GenericData.Record(v2);
        for(Schema.Field field:v1.getSchema().getFields())
            wire.put(field.name(),v1.get(field.pos()));
        wire.put("contractNote","compatible-v2");
        producer.send(new ProducerRecord<>(topic,user.toString(),wire)).get(15,TimeUnit.SECONDS);
    }

    private static KafkaProducer<String,Object> producer() {
        var config=new HashMap<String,Object>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,KafkaAvroSerializer.class);
        config.put("schema.registry.url",url());
        config.put("auto.register.schemas",false);
        config.put("use.latest.version",true);
        config.put("latest.compatibility.strict",true);
        config.put("avro.remove.java.properties",true);
        config.put("value.subject.name.strategy",
                "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");
        return new KafkaProducer<>(config);
    }

    private static void send(KafkaProducer<String,Object> producer,String topic,UUID user,Object event)
            throws Exception {
        producer.send(new ProducerRecord<>(topic,user.toString(),EngagementAvroMapper.toAvro(event)))
                .get(15,TimeUnit.SECONDS);
    }

    private static void register(String name,String topic) throws Exception {
        try (InputStream stream=EngagementAvroRecommendationDistributedTest.class
                .getResourceAsStream("/"+name+".avsc")) {
            assertThat(stream).isNotNull();
            String raw=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            String path=URLEncoder.encode(topic+"-com.vellumhub.kafka.avro.engagement."+name,
                    StandardCharsets.UTF_8);
            var client=HttpClient.newHttpClient();
            var policy=HttpRequest.newBuilder(URI.create(url()+"/config/"+path))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}")).build();
            assertThat(client.send(policy,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            var payload=JSON.writeValueAsString(Map.of("schemaType","AVRO",
                    "schema",JSON.writeValueAsString(JSON.readTree(raw))));
            var request=HttpRequest.newBuilder(URI.create(url()+"/subjects/"+path+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var result=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
        }
    }
}
