package com.vellumhub.recommendation_service.module.recommendation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@Tag("distributed")
class RecommendationResponseRedisIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no", "--save", "",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");

    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private RecommendationResponseCache first;
    private RecommendationResponseCache second;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        first = new RecommendationResponseCache(redis,
                new SimpleMeterRegistry(), true, 45, 100);
        second = new RecommendationResponseCache(redis,
                new SimpleMeterRegistry(), true, 45, 100);
    }

    @AfterEach
    void tearDown() {
        factory.destroy();
    }

    private List<RecommendationResponse> response(String title) {
        return List.of(new RecommendationResponse(UUID.randomUUID(), title, null,
                2026, null, "Author", List.of("Fiction")));
    }

    @Test
    void twoInstancesShareCacheButNotUserData() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        List<RecommendationResponse> a = response("A"), b = response("B");
        AtomicInteger loads = new AtomicInteger();
        assertThat(first.read(alice, 10, 0, () -> {loads.incrementAndGet(); return a;}))
                .isEqualTo(a);
        assertThat(second.read(alice, 10, 0, () -> {loads.incrementAndGet(); return b;}))
                .isEqualTo(a);
        assertThat(second.read(bob, 10, 0, () -> b)).isEqualTo(b);
        assertThat(loads.get()).isEqualTo(1);
    }

    @Test
    void invalidateOnOneInstanceIsVisibleToTheOtherAndDoesNotResurrectStaleFill() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> oldBooks = response("Before");
        List<RecommendationResponse> newBooks = response("After");
        assertThat(first.read(user, 10, 0, () -> {
            second.invalidateUserAfterCommit(user);
            return oldBooks;
        })).isEqualTo(oldBooks);
        assertThat(second.read(user, 10, 0, () -> newBooks)).isEqualTo(newBooks);
        assertThat(first.read(user, 10, 0, () -> oldBooks)).isEqualTo(newBooks);
        first.invalidateAllAfterCommit();
        assertThat(second.read(user, 10, 0, () -> oldBooks)).isEqualTo(oldBooks);
    }

    @Test
    void redisStoppedDoesNotBlockDatabaseRead() {
        UUID user = UUID.randomUUID();
        REDIS.getDockerClient().stopContainerCmd(REDIS.getContainerId()).exec();
        try {
            List<RecommendationResponse> expected = response("Postgres");
            assertThat(first.read(user, 10, 0, () -> expected)).isEqualTo(expected);
        } finally {
            REDIS.getDockerClient().startContainerCmd(REDIS.getContainerId()).exec();
        }
    }
}
