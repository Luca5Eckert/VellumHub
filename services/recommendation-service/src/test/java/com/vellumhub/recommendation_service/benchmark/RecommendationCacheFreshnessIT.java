package com.vellumhub.recommendation_service.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Real Kafka -> UserProfile PG transaction -> Redis generation -> HTTP ranking.
 * The first request is deliberately cached BEFORE the rating is published.
 */
@Testcontainers
@Tag("distributed-benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RecommendationCacheFreshnessIT extends DistributedIntegrationTestSupport {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no", "--save", "",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");

    @DynamicPropertySource
    static void cacheProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("vellumhub.cache.recommendations.enabled", () -> "true");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired KafkaTemplate<String, Object> kafka;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry metrics;
    private final ObjectMapper json = new ObjectMapper();
    private UUID targetBook;
    private UUID signalBook;

    @BeforeEach
    void fixture() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbc.update("delete from recommendation_genres");
        jdbc.update("delete from user_profiles");
        jdbc.update("delete from recommendations");
        jdbc.update("delete from book_features");
        signalBook = id("cache-signal");
        targetBook = id("cache-semantic-target");
        insertBook(signalBook, 0, .50, "Signal");
        insertBook(targetBook, 0, .00, "Semantic Target");
        for (int i = 1; i <= 5; i++) insertBook(id("cache-decoy-" + i), i, 1.0, "Decoy " + i);
    }

    @Test
    @Timeout(50)
    void prewarmedCacheReflectsCommittedKafkaEventWithoutWaitingForTtl() throws Exception {
        UUID user = id("cache-freshness-user");
        UUID before = firstBook(user);
        assertThat(before).isNotEqualTo(targetBook);
        assertThat(firstBook(user)).isEqualTo(before); // HIT: warm before event

        kafka.send(KafkaTopics.CREATED_RATING, user.toString(),
                new CreatedRatingEvent(id("cache-freshness-event"), Instant.now(), 471122L,
                        user, signalBook, null, 5, false)).get(5, TimeUnit.SECONDS);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        UUID observed = before;
        while (System.nanoTime() < deadline) {
            observed = firstBook(user);
            if (targetBook.equals(observed)) break;
            Thread.sleep(25);
        }
        assertThat(observed).as("event must invalidate prewarmed recommendation cache").isEqualTo(targetBook);
        assertThat(firstBook(user)).isEqualTo(targetBook);
        assertThat(metrics.get("vellumhub.cache.requests")
                .tag("cache", "recommendations").tag("result", "hit")
                .counter().count()).isGreaterThan(0.0);
    }

    @Test
    void cachedUserMustStillPassSecurityFilter() throws Exception {
        UUID user = id("cache-auth-user");
        firstBook(user); // seed a cache hit
        assertThat(mvc.perform(get("/recommendations").param("limit", "3")).andReturn()
                .getResponse().getStatus()).isEqualTo(401);
    }

    private UUID firstBook(UUID userId) throws Exception {
        var result = mvc.perform(get("/recommendations").param("limit", "3")
                        .with(jwt().jwt(token -> token.subject(userId.toString())
                                .claim("roles", List.of("USER"))
                                .claim("grant_type", "authorization_code")
                                .claim("scope", "recommendation:read"))
                                .authorities(
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"),
                                        new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_recommendation:read"))))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var data = json.readTree(result.getResponse().getContentAsString());
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isPositive();
        return UUID.fromString(data.get(0).get("id").asText());
    }

    private void insertBook(UUID bookId, int axis, double popularity, String title) {
        StringBuilder vector = new StringBuilder("[");
        for (int index = 0; index < 384; index++) {
            if (index > 0) vector.append(",");
            vector.append(index == axis ? "1.0" : "0.0");
        }
        vector.append("]");
        jdbc.update("insert into book_features (book_id, embedding, popularity_score, last_updated) values (?, cast(? as vector), ?, now())",
                bookId, vector.toString(), popularity);
        jdbc.update("insert into recommendations (book_id, title, description, release_year, cover_url, author) values (?, ?, ?, ?, ?, ?)",
                bookId, title, "Cache freshness fixture", 2026, "https://example.test/cache/" + bookId + ".jpg", "Benchmark");
    }

    private UUID id(String name) {
        return UUID.nameUUIDFromBytes(("redis-cache-36:" + name).getBytes(StandardCharsets.UTF_8));
    }
}
