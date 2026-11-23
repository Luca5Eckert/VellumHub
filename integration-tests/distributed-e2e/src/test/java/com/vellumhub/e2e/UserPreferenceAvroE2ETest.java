package com.vellumhub.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import com.vellumhub.e2e.support.RecommendationE2ETestSupport;
import com.vellumhub.kafka.avro.userpreference.UserPreferenceChangedEvent;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.recommendation_service.RecommendationServiceApplication;
import com.vellumhub.recommendation_service.module.user_profile.domain.port.EmbeddingUserProfileProvider;
import com.vellumhub.testing.distributed.avro.BookAvroFixture;
import com.vellumhub.testing.distributed.kafka.KafkaProbe;
import com.vellumhub.user_service.UserServiceApplication;
import com.vellumhub.user_service.module.user_preference.infrastructure.KafkaPreferenceOutbox;
import org.apache.avro.Schema;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("distributed")
@Tag("preference-avro-e2e")
@SpringBootTest(classes = RecommendationServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {"app.kafka.json-listeners.enabled=false", "app.kafka.legacy-consumers.enabled=false",
                "app.user-preference.json.consumer-enabled=false", "app.user-preference.avro.consumer-enabled=true"})
class UserPreferenceAvroE2ETest extends RecommendationE2ETestSupport {
    private static final String TOPIC = KafkaTopics.USER_PREFERENCE_EVENTS_AVRO;
    private static final String GROUP = KafkaConsumerGroups.RECOMMENDATION_PREFERENCES_AVRO;
    private static final String PREFERENCES_PATH = "/users/me/preferences";
    private static final Duration TIMEOUT = Duration.ofSeconds(25);
    private static final ObjectMapper JSON = new ObjectMapper();

    @Container
    static final PostgreSQLContainer<?> USER_DB = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static final GenericContainer<?> REGISTRY = new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080).waitingFor(Wait.forHttp("/apis/ccompat/v7/subjects").forStatusCode(200));

    @DynamicPropertySource
    static void registry(DynamicPropertyRegistry properties) {
        properties.add("app.user-preference.avro.registry-url", () -> BookAvroFixture.registryUrl(REGISTRY));
    }

    @Autowired JdbcTemplate recommendation;
    @MockitoBean EmbeddingUserProfileProvider embeddings;

    @Test
    void propagatesAuthenticatedWritesAndRecoversWithoutPublishingRolledBackOrDuplicatingEffects() throws Exception {
        var user = UUID.randomUUID();
        var access = new TestAccessToken(user);
        var vector = new float[384];
        vector[0] = 1;
        when(embeddings.of(anyList(), nullable(String.class))).thenReturn(vector);
        String broker = KAFKA.getBootstrapServers();
        String registryUrl = BookAvroFixture.registryUrl(REGISTRY);

        try (access;
             var http = HttpClient.newHttpClient();
             var admin = AdminClient.create(Map.of("bootstrap.servers", broker));
             var probe = KafkaProbe.subscribe(broker, TOPIC, "preference-http-e2e");
             var userApp = new SpringApplicationBuilder(UserServiceApplication.class)
                     .run("--spring.config.location=classpath:user/application.properties", "--server.port=0",
                             "--spring.datasource.url=" + USER_DB.getJdbcUrl(),
                             "--spring.datasource.username=" + USER_DB.getUsername(),
                             "--spring.datasource.password=" + USER_DB.getPassword(),
                             "--spring.flyway.locations=classpath:user/db/migration",
                             "--spring.jpa.hibernate.ddl-auto=validate",
                             "--spring.kafka.bootstrap-servers=" + broker,
                             "--app.preference-outbox.avro-enabled=true",
                             "--app.preference-outbox.registry-url=" + registryUrl,
                             "--app.preference-outbox.enabled=false",
                             "--JWT_ISSUER=" + access.issuer(),
                             "--JWT_JWKS_URI=" + access.issuer() + "/jwks")) {
            var userJdbc = userApp.getBean(JdbcTemplate.class);
            var outbox = userApp.getBean(KafkaPreferenceOutbox.class);
            int port = ((ServletWebServerApplicationContext) userApp).getWebServer().getPort();
            String base = "http://127.0.0.1:" + port;
            userJdbc.update("""
                    INSERT INTO tb_users(id, name, email, active, version, created_at)
                    VALUES (?, 'Preference fixture', 'preference@example.invalid', true, 0, now())
                    """, user);

            // Make the outbox insert fail after the preference has been staged in the same transaction.
            userJdbc.execute("ALTER TABLE user_preference_outbox ADD CONSTRAINT test_reject_write CHECK (revision < 0)");
            try {
                assertThat(put(http, base, access.token(), "Horror").statusCode()).isEqualTo(409);
                assertThat(userJdbc.queryForObject("SELECT count(*) FROM user_preferences WHERE user_id=?", Long.class, user)).isZero();
                assertThat(pending(userJdbc, user)).isZero();
            } finally {
                userJdbc.execute("ALTER TABLE user_preference_outbox DROP CONSTRAINT test_reject_write");
            }
            outbox.dispatchNext();
            assertThat(endOffsets(admin)).allMatch(offset -> offset == 0L);

            assertThat(put(http, base, access.token(), "Fantasy").statusCode()).isEqualTo(200);
            assertThat(put(http, base, access.token(), "History").statusCode()).isEqualTo(200);
            assertThat(pending(userJdbc, user)).isEqualTo(2);
            var read = http.send(HttpRequest.newBuilder(URI.create(base + PREFERENCES_PATH)).timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + access.token()).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(read.statusCode()).isEqualTo(200);
            assertThat(JSON.readTree(read.body()).path("genres")).isEqualTo(JSON.valueToTree(List.of("History")));

            // Broker is available, but the approved schema is not yet registered. The head must retry.
            outbox.dispatchNext();
            assertThat(userJdbc.queryForObject("""
                    SELECT next_attempt_at > occurred_at + interval '4 seconds'
                    FROM user_preference_outbox WHERE user_id=? AND revision=0
                    """, Boolean.class, user)).isTrue();
            assertThat(pending(userJdbc, user)).isEqualTo(2);
            assertThat(endOffsets(admin)).allMatch(offset -> offset == 0L);

            registerSchema(http, registryUrl);
            await().atMost(TIMEOUT).untilAsserted(() -> {
                outbox.dispatchNext();
                assertThat(pending(userJdbc, user)).isLessThan(2);
            });
            var first = probe.awaitRecord(TOPIC, user.toString(), TIMEOUT, Duration.ofMillis(200));
            await().atMost(TIMEOUT).untilAsserted(() -> assertCheckpoint(user, 0));
            verify(embeddings).of(List.of("Fantasy"), null);

            outbox.dispatchNext();
            await().atMost(TIMEOUT).untilAsserted(() -> assertCheckpoint(user, 1));
            assertThat(pending(userJdbc, user)).isZero();
            verify(embeddings).of(List.of("History"), null);

            try (var producer = new KafkaProducer<String, byte[]>(Map.of("bootstrap.servers", broker),
                    new StringSerializer(), new ByteArraySerializer())) {
                var duplicate = producer.send(new ProducerRecord<>(TOPIC, first.partition(), first.key(), first.value()))
                        .get(15, TimeUnit.SECONDS);
                var partition = new TopicPartition(TOPIC, duplicate.partition());
                await().atMost(TIMEOUT).untilAsserted(() -> {
                    var committed = admin.listConsumerGroupOffsets(GROUP)
                            .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS).get(partition);
                    assertThat(committed).isNotNull();
                    assertThat(committed.offset()).isGreaterThan(duplicate.offset());
                });
            }
            assertCheckpoint(user, 1);
            assertThat(recommendation.queryForObject(
                    "SELECT count(*) FROM processed_preference_events WHERE user_id=?", Long.class, user)).isEqualTo(2L);
            verify(embeddings, times(1)).of(List.of("Fantasy"), null);
            verify(embeddings, times(1)).of(List.of("History"), null);
        }
    }

    private void assertCheckpoint(UUID user, long revision) {
        assertThat(recommendation.queryForList(
                "SELECT last_revision FROM preference_event_offsets WHERE user_id=?", Long.class, user))
                .containsExactly(revision);
        assertThat(recommendation.queryForObject(
                "SELECT count(*) FROM user_profiles WHERE user_id=? AND profile_vector IS NOT NULL", Long.class, user))
                .isEqualTo(1L);
    }

    private static long pending(JdbcTemplate jdbc, UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM user_preference_outbox WHERE user_id=?", Long.class, user);
    }

    private static List<Long> endOffsets(AdminClient admin) throws Exception {
        var description = admin.describeTopics(List.of(TOPIC)).allTopicNames().get(5, TimeUnit.SECONDS).get(TOPIC);
        var request = new HashMap<TopicPartition, OffsetSpec>();
        description.partitions().forEach(partition ->
                request.put(new TopicPartition(TOPIC, partition.partition()), OffsetSpec.latest()));
        return admin.listOffsets(request).all().get(5, TimeUnit.SECONDS).values().stream()
                .map(result -> result.offset()).toList();
    }

    private static HttpResponse<String> put(HttpClient http, String base, String token, String genre) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(base + PREFERENCES_PATH)).timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(Map.of("genres", List.of(genre)))))
                .build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void registerSchema(HttpClient http, String registry) throws Exception {
        Schema schema;
        try (var resource = UserPreferenceChangedEvent.class.getResourceAsStream("/UserPreferenceChangedEvent.avsc")) {
            schema = new Schema.Parser().parse(resource);
        }
        String subject = TOPIC + "-" + schema.getFullName();
        var request = HttpRequest.newBuilder(URI.create(registry + "/subjects/" + subject + "/versions")).timeout(TIMEOUT)
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(
                        Map.of("schemaType", "AVRO", "schema", schema.toString())))).build();
        assertThat(http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(200);
    }

    private static final class TestAccessToken implements AutoCloseable {
        private final HttpServer server;
        private final String token;

        TestAccessToken(UUID user) throws Exception {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            var key = generator.generateKeyPair();
            var publicKey = new RSAKey.Builder((RSAPublicKey) key.getPublic()).keyID("preferences-test").build();
            byte[] jwks = new JWKSet(publicKey).toString().getBytes(StandardCharsets.UTF_8);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                try (exchange) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, jwks.length);
                    exchange.getResponseBody().write(jwks);
                }
            });
            server.start();
            var now = Instant.now();
            var claims = new JWTClaimsSet.Builder().issuer(issuer()).subject(user.toString())
                    .audience("api://vellumhub").claim("client_id", "preferences-test")
                    .claim("grant_type", "authorization_code").claim("roles", List.of("USER"))
                    .claim("scope", "user:read user:write").jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(180))).build();
            var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(new JOSEObjectType("at+jwt")).keyID(publicKey.getKeyID()).build(), claims);
            signed.sign(new RSASSASigner(key.getPrivate()));
            token = signed.serialize();
        }

        String issuer() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        String token() { return token; }

        @Override
        public void close() { server.stop(0); }
    }
}
