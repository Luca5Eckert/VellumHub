package com.vellumhub.catalog_service.module.book.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Real authenticated MockMvc + PostgreSQL JPA + Redis A/B, run in separate
 * Maven processes using -Dcatalog.cache.benchmark.enabled=false/true.
 * Never infer production-network latency from CI MockMvc results.
 */
@Testcontainers
@Tag("catalog-cache-benchmark")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class CatalogCacheReadBenchmarkIT {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("catalog_cache_benchmark").withUsername("test").withPassword("test");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379)
            .withCommand("redis-server", "--appendonly", "no", "--save", "",
                    "--maxmemory", "128mb", "--maxmemory-policy", "noeviction");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("spring.kafka.bootstrap-servers", () -> "localhost:65535");
        r.add("spring.kafka.listener.auto-startup", () -> "false");
        r.add("spring.kafka.admin.fail-fast", () -> "false");
        r.add("management.health.kafka.enabled", () -> "false");
        r.add("jwt.public-key-location", () -> "classpath:security/test-public.pem");
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("vellumhub.cache.catalog.enabled",
                () -> System.getProperty("catalog.cache.benchmark.enabled", "false"));
        r.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
        r.add("app.outbox.enabled", () -> "false");
    }

    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired EntityManagerFactory emf;

    private static final int BOOKS = 2200;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeAll
    void seedDeterministicBooks() {
        var batch = new ArrayList<Object[]>();
        for (int i = 1; i <= BOOKS; i++) {
            batch.add(new Object[] { bookId(i), "Book " + i, "Description " + i,
                    2000 + i % 25, "Author " + i, "ISBN-" + i, 200 + i % 300,
                    "Publisher " + i });
        }
        jdbc.batchUpdate("""
                INSERT INTO books(id, title, description, release_year, author, isbn,
                                  page_count, publisher, version, created_at, updated_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, '2026-01-01 00:00:00+00', '2026-01-01 00:00:00+00')
                ON CONFLICT(id) DO NOTHING
                """, batch);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM books", Integer.class)).isGreaterThanOrEqualTo(BOOKS);
    }

    private static UUID bookId(int sequence) { return new UUID(0L, sequence); }

    private RequestPostProcessor reader() {
        return jwt().jwt(token -> token.claim("grant_type", "authorization_code"))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("SCOPE_catalog:read"));
    }

    private int get(String endpoint) {
        try {
            return http.perform(get(endpoint).with(reader())).andReturn().getResponse().getStatus();
        } catch (Exception e) {
            throw new IllegalStateException("Authenticated Catalog read failed", e);
        }
    }

    private String endpoint(String scenario, int i) {
        boolean hit = switch (scenario) {
            case "warm" -> true;
            case "cold" -> false;
            case "mixed80" -> i % 5 != 0;
            case "mixed95" -> i % 20 != 0;
            default -> throw new IllegalArgumentException(scenario);
        };
        int index = hit ? 2001 + (i % 10) : 1 + i / 2;
        if ((i & 1) == 0) return "/books/" + bookId(index);
        return "/books?pageNumber=" + (index - 1) + "&pageSize=1";
    }

    private void warmHotKeys() {
        for (int i = 2001; i <= 2010; i++) {
            assertThat(get("/books/" + bookId(i))).isEqualTo(200);
            assertThat(get("/books?pageNumber=" + (i - 1) + "&pageSize=1")).isEqualTo(200);
        }
    }

    @Test
    void comparableOffOnAuthorizedReadsProduceRawEvidence() throws Exception {
        boolean enabled = Boolean.parseBoolean(System.getProperty("catalog.cache.benchmark.enabled", "false"));
        String profile = System.getProperty("catalog.benchmark.profile", "smoke");
        String sha = System.getProperty("catalog.benchmark.commitSha", "local");
        int requests, repetitions;
        int[] concurrency;
        switch (profile) {
            case "reference" -> { requests = 1000; repetitions = 3; concurrency = new int[] {1, 8, 32}; }
            case "stress" -> { requests = 3000; repetitions = 3; concurrency = new int[] {8, 32, 64}; }
            case "smoke" -> { requests = 120; repetitions = 1; concurrency = new int[] {1, 8}; }
            default -> throw new IllegalArgumentException("Unknown benchmark profile: " + profile);
        }
        String[] scenarios = {"cold", "warm", "mixed80", "mixed95"};
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int rep = 0; rep < repetitions; rep++) {
            for (int clients : concurrency) {
                for (String scenario : scenarios) {
                    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
                    warmHotKeys(); // Outside the measurement for both OFF and ON.
                    var statistics = emf.unwrap(SessionFactory.class).getStatistics();
                    statistics.clear();
                    long sqlBefore = statistics.getPrepareStatementCount();
                    long cpuBefore = ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
                    long started = System.nanoTime();
                    long[] nanos = new long[requests];
                    AtomicInteger errors = new AtomicInteger();

                    try (var executor = Executors.newFixedThreadPool(clients)) {
                        List<Future<?>> futures = new ArrayList<>();
                        for (int i = 0; i < requests; i++) {
                            final int idx = i;
                            futures.add(executor.submit(() -> {
                                long t = System.nanoTime();
                                int status = get(endpoint(scenario, idx));
                                nanos[idx] = System.nanoTime() - t;
                                if (status != 200) errors.incrementAndGet();
                            }));
                        }
                        for (var future : futures) future.get(90, TimeUnit.SECONDS);
                    }
                    double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
                    long sql = statistics.getPrepareStatementCount() - sqlBefore;
                    Arrays.sort(nanos);
                    assertThat(errors.get()).as("All JWT-authenticated reads must return 200").isZero();
                    if (enabled && "warm".equals(scenario)) {
                        assertThat(sql).as("Warm hits must not query JPA/PG").isZero();
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("scenario", scenario);
                    row.put("concurrency", clients);
                    row.put("rep", rep);
                    row.put("requests", requests);
                    row.put("p50_ms", percentile(nanos, 0.50));
                    row.put("p95_ms", percentile(nanos, 0.95));
                    row.put("p99_ms", percentile(nanos, 0.99));
                    row.put("throughput_rps", requests / elapsedSeconds);
                    row.put("sql_statements", sql);
                    row.put("errors", errors.get());
                    row.put("cpu_coordinator_ns", Math.max(0, ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime() - cpuBefore));
                    rows.add(row);
                    System.out.printf("CATALOG_CACHE_AB profile=%s enabled=%s scenario=%s concurrency=%d rep=%d p95=%.2fms sql=%d%n",
                            profile, enabled, scenario, clients, rep, percentile(nanos, 0.95), sql);
                }
            }
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("schema_version", 1);
        report.put("commit_sha", sha);
        report.put("profile", profile);
        report.put("enabled", enabled);
        report.put("dataset_books", BOOKS);
        report.put("http", "authenticated MockMvc (no external HTTP network)");
        report.put("postgres", "postgres:15-alpine Testcontainers with real JPA/Flyway");
        report.put("redis", "redis:7.4-alpine Testcontainers noeviction");
        report.put("request_count", requests * repetitions * concurrency.length * scenarios.length);
        report.put("rows", rows);
        Path output = Path.of("target", "catalog-cache-benchmark", sha, enabled ? "on.json" : "off.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
        assertThat(Files.size(output)).isGreaterThan(100L);
    }

    private double percentile(long[] sorted, double q) {
        int index = Math.min(sorted.length - 1, (int) Math.ceil(q * sorted.length) - 1);
        return sorted[index] / 1_000_000.0;
    }
}
