package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Optional response cache for authenticated Catalog GETs.
 *
 * Detail generations are per book, page generation is global. An in-flight
 * reader can populate only the generation captured before a commit. Redis is
 * disposable, noeviction, and NOT a source of truth. On Redis errors, reads
 * use the existing PostgreSQL path. No null / 404 / empty-page caching.
 */
@Component
public class BookReadCache {
    private static final String PREFIX = "vellumhub:catalog:response:v1:";
    private static final String DETAIL_GENERATION = PREFIX + "book-generation:";
    private static final String PAGE_GENERATION = PREFIX + "page-generation";
    private static final Duration DETAIL_GENERATION_TTL = Duration.ofDays(1);
    private static final TypeReference<List<StoredBook>> STORED_LIST = new TypeReference<>() {};
    private static final int MAX_IN_FLIGHT = 256;

    private final StringRedisTemplate redis;
    private final MeterRegistry meters;
    private final ObjectMapper json = new ObjectMapper();
    private final boolean enabled;
    private final int detailTtl;
    private final int pageTtl;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> inFlight = new ConcurrentHashMap<>();
    private final Semaphore permits = new Semaphore(MAX_IN_FLIGHT);

    public BookReadCache(StringRedisTemplate redis, MeterRegistry meters,
                         @Value("${vellumhub.cache.catalog.enabled:false}") boolean enabled,
                         @Value("${vellumhub.cache.catalog.detail-ttl-seconds:300}") int detailTtl,
                         @Value("${vellumhub.cache.catalog.page-ttl-seconds:45}") int pageTtl) {
        if (detailTtl < 1 || detailTtl > 900 || pageTtl < 1 || pageTtl > 300) {
            throw new IllegalArgumentException("Invalid Catalog cache TTL bounds");
        }
        this.redis = redis;
        this.meters = meters;
        this.enabled = enabled;
        this.detailTtl = detailTtl;
        this.pageTtl = pageTtl;
    }

    public GetBookResponse detail(UUID id, Supplier<GetBookResponse> loader) {
        if (!enabled || id == null) return bypass(loader);
        String key;
        try {
            key = PREFIX + "book:" + id + ":" + generation(DETAIL_GENERATION + id, DETAIL_GENERATION_TTL);
        } catch (RuntimeException failure) {
            event("error");
            return loader.get();
        }
        return read(key, loader, payload -> json.readValue(payload, StoredBook.class).restore(),
                book -> json.writeValueAsString(StoredBook.from(book)), book -> book != null, detailTtl);
    }

    public List<GetBookResponse> page(int number, int size, Supplier<List<GetBookResponse>> loader) {
        // The controller normalizes pagination with PageBook before calling us.
        if (!enabled || number < 0 || number > 10_000 || size < 1 || size >= 50) return bypass(loader);
        String key;
        try {
            key = PREFIX + "page:createdAt-id:" + number + ":" + size + ":" + generation(PAGE_GENERATION, null);
        } catch (RuntimeException failure) {
            event("error");
            return loader.get();
        }
        return read(key, loader, payload -> json.readValue(payload, STORED_LIST).stream().map(StoredBook::restore).toList(),
                books -> json.writeValueAsString(books.stream().map(StoredBook::from).toList()),
                books -> books != null && !books.isEmpty(), pageTtl);
    }

    private <T> T bypass(Supplier<T> loader) {
        event("bypass");
        return loader.get();
    }

    private interface JsonCodec<T, R> { R apply(T value) throws Exception; }

    private <T> T read(String key, Supplier<T> loader, JsonCodec<String, T> decode,
                       JsonCodec<T, String> encode, Predicate<T> cacheable, int ttlSeconds) {
        Timer.Sample sample = Timer.start(meters);
        try {
            try {
                String payload = redis.opsForValue().get(key);
                if (payload != null) {
                    try {
                        T hit = decode.apply(payload);
                        if (hit != null) {
                            event("hit");
                            return hit;
                        }
                    } catch (Exception invalid) {
                        // Corrupt entries are not sent to clients. Deletion is best effort.
                        event("corrupt");
                    }
                    try { redis.delete(key); } catch (RuntimeException ignored) { event("error"); }
                }
                event("miss");
            } catch (RuntimeException failure) {
                event("error");
                return loader.get();
            }
            return loadOnce(key, loader, encode, cacheable, ttlSeconds);
        } finally {
            sample.stop(Timer.builder("vellumhub.catalog.cache.serving.duration")
                    .publishPercentileHistogram().register(meters));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadOnce(String key, Supplier<T> loader, JsonCodec<T, String> encode,
                           Predicate<T> cacheable, int ttlSeconds) {
        CompletableFuture<Object> previous = inFlight.get(key);
        if (previous != null) return await(previous, loader);
        if (!permits.tryAcquire()) {
            event("capacity");
            return loadAndStore(key, loader, encode, cacheable, ttlSeconds);
        }
        CompletableFuture<Object> own = new CompletableFuture<>();
        previous = inFlight.putIfAbsent(key, own);
        if (previous != null) {
            permits.release();
            return await(previous, loader);
        }
        event("leader");
        try {
            T result = loadAndStore(key, loader, encode, cacheable, ttlSeconds);
            own.complete(result);
            return result;
        } catch (RuntimeException | Error failure) {
            own.completeExceptionally(failure);
            throw failure;
        } finally {
            inFlight.remove(key, own);
            permits.release();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T await(CompletableFuture<Object> pending, Supplier<T> loader) {
        event("waiting");
        try {
            T result = (T) pending.get(2, TimeUnit.SECONDS);
            event("joined");
            return result;
        } catch (TimeoutException e) {
            event("timeout");
            return loader.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            event("interrupted");
            return loader.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Book cache loader failed", cause);
        }
    }

    private <T> T loadAndStore(String key, Supplier<T> loader, JsonCodec<T, String> encode,
                               Predicate<T> cacheable, int ttlSeconds) {
        T result = loader.get();
        if (cacheable.test(result)) {
            try {
                String payload = encode.apply(result);
                redis.opsForValue().set(key, payload, ttl(ttlSeconds));
            } catch (Exception failure) {
                event("error");
            }
        }
        return result;
    }

    private Duration ttl(int seconds) {
        int jitter = seconds > 10 ? ThreadLocalRandom.current().nextInt(Math.max(1, seconds / 10)) : 0;
        return Duration.ofSeconds(Math.max(1, seconds - jitter));
    }

    private String generation(String redisKey, Duration expiry) {
        String current = redis.opsForValue().get(redisKey);
        if (current != null) return current;
        String candidate = UUID.randomUUID().toString();
        if (expiry == null) {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, candidate))) return candidate;
        } else {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(redisKey, candidate, expiry))) return candidate;
        }
        String winner = redis.opsForValue().get(redisKey);
        if (winner == null) throw new IllegalStateException("Catalog generation unavailable");
        return winner;
    }

    /** Called by BookChangeWriter after a successful Outbox append within the mutation TX. */
    public void invalidateAfterCommit(UUID bookId) {
        if (!enabled || bookId == null) return;
        Runnable action = () -> {
            // Separate epochs isolate detail edits from other books, but any
            // insert/update/delete can change pages and their ordering.
            try {
                redis.opsForValue().set(DETAIL_GENERATION + bookId, UUID.randomUUID().toString(),
                        DETAIL_GENERATION_TTL);
                redis.opsForValue().set(PAGE_GENERATION, UUID.randomUUID().toString());
                event("invalidate");
            } catch (RuntimeException failure) {
                // A failed optional cache invalidation cannot roll back committed data.
                // The remaining stale response is bounded by its TTL.
                event("invalidate-error");
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            // Only safe when the caller has already committed. All canonical
            // Book writes use @Transactional handlers; append completes before this call.
            action.run();
        }
    }

    private void event(String result) {
        meters.counter("vellumhub.catalog.cache.events", "cache", "books", "result", result).increment();
    }

    // Jackson 2 is used by the existing Kafka/OpenAPI stack. Convert Instant
    // explicitly instead of depending on an optional JavaTime Jackson module.
    public record StoredBook(UUID id, String title, String description, int releaseYear,
                             String coverUrl, String author, String isbn, int pageCount,
                             String publisher, List<String> genres, String createdAt,
                             String updatedAt) {
        static StoredBook from(GetBookResponse b) {
            return new StoredBook(b.id(), b.title(), b.description(), b.releaseYear(),
                    b.coverUrl(), b.author(), b.isbn(), b.pageCount(), b.publisher(),
                    b.genres(), b.createdAt() == null ? null : b.createdAt().toString(),
                    b.updatedAt() == null ? null : b.updatedAt().toString());
        }
        GetBookResponse restore() {
            return new GetBookResponse(id, title, description, releaseYear, coverUrl,
                    author, isbn, pageCount, publisher, genres,
                    createdAt == null ? null : Instant.parse(createdAt),
                    updatedAt == null ? null : Instant.parse(updatedAt));
        }
    }
}
