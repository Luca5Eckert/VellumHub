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

/** Real PostgreSQL Outbox -> Kafka Avro -> two distinct Apicurio subjects. */
@Tag("distributed")
@Tag("engagement-avro-e2e")
class EngagementAvroOutboxDistributedTest extends KafkaIntegrationTestSupport {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:15-alpine");
    @Container static final GenericContainer<?> REGISTRY = new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void sameTopicStrategyKeepsTypesDistinctAndNeverAutoRegisters() throws Exception {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc=new JdbcTemplate(ds);
        String registry="http://"+REGISTRY.getHost()+":"+REGISTRY.getMappedPort(8080)+"/apis/ccompat/v7";
        int ratingSchema=register(registry, "RatingCreatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO);
        int reactionSchema=register(registry, "ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS_AVRO);
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        var rating=new RatingCreatedEvent(UUID.randomUUID(),Instant.now(),890001L,user,book,5,0L);
        var reaction=new ReactionChangedEvent(UUID.randomUUID(),Instant.now(),890002L,user,book,
                null,"POSITIVE","POSITIVE",0L);
        var writer=new JdbcOutboxWriter(jdbc);
        writer.append(new OutboxMessage(rating.eventId(),"RATING",rating.ratingId().toString(),0L,
                "RatingCreatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user.toString(),rating,"AVRO",1));
        writer.append(new OutboxMessage(reaction.eventId(),"REACTION",reaction.reactionId().toString(),0L,
                "ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,user.toString(),reaction,"AVRO",1));
        var config=new HashMap<String,Object>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,KAFKA.getBootstrapServers());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,JsonSerializer.class);
        var factory=new DefaultKafkaProducerFactory<String,Object>(config);
        try (var avro=new EngagementAvroPublisher(factory,registry);
             var ratings=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),KafkaTopics.ENGAGEMENT_RATINGS_AVRO,"ratings-avro-probe");
             var reactions=KafkaProbe.subscribe(KAFKA.getBootstrapServers(),KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,"reactions-avro-probe")) {
            var dispatcher=new OutboxDispatcher(new JdbcOutboxStore(jdbc),
                    new KafkaOutboxTransport(new KafkaTemplate<>(factory),avro),new SimpleMeterRegistry());
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                dispatcher.dispatch();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE status='PUBLISHED'",
                        Long.class)).isEqualTo(2L);
            });
            var first=ratings.awaitRecord(KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user.toString(),
                    ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
            var second=reactions.awaitRecord(KafkaTopics.ENGAGEMENT_REACTIONS_AVRO,user.toString(),
                    ASYNC_TIMEOUT,ASYNC_POLL_INTERVAL);
            assertThat(first.value()[0]).isZero();
            assertThat(second.value()[0]).isZero();
            assertThat(ByteBuffer.wrap(first.value(),1,4).getInt()).isEqualTo(ratingSchema);
            assertThat(ByteBuffer.wrap(second.value(),1,4).getInt()).isEqualTo(reactionSchema);
            try (var decoder=new EngagementAvroDecoder(registry)) {
                assertThat(decoder.decode(first.topic(),first.key(),first.value())).isEqualTo(rating);
                assertThat(decoder.decode(second.topic(),second.key(),second.value())).isEqualTo(reaction);
            }
            assertThat(jdbc.queryForObject(
                    "SELECT wire_format FROM outbox_events WHERE event_id=?",String.class,rating.eventId()))
                    .isEqualTo("AVRO");
        } finally {factory.destroy();}
    }

    @Test void aFrozenRatingDoesNotFreezeReactionsAndJsonHistoryRemainsJson() {
        var ds=new DriverManagerDataSource(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var jdbc=new JdbcTemplate(ds);
        var writer=new JdbcOutboxWriter(jdbc);
        UUID user=UUID.randomUUID(),book=UUID.randomUUID();
        var rating=new RatingCreatedEvent(UUID.randomUUID(),Instant.EPOCH,990001L,user,book,5,0L);
        var reaction=new ReactionChangedEvent(UUID.randomUUID(),Instant.EPOCH,990002L,user,book,
                null,"POSITIVE","POSITIVE",0L);
        jdbc.update("UPDATE engagement_write_gate SET frozen=true WHERE aggregate_type='RATING'");
        try {
            var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
            assertThatThrownBy(() -> tx.executeWithoutResult(status ->
                writer.append(new OutboxMessage(rating.eventId(),"RATING","990001",0,
                    "RatingCreatedEvent",KafkaTopics.ENGAGEMENT_RATINGS_AVRO,user.toString(),rating,"AVRO",1))))
                    .isInstanceOf(EngagementWritesFrozenException.class);
            tx.executeWithoutResult(status -> writer.append(new OutboxMessage(reaction.eventId(),
                    "REACTION","990002",0,"ReactionChangedEvent",KafkaTopics.ENGAGEMENT_REACTIONS,
                    user.toString(),reaction)));
            assertThat(jdbc.queryForObject("SELECT wire_format FROM outbox_events WHERE event_id=?",
                    String.class,reaction.eventId())).isEqualTo("JSON");
        } finally {
            jdbc.update("UPDATE engagement_write_gate SET frozen=false WHERE aggregate_type='RATING'");
        }
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
