package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A disposable noeviction Redis must reject new cache writes at memory limit
 * without changing successful PostgreSQL-backed HTTP response semantics.
 */
@Testcontainers
@Tag("distributed")
class BookReadCacheNoEvictionIT {
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--save", "", "--appendonly", "no",
                    "--maxmemory", "1800kb", "--maxmemory-policy", "noeviction");

    @Test
    void memoryPressureRejectsCacheWritesButNotSourceOfTruthReads() {
        var factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            var meters = new SimpleMeterRegistry();
            var cache = new BookReadCache(redis, meters, true, 300, 45);
            String description = "x".repeat(900); // realistic bounded Book description.
            for (int page = 0; page < 65; page++) {
                final int pageNo = page;
                List<GetBookResponse> result = cache.page(pageNo, 49, () ->
                        IntStream.range(0, 49).mapToObj(index ->
                                new GetBookResponse(new UUID(0L, pageNo * 49L + index + 1L),
                                        "Book " + index, description, 2026, null,
                                        "Author", "ISBN" + index, 240, "Publisher",
                                        List.of("Fiction", "Mystery"),
                                        Instant.parse("2026-01-01T00:00:00Z"),
                                        Instant.parse("2026-02-01T00:00:00Z")))
                                .toList());
                assertThat(result).hasSize(49);
            }
            assertThat(meters.counter("vellumhub.catalog.cache.events",
                    "cache", "books", "result", "error").count())
                    .as("Redis maxmemory/noeviction must reject writes without failing reads")
                    .isGreaterThan(0);
        } finally {
            factory.destroy();
        }
    }
}
