package com.vellumhub.engagement_service.module.outbox.infrastructure.dispatcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.vellumhub.engagement_service.module.outbox.application.exception.EngagementWritesFrozenException;
import com.vellumhub.engagement_service.module.outbox.application.model.OutboxMessage;
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

/** Two independent V9->V10 cutovers over the real PostgreSQL Outbox, Registry and Kafka. */
@Tag("distributed")
@Tag("engagement-avro-e2e")
class EngagementAvroCutoverRehearsalDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY =
            new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON = new ObjectMapper();

    private record Stream(String type,String id,String oldTopic,String avroTopic,String key,
            Object before,UUID beforeId,Object changed,UUID changedId,Object restored,UUID restoredId) {}

    @Test void bothStreamsDrainJsonAndCanRollBackIndependentlyWithoutDuplicateFacts() throws Exception {
        var ds = new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("9").load().migrate();
        var jdbc = new JdbcTemplate(ds);
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        long ratingId=940001L,reactionId=940002L;
        var ratingBefore=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),ratingId,user,book,4,0L);
        var ratingChanged=new RatingUpdatedEvent(UUID.randomUUID(),Instant.now(),ratingId,user,book,4,5,false,1L);
        var ratingRestored=new RatingDeletedEvent(UUID.randomUUID(),Instant.now(),ratingId,user,book,5,2L);
        var reactionBefore=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),reactionId,
                user,book,null,"POSITIVE","POSITIVE",0L);
        var reactionChanged=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),reactionId,
                user,book,"POSITIVE","VERY_POSITIVE","VERY_POSITIVE",1L);
        var reactionRestored=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),reactionId,
                user,book,"VERY_POSITIVE","POSITIVE","POSITIVE",2L);
        var rating=new Stream("RATING",Long.toString(ratingId),KafkaTopics.ENGAGEMENT_RATINGS,
                KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user.toString(),ratingBefore,ratingBefore.eventId(),
                ratingChanged,ratingChanged.eventId(),ratingRestored,ratingRestored.eventId());
        var reaction=new Stream("REACTION",Long.toString(reactionId),KafkaTopics.ENGAGEMENT_REACTIONS,
                KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,user.toString(),reactionBefore,reactionBefore.eventId(),
                reactionChanged,reactionChanged.eventId(),reactionRestored,reactionRestored.eventId());
        var jsonMapper = JsonMapper.builder().findAndAddModules().build();
        for (var stream:List.of(rating,reaction)) {
            // Binary from before V10: historical rows have no explicit wire_format column.
            jdbc.update("""
                    INSERT INTO outbox_events(event_id,aggregate_type,aggregate_id,aggregate_version,
                        event_type,topic,message_key,payload)
                    VALUES (?,?,?,0,?,?,?,CAST(? AS jsonb))
                    """,stream.beforeId(),stream.type(),stream.id(),stream.before().getClass().getSimpleName(),
                    stream.oldTopic(),stream.key(),jsonMapper.writeValueAsString(stream.before()));
        }
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        for(var stream:List.of(rating,reaction))
            assertThat(jdbc.queryForMap(
                    "SELECT wire_format,payload_schema_version FROM outbox_events WHERE event_id=?",
                    stream.beforeId())).containsEntry("wire_format","JSON")
                    .containsEntry("payload_schema_version",1);

        var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
        var writer=new JdbcOutboxWriter(jdbc);
        var config=new HashMap<String,Object>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        var factory=new DefaultKafkaProducerFactory<String,Object>(config);
        String registry=url();
        int ratingSchema=register(registry,"RatingUpdatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO);
        int reactionSchema=register(registry,"ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS_AVRO);
        try (var avro=new EngagementAvroPublisher(factory,registry);
             var oldRating=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_RATINGS,"rating-old-cutover");
             var newRating=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_RATINGS_AVRO,"rating-avro-cutover");
             var oldReaction=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_REACTIONS,"reaction-old-cutover");
             var newReaction=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),
                     KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,"reaction-avro-cutover")) {
            var dispatcher=new OutboxDispatcher(new JdbcOutboxStore(jdbc),
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory),avro),new SimpleMeterRegistry());

            // Freeze each writer, drain both historical JSON rows before any wire change.
            freeze(jdbc,"RATING",true);
            freeze(jdbc,"REACTION",true);
            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                    writer.append(msg(rating,rating.changed(),rating.changedId(),1,true))))
                    .isInstanceOf(EngagementWritesFrozenException.class);
            assertThat(pending(jdbc,"RATING")).isEqualTo(1L);
            assertThat(pending(jdbc,"REACTION")).isEqualTo(1L);
            dispatcher.dispatch();
            published(jdbc,rating.beforeId());
            published(jdbc,reaction.beforeId());
            var firstRating=oldRating.awaitRecord(rating.oldTopic(),rating.key(),
                    Duration.ofSeconds(20),Duration.ofMillis(150));
            var firstReaction=oldReaction.awaitRecord(reaction.oldTopic(),reaction.key(),
                    Duration.ofSeconds(20),Duration.ofMillis(150));
            assertThat(firstRating.value()[0]).isEqualTo((byte)'{');
            assertThat(firstReaction.value()[0]).isEqualTo((byte)'{');
            assertThat(pending(jdbc,"RATING")).isZero();
            assertThat(pending(jdbc,"REACTION")).isZero();

            for(var stream:List.of(rating,reaction)) {
                // Writer gate is independent; no data from an earlier route is reserialized.
                freeze(jdbc,stream.type(),false);
                tx.executeWithoutResult(status -> writer.append(
                        msg(stream,stream.changed(),stream.changedId(),1,true)));
                freeze(jdbc,stream.type(),true);
                dispatcher.dispatch();
                published(jdbc,stream.changedId());
                assertThat(jdbc.queryForMap("SELECT wire_format,topic FROM outbox_events WHERE event_id=?",
                        stream.changedId())).containsEntry("wire_format","AVRO")
                        .containsEntry("topic",stream.avroTopic());
                assertThat(pending(jdbc,stream.type())).isZero();

                // Drained: restore JSON routing while keeping the original Avro-stamped row.
                freeze(jdbc,stream.type(),false);
                tx.executeWithoutResult(status -> writer.append(
                        msg(stream,stream.restored(),stream.restoredId(),2,false)));
                freeze(jdbc,stream.type(),true);
                dispatcher.dispatch();
                published(jdbc,stream.restoredId());
                assertThat(pending(jdbc,stream.type())).isZero();
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM outbox_events
                        WHERE aggregate_type=? AND status='PUBLISHED'
                        """,Long.class,stream.type())).isEqualTo(3L);
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM outbox_events
                        WHERE aggregate_type=? AND wire_format='AVRO' AND status='PUBLISHED'
                            AND topic=?
                        """,Long.class,stream.type(),stream.avroTopic())).isEqualTo(1L);
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM outbox_events
                        WHERE aggregate_type=? AND wire_format='JSON' AND status='PUBLISHED'
                            AND topic=?
                        """,Long.class,stream.type(),stream.oldTopic())).isEqualTo(2L);
                assertThat(jdbc.queryForObject("""
                        SELECT payload->>'eventId' FROM outbox_events WHERE event_id=?
                        """,String.class,stream.changedId())).isEqualTo(stream.changedId().toString());
            }

            var rAvro=newRating.awaitRecord(rating.avroTopic(),rating.key(),
                    Duration.ofSeconds(20),Duration.ofMillis(150));
            var xAvro=newReaction.awaitRecord(reaction.avroTopic(),reaction.key(),
                    Duration.ofSeconds(20),Duration.ofMillis(150));
            assertThat(rAvro.value()[0]).isZero();
            assertThat(xAvro.value()[0]).isZero();
            assertThat(ByteBuffer.wrap(rAvro.value(),1,4).getInt()).isEqualTo(ratingSchema);
            assertThat(ByteBuffer.wrap(xAvro.value(),1,4).getInt()).isEqualTo(reactionSchema);
            try(var decoder=new EngagementAvroDecoder(registry)) {
                assertThat(decoder.decode(rAvro.topic(),rAvro.key(),rAvro.value()))
                        .isEqualTo(ratingChanged);
                assertThat(decoder.decode(xAvro.topic(),xAvro.key(),xAvro.value()))
                        .isEqualTo(reactionChanged);
            }
            assertThat(oldRating.awaitRecord(rating.oldTopic(),rating.key(),
                    Duration.ofSeconds(20),Duration.ofMillis(150)).value()[0]).isEqualTo((byte)'{');
            assertThat(oldReaction.awaitRecord(reaction.oldTopic(),reaction.key(),
                    Duration.ofSeconds(20),Duration.ofMillis(150)).value()[0]).isEqualTo((byte)'{');
        } finally {
            freeze(jdbc,"RATING",false);
            freeze(jdbc,"REACTION",false);
            factory.destroy();
        }
    }

    private static OutboxMessage msg(Stream stream,Object fact,UUID id,long revision,boolean avro) {
        return new OutboxMessage(id,stream.type(),stream.id(),revision,
                fact.getClass().getSimpleName(),avro?stream.avroTopic():stream.oldTopic(),
                stream.key(),fact,avro?"AVRO":"JSON",1);
    }
    private static void freeze(JdbcTemplate jdbc,String type,boolean frozen) {
        jdbc.update("UPDATE engagement_write_gate SET frozen=?,updated_at=now() WHERE aggregate_type=?",
                frozen,type);
    }
    private static long pending(JdbcTemplate jdbc,String type) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM outbox_events WHERE aggregate_type=? AND status<>'PUBLISHED'
                """,Long.class,type);
    }
    private static void published(JdbcTemplate jdbc,UUID id) {
        await().atMost(Duration.ofSeconds(25)).untilAsserted(() ->
                assertThat(jdbc.queryForObject("SELECT status FROM outbox_events WHERE event_id=?",
                        String.class,id)).isEqualTo("PUBLISHED"));
    }
    private static String url() {
        return "http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
    }
    private static int register(String registry,String recordName,String topic) throws Exception {
        try (InputStream in=EngagementAvroCutoverRehearsalDistributedTest.class
                .getResourceAsStream("/"+recordName+".avsc")) {
            assertThat(in).isNotNull();
            String subject=topic+"-com.vellumhub.kafka.avro.engagement."+recordName;
            String path=URLEncoder.encode(subject,StandardCharsets.UTF_8);
            var client=HttpClient.newHttpClient();
            var policy=HttpRequest.newBuilder(URI.create(registry+"/config/"+path))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .PUT(HttpRequest.BodyPublishers.ofString("{\"compatibility\":\"BACKWARD_TRANSITIVE\"}"))
                    .build();
            assertThat(client.send(policy,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
            String payload=JSON.writeValueAsString(Map.of("schemaType","AVRO",
                    "schema",JSON.writeValueAsString(JSON.readTree(in.readAllBytes()))));
            var request=HttpRequest.newBuilder(URI.create(registry+"/subjects/"+path+"/versions"))
                    .header("Content-Type","application/vnd.schemaregistry.v1+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            return JSON.readTree(response.body()).path("id").asInt(-1);
        }
    }
}
