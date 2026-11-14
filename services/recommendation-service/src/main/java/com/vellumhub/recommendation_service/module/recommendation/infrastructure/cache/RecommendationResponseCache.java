package com.vellumhub.recommendation_service.module.recommendation.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.recommendation_service.module.recommendation.presentation.dto.RecommendationResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Optional cache of *response DTOs*, not JPA entities or embeddings.
 *
 * Global version has no TTL; per-user versions expire after 24 hours (much\n * longer than a response TTL). The Redis instance uses noeviction.
 * A reader that started before invalidation can only fill its OLD versioned key;
 * it cannot resurrect an entry for a new generation. If Redis is unavailable,
 * reads use PostgreSQL. A failed post-commit invalidation can leave data stale
 * for at most the response TTL, not indefinitely.
 */
@Component
public class RecommendationResponseCache {
    private static final String PREFIX = "vellumhub:recommendation:response:v1:ranker-70-30:";
    private static final String GLOBAL_VERSION_KEY = PREFIX + "global-generation";
    private static final String USER_VERSION_PREFIX = PREFIX + "user-generation:";
    // User generations expire after a day: response TTL <= one hour, and a new
    // random generation cannot expose an old response even when a key survives.
    private static final Duration USER_GENERATION_TTL = Duration.ofDays(1);
    private static final TypeReference<List<RecommendationResponse>> RESPONSES = new TypeReference<>() {};

    private final StringRedisTemplate redis;
    private final ObjectMapper json = new ObjectMapper();
    private final MeterRegistry registry;
    private final boolean enabled;
    private final long ttlSeconds;
    private final int maxLimit;

    public RecommendationResponseCache(
            StringRedisTemplate redis,
            MeterRegistry registry,
            @Value("${vellumhub.cache.recommendations.enabled:false}") boolean enabled,
            @Value("${vellumhub.cache.recommendations.ttl-seconds:45}") long ttlSeconds,
            @Value("${vellumhub.cache.recommendations.max-limit:100}") int maxLimit) {
        if (ttlSeconds < 1 || ttlSeconds > 3600 || maxLimit < 1) {
            throw new IllegalArgumentException("Invalid recommendation cache bounds");
        }
        this.redis = redis;
        this.registry = registry;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
        this.maxLimit = maxLimit;
    }

    public List<RecommendationResponse> read(UUID userId, int limit, int offset,
                                             Supplier<List<RecommendationResponse>> loader) {
        Timer.Sample serving = Timer.start(registry);
        String outcome = "bypass";
        try {
            // The caller MUST authenticate and authorize before calling this method.
            // Do not change the behavior of invalid/unbounded legacy inputs just to cache them.
            if (!enabled || userId == null || limit < 1 || limit > maxLimit || offset < 0) {
                request("bypass");
                return loader.get();
            }

            String key = null;
            String global = null;
            String user = null;
            try {
                global = generation(GLOBAL_VERSION_KEY);
                user = generation(USER_VERSION_PREFIX + userId);
                key = responseKey(userId, limit, offset, global, user);
                String lookupKey = key;
                String cached = timed("get", () -> redis.opsForValue().get(lookupKey));
                if (cached != null) {
                    try {
                        List<RecommendationResponse> response = json.readValue(cached, RESPONSES);
                        outcome = "hit";
                        request("hit");
                        return response;
                    } catch (JsonProcessingException invalidValue) {
                        // Corrupt/old payload is never returned. Version keys allow safe roll-forward.
                        timed("delete", () -> { redis.delete(lookupKey); return null; });
                    }
                }
                outcome = "miss";
                request("miss");
            } catch (RuntimeException redisError) {
                outcome = "error";
                request("error");
                key = null; // Do not attempt to populate after a failed lookup.
            }

            List<RecommendationResponse> fresh = loader.get();
            if (key != null && fresh != null && !fresh.isEmpty()) {
                try {
                    // If invalidation raced with the DB read, do not write a stale value at all.
                    if (global.equals(generation(GLOBAL_VERSION_KEY))
                            && user.equals(generation(USER_VERSION_PREFIX + userId))) {
                        String payload = json.writeValueAsString(fresh);
                        String destination = key;
                        timed("put", () -> {
                            redis.opsForValue().set(destination, payload, ttl());
                            return null;
                        });
                    }
                } catch (RuntimeException | JsonProcessingException redisError) {
                    operationFailure("put");
                    // Redis/serialization never changes the correct source-of-truth response.
                }
            }
            return fresh;
        } finally {
            serving.stop(Timer.builder("vellumhub.recommendation.serving.duration")
                    .tag("result", outcome)
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    /**
     * Called after a successful local UserProfile save. The transaction's commit
     * (projection AND checkpoint) must precede invalidation.
     */
    public void invalidateUserAfterCommit(UUID userId) {
        if (!enabled || userId == null) return;
        afterCommit(() -> invalidate(USER_VERSION_PREFIX + userId, "user"));
    }

    /**
     * Book creation/update/delete may affect ANY user's ranking or visible metadata.
     */
    public void invalidateAllAfterCommit() {
        if (!enabled) return;
        afterCommit(() -> invalidate(GLOBAL_VERSION_KEY, "global"));
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            // Spring Data's own repository transaction, when there is no outer TX,
            // has already returned (and committed) before the adapter calls this.
            action.run();
        }
    }

    private void invalidate(String versionKey, String scope) {
        try {
            timed("invalidate", () -> {
                if (versionKey.startsWith(USER_VERSION_PREFIX)) {
                    redis.opsForValue().set(versionKey, UUID.randomUUID().toString(), USER_GENERATION_TTL);
                } else {
                    redis.opsForValue().set(versionKey, UUID.randomUUID().toString());
                }
                return null;
            });
            registry.counter("vellumhub.cache.invalidations", "cache", "recommendations",
                    "scope", scope, "result", "success").increment();
        } catch (RuntimeException failed) {
            registry.counter("vellumhub.cache.invalidations", "cache", "recommendations",
                    "scope", scope, "result", "error").increment();
            // A failed optional cache cannot retroactively fail a committed Kafka/DB transaction.
            // Residual staleness is bounded by the response TTL, with metrics for operators.
        }
    }

    private String generation(String versionKey) {
        return timed("generation", () -> {
            String value = redis.opsForValue().get(versionKey);
            if (value != null) return value;
            String candidate = UUID.randomUUID().toString();
            Boolean created = versionKey.startsWith(USER_VERSION_PREFIX)
                    ? redis.opsForValue().setIfAbsent(versionKey, candidate, USER_GENERATION_TTL)
                    : redis.opsForValue().setIfAbsent(versionKey, candidate);
            if (Boolean.TRUE.equals(created)) return candidate;
            value = redis.opsForValue().get(versionKey);
            if (value == null) throw new IllegalStateException("Missing cache generation");
            return value;
        });
    }

    private String responseKey(UUID userId, int limit, int offset, String global, String user) {
        return PREFIX + "user:" + userId + ":limit:" + limit + ":offset:" + offset
                + ":global:" + global + ":generation:" + user;
    }

    private Duration ttl() {
        // Small jitter avoids synchronized expirations; never exceed the configured upper bound.
        long reduction = ttlSeconds >= 5
                ? ThreadLocalRandom.current().nextLong(0, Math.max(1, ttlSeconds / 5) + 1)
                : 0;
        return Duration.ofSeconds(ttlSeconds - reduction);
    }

    private <T> T timed(String operation, Supplier<T> supplier) {
        Timer.Sample sample = Timer.start(registry);
        try {
            return supplier.get();
        } finally {
            sample.stop(Timer.builder("vellumhub.cache.operation.duration")
                    .tags("cache", "recommendations", "operation", operation)
                    .register(registry));
        }
    }

    private void request(String result) {
        registry.counter("vellumhub.cache.requests", "cache", "recommendations",
                "result", result).increment();
    }

    private void operationFailure(String operation) {
        registry.counter("vellumhub.cache.operation.errors", "cache", "recommendations",
                "operation", operation).increment();
    }
}
