package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.vellumhub.catalog_service.module.book.presentation.dto.GetBookResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@Tag("distributed")
class BookReadCacheRedisIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no", "--save", "",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");

    private LettuceConnectionFactory factory;
    private LettuceConnectionFactory secondFactory;
    private StringRedisTemplate redis;
    private StringRedisTemplate secondRedis;
    private BookReadCache first;
    private BookReadCache second;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        secondFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        secondFactory.afterPropertiesSet();
        secondRedis = new StringRedisTemplate(secondFactory);
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        registry = new SimpleMeterRegistry();
        first = new BookReadCache(redis, registry, true, 300, 45);
        second = new BookReadCache(secondRedis, new SimpleMeterRegistry(), true, 300, 45);
    }

    @AfterEach
    void tearDown() {
        secondFactory.destroy();
        factory.destroy();
        registry.close();
    }

    private GetBookResponse book(UUID id, String title) {
        return new GetBookResponse(id, title, "Description", 2024, "https://example.test/cover.jpg",
                "Author", "9780000000000", 240, "Publisher", List.of("Sci-Fi", "Fantasy"),
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void absentIdsDoNotAllocatePerUuidRedisGenerationKeys() {
        // A client may probe arbitrary authenticated IDs that return 404.
        // Never let those probes allocate persistent Redis generation markers.
        for (int i = 0; i < 60; i++) {
            assertThat(first.detail(UUID.randomUUID(), () -> null)).isNull();
        }
        assertThat(redis.keys("vellumhub:catalog:response:v1:book-generation:*")).isEmpty();
        UUID changed = UUID.randomUUID();
        second.invalidateAfterCommit(changed);
        assertThat(redis.keys("vellumhub:catalog:response:v1:book-generation:*"))
                .hasSize(1);
    }

    @Test
    void twoReplicasShareDetailAndPageButKeepDifferentBooksIndependent() {
        UUID idA = UUID.randomUUID(), idB = UUID.randomUUID();
        GetBookResponse a = book(idA, "First"), b = book(idB, "Second");
        AtomicInteger loads = new AtomicInteger();

        assertThat(first.detail(idA, () -> { loads.incrementAndGet(); return a; })).isEqualTo(a);
        assertThat(second.detail(idA, () -> { loads.incrementAndGet(); return b; })).isEqualTo(a);
        assertThat(second.detail(idB, () -> b)).isEqualTo(b);
        assertThat(loads.get()).isEqualTo(1);

        assertThat(first.page(0, 10, () -> List.of(a, b))).containsExactly(a, b);
        assertThat(second.page(0, 10, () -> { throw new AssertionError("page miss on HIT"); }))
                .containsExactly(a, b);
        assertThat(first.page(1, 10, List::of)).isEmpty(); // Empty result is not cached.
    }

    @Test
    void changedBookInvalidatesItsDetailAndAllPagesWithoutResurrectingStaleFill() {
        UUID idA = UUID.randomUUID(), idB = UUID.randomUUID();
        GetBookResponse before = book(idA, "Before"), after = book(idA, "After");
        GetBookResponse untouched = book(idB, "Untouched");
        first.detail(idB, () -> untouched);

        // A stale loader began in generation G; the write invalidates to G+1
        // before it stores the old result. Readers on G+1 must never see it.
        assertThat(first.detail(idA, () -> {
            second.invalidateAfterCommit(idA);
            return before;
        })).isEqualTo(before);
        assertThat(second.detail(idA, () -> after)).isEqualTo(after);
        assertThat(first.detail(idA, () -> before)).isEqualTo(after);
        assertThat(second.detail(idB, () -> {throw new AssertionError("unrelated detail evicted");}))
                .isEqualTo(untouched);

        first.page(0, 10, () -> List.of(before));
        second.invalidateAfterCommit(idA);
        assertThat(second.page(0, 10, () -> List.of(after))).containsExactly(after);
    }

    @Test
    void uncommittedInvalidationRegistersCallbackAndRollbackDoesNotBumpGeneration() {
        UUID id = UUID.randomUUID();
        GetBookResponse original = book(id, "Original");
        first.detail(id, () -> original);
        first.page(0, 10, () -> List.of(original));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        try {
            first.invalidateAfterCommit(id);
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        } finally {
            // Simulate rollback: do not execute afterCommit() callbacks.
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertThat(second.detail(id, () -> {throw new AssertionError("rollback evicted detail");}))
                .isEqualTo(original);
        assertThat(second.page(0, 10, () -> {throw new AssertionError("rollback evicted page");}))
                .containsExactly(original);
        // A committed callback invalidates as expected.
        second.invalidateAfterCommit(id);
        assertThat(first.detail(id, () -> book(id, "New")).title()).isEqualTo("New");
    }

    @Test
    void concurrentColdReadsCoalescePerReplica() throws Exception {
        UUID id = UUID.randomUUID();
        GetBookResponse answer = book(id, "Hot");
        AtomicInteger sqlLoads = new AtomicInteger();
        CountDownLatch leaderStarted = new CountDownLatch(1), release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(32)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<GetBookResponse>>();
            futures.add(pool.submit(() -> first.detail(id, () -> {
                sqlLoads.incrementAndGet();
                leaderStarted.countDown();
                try {
                    if (!release.await(15, TimeUnit.SECONDS)) throw new IllegalStateException("slow read");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return answer;
            })));
            assertThat(leaderStarted.await(10, TimeUnit.SECONDS)).isTrue();
            for (int n = 1; n < 32; n++) {
                futures.add(pool.submit(() -> first.detail(id, () -> {
                    sqlLoads.incrementAndGet();
                    return answer;
                })));
            }
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                while (System.nanoTime() < deadline &&
                        registry.counter("vellumhub.catalog.cache.events", "cache", "books", "result", "waiting").count() < 31) {
                    Thread.sleep(20);
                }
                assertThat(registry.counter("vellumhub.catalog.cache.events", "cache", "books",
                        "result", "waiting").count()).isEqualTo(31);
            } finally {
                release.countDown();
            }
            for (var f : futures) assertThat(f.get(15, TimeUnit.SECONDS)).isEqualTo(answer);
        }
        assertThat(sqlLoads.get()).isEqualTo(1);
    }

    @Test
    void offBypassDoesNotTouchRedisAndDoesNotCacheFailuresOrMissingBooks() {
        UUID id = UUID.randomUUID();
        AtomicInteger loads = new AtomicInteger();
        BookReadCache off = new BookReadCache(redis, new SimpleMeterRegistry(), false, 300, 45);
        assertThat(off.detail(id, () -> { loads.incrementAndGet(); return book(id, "Off"); })).isNotNull();
        assertThat(off.detail(id, () -> { loads.incrementAndGet(); return book(id, "Off"); })).isNotNull();
        assertThat(loads.get()).isEqualTo(2);

        assertThrows(IllegalStateException.class, () ->
                first.detail(id, () -> { throw new IllegalStateException("not found"); }));
        assertThat(first.detail(id, () -> book(id, "Found")).title()).isEqualTo("Found");
    }

    @Test
    void phasedRolloutReadOffWriterStillInvalidatesActiveReader() {
        UUID id = UUID.randomUUID();
        BookReadCache writerReadOff = new BookReadCache(secondRedis, new SimpleMeterRegistry(),
                false, true, 300, 45);
        GetBookResponse oldBook = book(id, "Before"), newBook = book(id, "Committed");

        assertThat(first.detail(id, () -> oldBook)).isEqualTo(oldBook);
        assertThat(first.page(0, 10, () -> List.of(oldBook))).containsExactly(oldBook);
        assertThat(writerReadOff.detail(id, () -> newBook)).isEqualTo(newBook);
        writerReadOff.invalidateAfterCommit(id);

        assertThat(first.detail(id, () -> newBook)).isEqualTo(newBook);
        assertThat(first.page(0, 10, () -> List.of(newBook))).containsExactly(newBook);
        assertThat(registry.counter("vellumhub.catalog.cache.events",
                "cache", "books", "result", "miss").count()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void ttlExpiryAndVolatileRedisRestartCannotResurrectPreviousPayload() throws Exception {
        UUID id = UUID.randomUUID();
        BookReadCache shortTtl = new BookReadCache(redis, registry, true, 1, 1);
        GetBookResponse oldBook = book(id, "Old");
        GetBookResponse fresh = book(id, "Fresh");

        assertThat(shortTtl.detail(id, () -> oldBook)).isEqualTo(oldBook);
        assertThat(shortTtl.detail(id, () -> fresh)).isEqualTo(oldBook);
        Thread.sleep(1300); // Real Redis key expiry with TTL=1, not a mocked clock.
        assertThat(shortTtl.detail(id, () -> fresh)).isEqualTo(fresh);

        // Equivalent to cache-only Redis being restarted with no persistence.
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        var restarted = new BookReadCache(secondRedis, new SimpleMeterRegistry(), true, 1, 1);
        assertThat(restarted.detail(id, () -> oldBook)).isEqualTo(oldBook);
        restarted.invalidateAfterCommit(id);
        assertThat(shortTtl.detail(id, () -> fresh)).isEqualTo(fresh);
    }

    @Test
    void slowCoalescedLeaderFailsFollowersWithoutAmplifyingSqlWork() throws Exception {
        UUID id = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger dbLoads = new AtomicInteger();
        GetBookResponse result = book(id, "Leader");

        try (var pool = Executors.newFixedThreadPool(2)) {
            var leader = pool.submit(() -> first.detail(id, () -> {
                dbLoads.incrementAndGet();
                started.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Leader timeout");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                return result;
            }));
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var follower = pool.submit(() -> first.detail(id, () -> {
                dbLoads.incrementAndGet();
                throw new AssertionError("Follower must not run a duplicate database read");
            }));
            try {
                assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> follower.get(8, TimeUnit.SECONDS)))
                        .isInstanceOf(java.util.concurrent.ExecutionException.class)
                        .hasCauseInstanceOf(CacheLoadOverloadedException.class);
                assertThat(dbLoads.get()).isEqualTo(1);
            } finally {
                release.countDown();
            }
            assertThat(leader.get(10, TimeUnit.SECONDS)).isEqualTo(result);
        }
        assertThat(registry.counter("vellumhub.catalog.cache.events",
                "cache", "books", "result", "timeout").count()).isEqualTo(1);
    }

    @Test
    void redisConnectionRefusedFallsBackToHealthyDatabase() {
        // Independent real Lettuce network connection to a known-unreachable
        // local port, not Mockito intercepting opsForValue().
        var config = new RedisStandaloneConfiguration("127.0.0.1", 1);
        var lettuceConfig = org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
                .builder().commandTimeout(java.time.Duration.ofMillis(150)).build();
        var offlineFactory = new LettuceConnectionFactory(config, lettuceConfig);
        offlineFactory.afterPropertiesSet();
        try {
            var offline = new BookReadCache(new StringRedisTemplate(offlineFactory),
                    new SimpleMeterRegistry(), true, 300, 45);
            UUID id = UUID.randomUUID();
            assertThat(offline.detail(id, () -> book(id, "From PostgreSQL")).title())
                    .isEqualTo("From PostgreSQL");
            assertThat(offline.page(0, 10, () -> List.of(book(id, "Fallback"))))
                    .hasSize(1);
        } finally {
            offlineFactory.destroy();
        }
    }
}
