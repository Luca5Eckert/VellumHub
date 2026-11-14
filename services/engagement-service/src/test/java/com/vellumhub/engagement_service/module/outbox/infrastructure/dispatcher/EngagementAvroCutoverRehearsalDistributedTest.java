package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
import com.vellumhub.engagement_service.module.outbox.application.exception.EngagementWritesFrozenException;
import com.vellumhub.engagement_service.module.outbox.infrastructure.kafka.*;
import com.vellumhub.engagement_service.module.outbox.infrastructure.persistence.*;
import com.vellumhub.kafka.avro.EngagementAvroDecoder;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.*;
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


/** Real pre-V10 JSON rows -> PostgreSQL drain -> independently fenced Avro -> JSON rollback. */
@Tag("distributed")
@Tag("engagement-avro-e2e")
class EngagementAvroCutoverRehearsalDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB =
            new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
                    .withExposedPorts(8080)
                    .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void eachEngagementStreamDrainsPreV10JsonAndRollsBackWithSameIdentity() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        var jdbc = new JdbcTemplate(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .target("9").load().migrate();
        UUID user = UUID.randomUUID(), book = UUID.randomUUID();
        long ratingId = 985001L,reactionId = 985002L;
        var rating0 = new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),
                ratingId,user,book,3,0L);
        var reaction0 = new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),
                reactionId,user,book,null,"POSITIVE","POSITIVE",0L);
        var mapper = com.fasterxml.jackson.databind.json.JsonMapper.builder()
                .findAndAddModules().build();
        jdbc.update("""
                INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
                    event_type,topic,message_key,payload)
                VALUES (?,'RATING',?,0,'RatingCreatedEvent',?,?,CAST(? AS jsonb))
                """,rating0.eventId(),Long.toString(ratingId),KafkaTopics.ENGAGEMENT_RATINGS,
                user.toString(),mapper.writeValueAsString(rating0));
        jdbc.update("""
                INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
                    event_type,topic,message_key,payload)
                VALUES (?,'REACTION',?,0,'ReactionChangedEvent',?,?,CAST(? AS jsonb))
                """,reaction0.eventId(),Long.toString(reactionId),KafkaTopics.ENGAGEMENT_REACTIONS,
                user.toString(),mapper.writeValueAsString(reaction0));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        for (UUID id : List.of(rating0.eventId(),reaction0.eventId())) {
            assertThat(jdbc.queryForMap("""
                    SELECT wire_format,payload_schema_version,payload->>'eventId' AS event_id
                    FROM outbox_events WHERE event_id=?
                    """,id)).containsEntry("wire_format","JSON")
                            .containsEntry("payload_schema_version",1)
                            .containsEntry("event_id",id.toString());
        }

        String registry = "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)
                +"/apis/ccompat/v7";
        int ratingSchema = register(registry,"RatingUpdatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO);
        int reactionSchema = register(registry,"ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS_AVRO);
        var cfg = new HashMap<String,Object>();
        cfg.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        cfg.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        cfg.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        var factory = new DefaultKafkaProducerFactory<String,Object>(cfg);
        var writer = new JdbcOutboxWriter(jdbc);
        var tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
        try (var avroPublisher = new EngagementAvroPublisher(factory,registry);
             var legacyRatings = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_RATINGS,"rating-cutover-json-"+user);
             var legacyReactions = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_REACTIONS,"reaction-cutover-json-"+user);
             var avroRatings = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_RATINGS_AVRO,"rating-cutover-avro-"+user);
             var avroReactions = KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,"reaction-cutover-avro-"+user)) {
            var dispatcher = new OutboxDispatcher(new JdbcOutboxStore(jdbc),
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory),avroPublisher),
                    new SimpleMeterRegistry());
            freeze(jdbc,"RATING",true);
            freeze(jdbc,"REACTION",true);
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> writer.append(
                    new OutboxMessage(UUID.randomUUID(),"RATING",Long.toString(ratingId),1L,
                            "RatingUpdatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO,
                            user.toString(),new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),
                            ratingId,user,book,3,4,false,1L),"AVRO",1))))
                    .isInstanceOf(EngagementWritesFrozenException.class);

            // Existing rows stay JSON while *both* producer types are frozen.
            dispatcher.dispatch();
            awaitPublished(jdbc,rating0.eventId());
            awaitPublished(jdbc,reaction0.eventId());
            var legacyRating = legacyRatings.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS,
                    user.toString(),ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
            var legacyReaction = legacyReactions.awaitRecord(KafkaTopics.ENGAGEMENT_REACTIONS,
                    user.toString(),ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
            assertThat(JSON.readTree(legacyRating.value()).path("eventId").asText())
                    .isEqualTo(rating0.eventId().toString());
            assertThat(JSON.readTree(legacyReaction.value()).path("eventId").asText())
                    .isEqualTo(reaction0.eventId().toString());
            assertThat(pending(jdbc,"RATING",ratingId)).isZero();
            assertThat(pending(jdbc,"REACTION",reactionId)).isZero();

            var rating1 = new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),
                    ratingId,user,book,3,4,false,1L);
            var reaction1 = new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),
                    reactionId,user,book,"POSITIVE","VERY_POSITIVE","VERY_POSITIVE",1L);
            // Rating may reopen while Reaction remains fenced; cutovers are independent.
            freeze(jdbc,"RATING",false);
            tx.executeWithoutResult(s -> writer.append(message(rating1,"RATING",
                    ratingId,1L,"RatingUpdatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO,
                    user,"AVRO")));
            freeze(jdbc,"RATING",true);
            assertThat(jdbc.queryForObject(
                    "SELECT frozen FROM engagement_write_gate WHERE aggregate_type='REACTION'",
                    Boolean.class)).isTrue();
            freeze(jdbc,"REACTION",false);
            tx.executeWithoutResult(s -> writer.append(message(reaction1,"REACTION",
                    reactionId,1L,"ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,
                    user,"AVRO")));
            freeze(jdbc,"REACTION",true);
            dispatcher.dispatch();
            awaitPublished(jdbc,rating1.eventId());
            awaitPublished(jdbc,reaction1.eventId());
            var ratingFrame = avroRatings.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS_AVRO,
                    user.toString(),ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
            var reactionFrame = avroReactions.awaitRecord(KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,
                    user.toString(),ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
            assertThat(ratingFrame.value()[0]).isZero();
            assertThat(reactionFrame.value()[0]).isZero();
            assertThat(ByteBuffer.wrap(ratingFrame.value(),1,4).getInt()).isEqualTo(ratingSchema);
            assertThat(ByteBuffer.wrap(reactionFrame.value(),1,4).getInt()).isEqualTo(reactionSchema);
            try (var decoder = new EngagementAvroDecoder(registry)) {
                assertThat(decoder.decode(ratingFrame.topic(),ratingFrame.key(),ratingFrame.value()))
                        .isEqualTo(rating1);
                assertThat(decoder.decode(reactionFrame.topic(),reactionFrame.key(),reactionFrame.value()))
                        .isEqualTo(reaction1);
            }
            assertThat(pending(jdbc,"RATING",ratingId)).isZero();
            assertThat(pending(jdbc,"REACTION",reactionId)).isZero();

            // Restore JSON writers only after the corresponding Avro rows are ACKed.
            var rating2 = new RatingDeletedEvent(UUID.randomUUID(),Instant.now(),
                    ratingId,user,book,4,2L);
            var reaction2 = new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),
                    reactionId,user,book,"VERY_POSITIVE","POSITIVE","POSITIVE",2L);
            freeze(jdbc,"RATING",false);
            tx.executeWithoutResult(s -> writer.append(message(rating2,"RATING",
                    ratingId,2L,"RatingDeletedEvent",KafkaTopics.ENGAGEMENT_RATINGS,
                    user,"JSON")));
            freeze(jdbc,"RATING",true);
            freeze(jdbc,"REACTION",false);
            tx.executeWithoutResult(s -> writer.append(message(reaction2,"REACTION",
                    reactionId,2L,"ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS,
                    user,"JSON")));
            freeze(jdbc,"REACTION",true);
            dispatcher.dispatch();
            awaitPublished(jdbc,rating2.eventId());
            awaitPublished(jdbc,reaction2.eventId());
            assertThat(JSON.readTree(legacyRatings.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS,
                    user.toString(),ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL).value())
                    .path("eventId").asText()).isEqualTo(rating2.eventId().toString());
            assertThat(JSON.readTree(legacyReactions.awaitRecord(KafkaTopics.ENGAGEMENT_REACTIONS,
                    user.toString(),ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL).value())
                    .path("eventId").asText()).isEqualTo(reaction2.eventId().toString());
            for (String type : List.of("RATING","REACTION")) {
                long id = type.equals("RATING") ? ratingId : reactionId;
                String jsonTopic = type.equals("RATING") ?
                        KafkaTopics.ENGAGEMENT_RATINGS : KafkaTopics.ENGAGEMENT_REACTIONS;
                String avroTopic = type.equals("RATING") ?
                        KafkaTopics.ENGAGEMENT_RATINGS_AVRO : KafkaTopics.ENGAGEMENT_REACTIONS_AVRO;
                assertThat(pending(jdbc,type,id)).isZero();
                assertThat(count(jdbc,type,id,"JSON",jsonTopic)).isEqualTo(2L);
                assertThat(count(jdbc,type,id,"AVRO",avroTopic)).isEqualTo(1L);
            }
        } finally {
            freeze(jdbc,"RATING",false);
            freeze(jdbc,"REACTION",false);
            factory.destroy();
        }
    }

    private static OutboxMessage message(Object event,String type,long id,long version,
            String name,String topic,UUID user,String wire) {
        UUID eventId;
        if (event instanceof RatingUpdatedEvent changed) eventId = changed.eventId();
        else if (event instanceof RatingDeletedEvent removed) eventId = removed.eventId();
        else if (event instanceof ReactionChangedEvent reaction) eventId = reaction.eventId();
        else throw new IllegalArgumentException("Unexpected event "+event.getClass().getName());
        return new OutboxMessage(eventId,type,Long.toString(id),version,name,topic,
                user.toString(),event,wire,1);
    }
    private static void freeze(JdbcTemplate jdbc,String type,boolean frozen) {
        jdbc.update("UPDATE engagement_write_gate SET frozen=?,updated_at=now() WHERE aggregate_type=?",
                frozen,type);
    }
    private static long pending(JdbcTemplate jdbc,String type,long id) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_events WHERE aggregate_type=? AND aggregate_id=?
                AND status <> 'PUBLISHED'
                """,Long.class,type,Long.toString(id));
    }
    private static long count(JdbcTemplate jdbc,String type,long id,String wire,String topic) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM outbox_events WHERE aggregate_type=? AND aggregate_id=?
                AND status='PUBLISHED' AND wire_format=? AND topic=?
                """,Long.class,type,Long.toString(id),wire,topic);
    }
    private static void awaitPublished(JdbcTemplate jdbc,UUID eventId) {
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(jdbc.queryForObject(
                        "SELECT status FROM outbox_events WHERE event_id=?",
                        String.class,eventId)).isEqualTo("PUBLISHED"));
    }

    private static int register(String url,String name,String topic) throws Exception {
        try (InputStream stream=EngagementAvroOutboxDistributedTest.class.getResourceAsStream("/"+name+".avsc")) {
            assertThat(stream).isNotNull();
            String raw=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            String subject=topic+"-com.vellumhub.kafka.avro.engagement."+name;
            String path=URLEncoder.encode(subject,StandardCharsets.UTF_8);
            var client=HttpClient.newHttpClient();
            var policy=HttpRequest.newBuilder(URI.create(url+"/config/"+path))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}")).build();
            assertThat(client.send(policy,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            String payload=JSON.writeValueAsString(Map.of("schemaType","AVRO",
                    "schema",JSON.writeValueAsString(JSON.readTree(raw))));
            var request=HttpRequest.newBuilder(URI.create(url+"/subjects/"+path+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var result=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
            return JSON.readTree(result.body()).path("id").asInt(-1);
        }
    }
}
