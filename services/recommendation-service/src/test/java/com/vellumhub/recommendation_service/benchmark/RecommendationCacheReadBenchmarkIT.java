package com.vellumhub.recommendation_service.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.HashMap;
import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.util.Properties;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * OFF / ON compare the SAME authenticated HTTP path and PostgreSQL/pgvector dataset
 * in independent Maven processes. Benchmark timings are controlled local/CI
 * observations, not production numbers. No absolute latency threshold is enforced.
 */
@Testcontainers
@Tag("cache-benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RecommendationCacheReadBenchmarkIT extends DistributedIntegrationTestSupport {
    private static final int BOOKS = 600;
    private int userCount;
    private static final long SEED = 360_036L;

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no", "--save", "",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");

    @DynamicPropertySource
    static void cacheProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        registry.add("logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener", () -> "OFF");
        registry.add("vellumhub.cache.recommendations.enabled",
                () -> System.getProperty("cache.benchmark.enabled", "false"));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired MeterRegistry meterRegistry;
    @Autowired EntityManagerFactory entityManagerFactory;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void prepare() {
        String profile = System.getProperty("benchmark.profile", "smoke");
        userCount = switch (profile) {
            case "smoke" -> 400;
            case "reference" -> 1400;
            case "stress" -> 3500;
            default -> throw new IllegalArgumentException("Unknown profile: " + profile);
        };
        jdbc.update("delete from recommendation_genres");
        jdbc.update("delete from user_profiles");
        jdbc.update("delete from recommendations");
        jdbc.update("delete from book_features");
        for (int i = 0; i < BOOKS; i++) {
            UUID book = id("book", i);
            String vector = vector(i % 8);
            jdbc.update("insert into book_features (book_id, embedding, popularity_score, last_updated) values (?, cast(? as vector), ?, now())",
                    book, vector, (double) i / BOOKS);
            jdbc.update("insert into recommendations (book_id, title, description, release_year, cover_url, author) values (?, ?, ?, ?, ?, ?)",
                    book, "Book " + i, "Deterministic benchmark", 2026,
                    "https://example.test/cache/" + i + ".jpg", "Reference");
            jdbc.update("insert into recommendation_genres (recommendation_book_id, genres) values (?, ?)",
                    book, "Genre " + (i % 8));
            jdbc.update("insert into recommendation_genres (recommendation_book_id, genres) values (?, ?)",
                    book, "Literature");
        }
        for (int i = 0; i < userCount; i++) {
            jdbc.update("insert into user_profiles (user_id, version, profile_vector, interacted_book_ids, total_engagement_score, last_updated, created_at) values (?, 0, cast(? as vector), '{}'::uuid[], 0, now(), now())",
                    id("user", i), vector(i % 8));
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.MINUTES)
    void measureOffColdWarmAndMixedReads() throws Exception {
        boolean cacheEnabled = Boolean.parseBoolean(System.getProperty("cache.benchmark.enabled", "false"));
        String profile = System.getProperty("benchmark.profile", "smoke");
        int repeats = "smoke".equals(profile) ? 1 : 3;
        int operations = switch (profile) {
            case "smoke" -> 120;
            case "reference" -> 1000;
            case "stress" -> 3000;
            default -> throw new IllegalArgumentException("Unknown profile " + profile);
        };
        int[] concurrencies = switch (profile) {
            case "smoke" -> new int[]{1, 8};
            case "reference" -> new int[]{1, 8, 32};
            case "stress" -> new int[]{8, 32, 64};
            default -> throw new IllegalArgumentException("Unknown profile " + profile);
        };
        // Cold keys must be unique in each scenario. Reusing keys after 900 requests
        // accidentally converted the old "cold" reference workload into warm traffic.
        if (operations >= userCount - 24) {
            throw new IllegalStateException("Insufficient users for genuinely cold workload");
        }
        List<Map<String, Object>> summaries = new ArrayList<>();

        for (int concurrency : concurrencies) {
            for (int repetition = 0; repetition < repeats; repetition++) {
                for (String scenario : List.of("cold", "warm", "mixed80", "mixed95")) {
                    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
                    List<UUID> users = workload(scenario, operations);
                    Collections.shuffle(users, new Random(SEED + scenario.hashCode() + 1_009L * repetition + 101L * concurrency));
                    // Warm-up is never included in the measured samples.
                    if ("cold".equals(scenario)) {
                        // Warm HTTP/JVM/PG without populating any measured cold keys.
                        for (int i = 0; i < 30; i++) request(id("user", userCount - 1));
                    } else if (scenario.startsWith("mixed")) {
                        // Only the 12 designated hot keys are warm. The remaining
                        // ~20% of requests use unique cold keys and must MISS.
                        for (int i = 0; i < 30; i++) request(id("user", i % 12));
                    } else {
                        for (int i = 0; i < Math.min(30, users.size()); i++) request(users.get(i));
                    }
                    var pgStatistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
                    long preparedBefore = pgStatistics.getPrepareStatementCount();
                    long cpuBefore = processCpuNanos();
                    long gcBefore = gcMillis();
                    long memoryBefore = redisUsedMemoryBytes();
                    double generationBefore = timerMillis("generation");
                    double getBefore = timerMillis("get");
                    double putBefore = timerMillis("put");
                    double rankerBefore = rankingMillis();
                    long generationCountBefore = timerCount("generation");
                    long getCountBefore = timerCount("get");
                    long putCountBefore = timerCount("put");
                    double leadersBefore = singleFlightCount("leader");
                    double joinedBefore = singleFlightCount("joined");
                    double timeoutsBefore = singleFlightCount("timeout");
                    double overflowBefore = singleFlightCount("capacity");
                    double hitsBefore = cacheCount("hit");
                    double missesBefore = cacheCount("miss");
                    double errorsBefore = cacheCount("error");
                    double bypassBefore = cacheCount("bypass");
                    List<Double> latencies = Collections.synchronizedList(new ArrayList<>());
                    long start = System.nanoTime();
                    try (var pool = Executors.newFixedThreadPool(concurrency)) {
                        List<Callable<Void>> tasks = new ArrayList<>();
                        for (UUID user : users) {
                            tasks.add(() -> {
                                long t0 = System.nanoTime();
                                request(user);
                                latencies.add((System.nanoTime() - t0) / 1_000_000.0);
                                return null;
                            });
                        }
                        for (var future : pool.invokeAll(tasks)) future.get(4, TimeUnit.MINUTES);
                    }
                    long elapsed = System.nanoTime() - start;
                    latencies.sort(Double::compare);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("scenario", scenario);
                    row.put("concurrency", concurrency);
                    row.put("repeat", repetition);
                    row.put("requests", latencies.size());
                    row.put("p50_ms", percentile(latencies, .50));
                    row.put("p95_ms", percentile(latencies, .95));
                    row.put("p99_ms", percentile(latencies, .99));
                    row.put("throughput_requests_per_sec", latencies.size() * 1_000_000_000.0 / elapsed);
                    row.put("cache_generation_total_ms", timerMillis("generation") - generationBefore);
                    row.put("cache_get_total_ms", timerMillis("get") - getBefore);
                    row.put("cache_put_total_ms", timerMillis("put") - putBefore);
                    row.put("cache_generation_operations", timerCount("generation") - generationCountBefore);
                    row.put("cache_get_operations", timerCount("get") - getCountBefore);
                    row.put("cache_put_operations", timerCount("put") - putCountBefore);
                    row.put("db_ranking_total_ms", rankingMillis() - rankerBefore);
                    row.put("sql_prepared_statements", pgStatistics.getPrepareStatementCount() - preparedBefore);
                    row.put("process_cpu_ms", cpuBefore < 0 ? -1 : (processCpuNanos() - cpuBefore) / 1e6);
                    row.put("gc_collection_ms", gcMillis() - gcBefore);
                    row.put("redis_memory_delta_bytes", redisUsedMemoryBytes() - memoryBefore);
                    row.put("singleflight_leaders", singleFlightCount("leader") - leadersBefore);
                    row.put("singleflight_joins", singleFlightCount("joined") - joinedBefore);
                    row.put("singleflight_timeouts", singleFlightCount("timeout") - timeoutsBefore);
                    row.put("singleflight_capacity_bypass", singleFlightCount("capacity") - overflowBefore);
                    row.put("singleflight_leaders", singleFlightCount("leader") - leadersBefore);
        row.put("singleflight_joins", singleFlightCount("joined") - joinedBefore);
        row.put("cache_hits", cacheCount("hit") - hitsBefore);
                    row.put("cache_misses", cacheCount("miss") - missesBefore);
                    row.put("cache_errors", cacheCount("error") - errorsBefore);
                    row.put("cache_bypass", cacheCount("bypass") - bypassBefore);
                    row.put("latencies_ms", latencies); // raw, no real user IDs or tokens
                    summaries.add(row);
                    assertThat(latencies).hasSize(operations);
                }
                summaries.add(measureStampede(concurrency, repetition));
            }
        }
        String sha = System.getProperty("benchmark.commitSha", System.getenv().getOrDefault("GITHUB_SHA", "local"));
        Path dir = Path.of("target/cache-benchmark", sha);
        Files.createDirectories(dir);
        String mode = cacheEnabled ? "on" : "off";
        json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(mode + ".json").toFile(),
                Map.of("commit_sha", sha, "timestamp_utc", Instant.now().toString(),
                        "cache_enabled", cacheEnabled, "profile", profile,
                        "dataset", "600 deterministic books / " + userCount + " profile vectors, seed 360036",
                        "notes", "MockMvc authenticated HTTP, PostgreSQL pgvector + Redis Testcontainers; SQL statements from Hibernate statistics, process CPU/GC plus aggregate Redis stages. Not a network HTTP benchmark or production SLA. Warmup excluded.",
                        "measurements", summaries));
    }

    /** Uncached concurrent requests for ONE user expose stampede amplification on MISS. */
    private Map<String, Object> measureStampede(int concurrency, int repetition) throws Exception {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        UUID hotUser = id("user", userCount - 2);
        // Warm Spring/JPA without warming this user's cache key.
        for (int i = 0; i < 30; i++) request(id("user", USERS - 1));

        var sqlStats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        long sqlBefore = sqlStats.getPrepareStatementCount();
        double hitsBefore = cacheCount("hit"), missesBefore = cacheCount("miss");
        double joinedBefore = singleFlightCount("joined");
        double leadersBefore = singleFlightCount("leader");
        double generationBefore = timerMillis("generation");
        double getBefore = timerMillis("get"), putBefore = timerMillis("put");
        double rankerBefore = rankingMillis();
        long startCpu = processCpuNanos();
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Double> latency = new ArrayList<>();
        long elapsedStart = System.nanoTime();
        try (var pool = Executors.newFixedThreadPool(concurrency)) {
            List<Future<Double>> futures = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(20, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Stampede worker barrier timed out");
                    }
                    long startRead = System.nanoTime();
                    request(hotUser);
                    return (System.nanoTime() - startRead) / 1e6;
                }));
            }
            assertThat(ready.await(20, TimeUnit.SECONDS)).isTrue();
            elapsedStart = System.nanoTime();
            start.countDown();
            for (Future<Double> future : futures) latency.add(future.get(2, TimeUnit.MINUTES));
        }
        long elapsedNanos = System.nanoTime() - elapsedStart;
        latency.sort(Double::compare);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("scenario", "stampede");
        row.put("concurrency", concurrency);
        row.put("repeat", repetition);
        row.put("requests", concurrency);
        row.put("p50_ms", percentile(latency, .5));
        row.put("p95_ms", percentile(latency, .95));
        row.put("p99_ms", percentile(latency, .99));
        row.put("throughput_requests_per_sec", concurrency * 1e9 / elapsedNanos);
        row.put("cache_hits", cacheCount("hit") - hitsBefore);
        row.put("cache_misses", cacheCount("miss") - missesBefore);
        row.put("cache_generation_total_ms", timerMillis("generation") - generationBefore);
        row.put("cache_get_total_ms", timerMillis("get") - getBefore);
        row.put("cache_put_total_ms", timerMillis("put") - putBefore);
        row.put("db_ranking_total_ms", rankingMillis() - rankerBefore);
        row.put("sql_prepared_statements", sqlStats.getPrepareStatementCount() - sqlBefore);
        row.put("process_cpu_ms", startCpu < 0 ? -1.0 : (processCpuNanos() - startCpu) / 1e6);
        row.put("latencies_ms", latency);
        // With one local instance and a versioned key, 32 simultaneous requests
        // must not generate 32 copies of the ranked SQL. Allow minimal race
        // in task scheduling without depending on runner p95.
        if (Boolean.parseBoolean(System.getProperty("cache.benchmark.enabled", "false"))
                && concurrency >= 8) {
            assertThat((Number) row.get("sql_prepared_statements"))
                    .extracting(Number::longValue)
                    .as("single-flight should keep burst SQL bounded")
                    .satisfies(count -> assertThat(count).isLessThanOrEqualTo(6L));
        }
        return row;
    }

    private double timerMillis(String operation) {
        Timer timer = meterRegistry.find("vellumhub.cache.operation.duration")
                .tag("cache", "recommendations").tag("operation", operation).timer();
        return timer == null ? 0.0 : timer.totalTime(TimeUnit.MILLISECONDS);
    }

    private long timerCount(String operation) {
        Timer timer = meterRegistry.find("vellumhub.cache.operation.duration")
                .tag("cache", "recommendations").tag("operation", operation).timer();
        return timer == null ? 0L : timer.count();
    }

    private double rankingMillis() {
        double total = 0.0;
        for (String result : List.of("success", "empty", "failure")) {
            Timer timer = meterRegistry.find("vellumhub.recommendation.generation.duration")
                    .tag("operation", "recommendation_generation").tag("result", result).timer();
            if (timer != null) total += timer.totalTime(TimeUnit.MILLISECONDS);
        }
        return total;
    }

    private static long processCpuNanos() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        return bean instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getProcessCpuTime() : -1L;
    }

    private static long gcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gc.getCollectionTime() > 0) total += gc.getCollectionTime();
        }
        return total;
    }

    private long redisUsedMemoryBytes() {
        Properties info = redis.getConnectionFactory().getConnection().serverCommands().info("memory");
        return info == null ? -1L : Long.parseLong(info.getProperty("used_memory", "-1"));
    }

    private double singleFlightCount(String result) {
        var counter = meterRegistry.find("vellumhub.cache.singleflight.events")
                .tag("cache", "recommendations")
                .tag("result", result).counter();
        return counter == null ? 0.0 : counter.count();
    }

    private double cacheCount(String result) {
        var counter = meterRegistry.find("vellumhub.cache.requests")
                .tag("cache", "recommendations")
                .tag("result", result)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private List<UUID> workload(String scenario, int operations) {
        List<UUID> users = new ArrayList<>(operations);
        for (int i = 0; i < operations; i++) {
            int index = switch (scenario) {
                case "cold" -> i % userCount;
                case "warm" -> i % 8;
                case "mixed80" -> i % 5 == 0 ? 24 + (i % (userCount - 24)) : i % 12;
                case "mixed95" -> i % 20 == 0 ? 24 + (i % (USERS - 24)) : i % 12;
                default -> throw new IllegalArgumentException(scenario);
            };
            users.add(id("user", index));
        }
        return users;
    }

    private void request(UUID userId) throws Exception {
        var result = mvc.perform(get("/recommendations").param("limit", "10")
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
        assertThat(data.size()).isEqualTo(10);
    }

    private static double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return Double.NaN;
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private static UUID id(String name, int index) {
        return UUID.nameUUIDFromBytes((SEED + ":" + name + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static String vector(int axis) {
        StringBuilder text = new StringBuilder("[");
        for (int i = 0; i < 384; i++) {
            if (i != 0) text.append(',');
            text.append(i == axis ? "1.0" : "0.0");
        }
        return text.append(']').toString();
    }
}
