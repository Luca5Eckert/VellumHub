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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
    private SimpleMeterRegistry firstRegistry;
    private SimpleMeterRegistry secondRegistry;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        firstRegistry = new SimpleMeterRegistry();
        secondRegistry = new SimpleMeterRegistry();
        first = new RecommendationResponseCache(redis, firstRegistry, true, 45, 100);
        second = new RecommendationResponseCache(redis, secondRegistry, true, 45, 100);
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
    void thirtyTwoSimultaneousMissesUseOneDatabaseLoaderPerReplica() throws Exception {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> expected = response("Single flight");
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<List<RecommendationResponse>> loader = () -> {
            loads.incrementAndGet();
            leaderStarted.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return expected;
        };
        try (var pool = Executors.newFixedThreadPool(32)) {
            java.util.List<Future<List<RecommendationResponse>>> futures = new java.util.ArrayList<>();
            futures.add(pool.submit(() -> first.read(user, 10, 0, loader)));
            assertThat(leaderStarted.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 1; i < 32; i++) {
                futures.add(pool.submit(() -> first.read(user, 10, 0, loader)));
            }
            try {
                waitForFollowers(firstRegistry, 31);
            } finally {
                release.countDown();
            }
            for (var future : futures) assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(loads.get()).as("One ranked SQL read must serve 32 local requests").isEqualTo(1);
    }

    @Test
    void twoReplicasBoundConcurrentMissesToOneLeaderEach() throws Exception {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> expected = response("Two replica burst");
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch bothStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        Supplier<List<RecommendationResponse>> loader = () -> {
            loads.incrementAndGet();
            bothStarted.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("timeout");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return expected;
        };
        try (var pool = Executors.newFixedThreadPool(32)) {
            java.util.List<Future<List<RecommendationResponse>>> futures = new java.util.ArrayList<>();
            futures.add(pool.submit(() -> first.read(user, 10, 0, loader)));
            futures.add(pool.submit(() -> second.read(user, 10, 0, loader)));
            assertThat(bothStarted.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 15; i++) {
                futures.add(pool.submit(() -> first.read(user, 10, 0, loader)));
                futures.add(pool.submit(() -> second.read(user, 10, 0, loader)));
            }
            try {
                waitForFollowers(firstRegistry, 15);
                waitForFollowers(secondRegistry, 15);
            } finally {
                release.countDown();
            }
            for (var future : futures) assertThat(future.get(15, TimeUnit.SECONDS)).isEqualTo(expected);
        }
        assertThat(loads.get()).as("Two independent pods can each lead one miss").isEqualTo(2);
    }

    private void waitForFollowers(SimpleMeterRegistry registry, int expected) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var counter = registry.find("vellumhub.cache.singleflight.events")
                    .tag("cache", "recommendations").tag("result", "waiting").counter();
            if (counter != null && counter.count() >= expected) return;
            Thread.sleep(10);
        }
        throw new AssertionError("Expected " + expected + " concurrent cache followers");
    }

}
