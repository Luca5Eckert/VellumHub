package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookReadCacheTest {
    private GetBookResponse response(UUID id) {
        return new GetBookResponse(id, "Book", "Content", 2026, null, "Author", "ISBN",
                100, "Publisher", List.of("Fiction"), Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void featureFlagOffRestoresDatabaseReadWithoutTouchingRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        BookReadCache cache = new BookReadCache(redis, new SimpleMeterRegistry(), false, 300, 45);
        UUID id = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        cache.detail(id, () -> { loads.incrementAndGet(); return response(id); });
        cache.detail(id, () -> { loads.incrementAndGet(); return response(id); });
        cache.page(0, 10, () -> List.of(response(id)));
        cache.invalidateAfterCommit(id);
        assertThat(loads.get()).isEqualTo(2);
        verifyNoInteractions(redis);
    }

    @Test
    void unavailableRedisNeverProducesBookRead503WhenDatabaseHealthy() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("offline"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BookReadCache cache = new BookReadCache(redis, registry, true, 300, 45);
        UUID id = UUID.randomUUID();
        assertThat(cache.detail(id, () -> response(id))).isEqualTo(response(id));
        assertThat(cache.page(0, 10, () -> List.of(response(id)))).containsExactly(response(id));
        assertThat(registry.counter("vellumhub.catalog.cache.events", "cache", "books",
                "result", "error").count()).isEqualTo(2);
    }

    @Test
    void failedDbReadsAndMissingBooksAreNotConvertedIntoSuccessfulResponses() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        BookReadCache off = new BookReadCache(redis, new SimpleMeterRegistry(), false, 300, 45);
        assertThatThrownBy(() -> off.detail(UUID.randomUUID(),
                () -> { throw new IllegalStateException("database unavailable"); }))
                .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
        assertThatThrownBy(() -> new BookReadCache(redis, new SimpleMeterRegistry(),
                true, 901, 45)).isInstanceOf(IllegalArgumentException.class);
    }
}
