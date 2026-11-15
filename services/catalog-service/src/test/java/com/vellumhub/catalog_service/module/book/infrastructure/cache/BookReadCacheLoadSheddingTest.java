package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 80 Redis-outage misses cannot fan out to 80 simultaneous JDBC queries. */
class BookReadCacheLoadSheddingTest {
    @Test
    void eightySimultaneousMissesAreBoundedToSixtyFourJdbcLoads() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("Redis offline"));
        var metrics = new SimpleMeterRegistry();
        var cache = new BookReadCache(redis, metrics, true, true, 300, 45);
        int tasks = 80;
        var ready = new CountDownLatch(tasks);
        var start = new CountDownLatch(1);
        var leadersEntered = new CountDownLatch(64);
        var release = new CountDownLatch(1);
        var current = new AtomicInteger();
        var maximum = new AtomicInteger();
        var rejected = new AtomicInteger();

        try (var pool = Executors.newFixedThreadPool(tasks)) {
            var futures = new ArrayList<java.util.concurrent.Future<?>>();
            for (int n = 0; n < tasks; n++) {
                UUID id = new UUID(0L, n + 1L);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timed out");
                    try {
                        cache.detail(id, () -> {
                            int active = current.incrementAndGet();
                            maximum.accumulateAndGet(active, Math::max);
                            leadersEntered.countDown();
                            try {
                                if (!release.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("loader timed out");
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(interrupted);
                            } finally {
                                current.decrementAndGet();
                            }
                            return null; // Missing books are not cached.
                        });
                    } catch (CacheLoadOverloadedException expected) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            assertThat(ready.await(15, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            try {
                assertThat(leadersEntered.await(10, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(850); // Rejection budget is 500ms.
                assertThat(rejected.get()).isGreaterThan(0);
            } finally {
                release.countDown();
            }
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(maximum.get()).isLessThanOrEqualTo(64);
        assertThat(rejected.get()).isGreaterThan(0);
        assertThat(metrics.counter("vellumhub.catalog.cache.events",
                "cache", "books", "result", "db-rejected").count()).isGreaterThan(0);
    }

    @Test
    void rejectsUnsafeConfigurationWithReadsOnButWritersNotInvalidating() {
        var redis = mock(StringRedisTemplate.class);
        var metrics = new SimpleMeterRegistry();
        assertThatThrownBy(() -> new BookReadCache(redis, metrics, true, false, 300, 45))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalidation");
        // Supported rolling deploy phase A: all writers invalidating, reads OFF.
        assertThat(new BookReadCache(redis, metrics, false, true, 300, 45)).isNotNull();
    }
}
