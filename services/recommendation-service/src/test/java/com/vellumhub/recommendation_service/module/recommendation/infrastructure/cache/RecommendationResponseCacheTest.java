package com.vellumhub.recommendation_service.module.recommendation.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecommendationResponseCacheTest {
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private Map<String, String> store;
    private SimpleMeterRegistry registry;
    private RecommendationResponseCache cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        store = new HashMap<>();
        registry = new SimpleMeterRegistry();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(call -> store.get(call.getArgument(0, String.class)));
        when(values.multiGet(anyCollection())).thenAnswer(call -> {
            java.util.Collection<String> keys = call.getArgument(0);
            return keys.stream().map(store::get).toList();
        });
        when(values.setIfAbsent(anyString(), anyString())).thenAnswer(call ->
                store.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null);
        doAnswer(call -> {
            store.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString());
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(call -> store.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null);
        doAnswer(call -> {
            store.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(redis.delete(anyString())).thenAnswer(call -> store.remove(call.getArgument(0)) != null);
        cache = new RecommendationResponseCache(redis, registry, true, 45, 100);
    }

    @AfterEach
    void cleanupTransactionState() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private List<RecommendationResponse> response(String title) {
        return List.of(new RecommendationResponse(UUID.randomUUID(), title, "Desc", 2026,
                null, "Author", List.of("Novel")));
    }

    @Test
    void hitAvoidsLoaderAndPreservesExactDto() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> books = response("Book");
        AtomicInteger loads = new AtomicInteger();
        var loader = (java.util.function.Supplier<List<RecommendationResponse>>) () -> {
            loads.incrementAndGet();
            return books;
        };
        assertThat(cache.read(user, 10, 0, loader)).isEqualTo(books);
        assertThat(cache.read(user, 10, 0, loader)).isEqualTo(books);
        assertThat(loads.get()).isEqualTo(1);
        assertThat(registry.get("vellumhub.cache.requests").tag("cache", "recommendations")
                .tag("result", "hit").counter().count()).isEqualTo(1);
    }

    @Test
    void missUsesBatchedGenerationLookupAndDoesNotRecheckAfterDatabase() {
        UUID user = UUID.randomUUID();
        var books = response("Miss");
        cache.read(user, 10, 0, () -> books);
        verify(values, times(1)).multiGet(anyCollection());
        verify(values, times(1)).set(anyString(), anyString(), any(Duration.class));
        cache.read(user, 10, 0, () -> response("Should be HIT"));
        verify(values, times(2)).multiGet(anyCollection());
    }

    @Test
    void usersAndPagesNeverShareResponses() {
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        List<RecommendationResponse> a = response("Alice");
        List<RecommendationResponse> b = response("Bob");
        assertThat(cache.read(alice, 10, 0, () -> a)).isEqualTo(a);
        assertThat(cache.read(bob, 10, 0, () -> b)).isEqualTo(b);
        assertThat(cache.read(alice, 10, 1, () -> b)).isEqualTo(b);
        assertThat(cache.read(alice, 10, 0, () -> b)).isEqualTo(a);
    }

    @Test
    void profileAndCatalogMutationsInvalidateCurrentGeneration() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> original = response("Original"), changed = response("Changed");
        cache.read(user, 5, 0, () -> original);
        cache.invalidateUserAfterCommit(user);
        assertThat(cache.read(user, 5, 0, () -> changed)).isEqualTo(changed);
        cache.invalidateAllAfterCommit();
        assertThat(cache.read(user, 5, 0, () -> original)).isEqualTo(original);
    }

    @Test
    void staleLoaderCannotPopulateNewGeneration() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> before = response("Before"), after = response("After");
        List<RecommendationResponse> result = cache.read(user, 10, 0, () -> {
            cache.invalidateUserAfterCommit(user); // Simulates commit on another replica.
            return before;
        });
        assertThat(result).isEqualTo(before); // in-flight read may be stale.
        assertThat(cache.read(user, 10, 0, () -> after)).isEqualTo(after);
        assertThat(cache.read(user, 10, 0, () -> before)).isEqualTo(after);
    }

    @Test
    void invalidationOccursOnlyAfterTransactionCommit() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> before = response("Before"), after = response("After");
        cache.read(user, 10, 0, () -> before);

        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateUserAfterCommit(user);
        assertThat(cache.read(user, 10, 0, () -> after)).isEqualTo(before);

        // In a real transaction Spring invokes this only on successful commit.
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        assertThat(cache.read(user, 10, 0, () -> after)).isEqualTo(after);
    }

    @Test
    void rollbackDoesNotInvalidate() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> first = response("First");
        cache.read(user, 10, 0, () -> first);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        cache.invalidateUserAfterCommit(user);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(cache.read(user, 10, 0, () -> response("Wrong"))).isEqualTo(first);
    }

    @Test
    void redisOutageBypassesCacheAndDoesNotFailHealthyRead() {
        when(values.multiGet(anyCollection())).thenThrow(new IllegalStateException("Redis down"));
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> expected = response("DB");
        assertThat(cache.read(user, 10, 0, () -> expected)).isEqualTo(expected);
        assertThat(registry.get("vellumhub.cache.requests").tag("cache", "recommendations")
                .tag("result", "error").counter().count()).isEqualTo(1);
    }

    @Test
    void disabledCacheDoesNotTouchRedisAndDoesNotAlterReadSemantics() {
        RecommendationResponseCache disabled = new RecommendationResponseCache(
                redis, registry, false, 45, 100);
        List<RecommendationResponse> books = response("DB");
        assertThat(disabled.read(UUID.randomUUID(), -1, -1, () -> books)).isEqualTo(books);
        verify(redis, never()).delete(anyString());
        verify(values, never()).get(anyString());
    }

    @Test
    void corruptDtoIsEvictedAndRecomputedWithoutReturningCorruptBytes() {
        UUID user = UUID.randomUUID();
        List<RecommendationResponse> original = response("Original");
        cache.read(user, 10, 0, () -> original);
        String payloadKey = store.keySet().stream().filter(key -> key.contains(":limit:10:offset:0:"))
                .findFirst().orElseThrow();
        store.put(payloadKey, "not-json");
        List<RecommendationResponse> repaired = response("Repaired");
        assertThat(cache.read(user, 10, 0, () -> repaired)).isEqualTo(repaired);
        assertThat(cache.read(user, 10, 0, () -> original)).isEqualTo(repaired);
    }

    @Test
    void jsonNullCannotBeServedAsAValidCacheHit() {
        UUID user = UUID.randomUUID();
        cache.read(user, 10, 0, () -> response("First"));
        String payloadKey = store.keySet().stream().filter(key -> key.contains(":limit:10:offset:0:"))
                .findFirst().orElseThrow();
        store.put(payloadKey, "null");
        List<RecommendationResponse> updated = response("After");
        assertThat(cache.read(user, 10, 0, () -> updated)).isEqualTo(updated);
    }

    @Test
    void emptyResponsesAndErrorsAreNotCached() {
        UUID user = UUID.randomUUID();
        assertThat(cache.read(user, 10, 0, List::of)).isEmpty();
        List<RecommendationResponse> books = response("Next");
        assertThat(cache.read(user, 10, 0, () -> books)).isEqualTo(books);
        UUID failedUser = UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                cache.read(failedUser, 10, 0, () -> { throw new IllegalArgumentException("DB failed"); }))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
