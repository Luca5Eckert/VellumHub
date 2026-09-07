package com.vellumhub.recommendation_service.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Tag("distributed-benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class RecommendationFreshnessBenchmarkIT extends DistributedIntegrationTestSupport {

    private static final long SEED = 2_832_026L;
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final long POLL_INTERVAL_MILLIS = 10L;
    private static final int LIMIT = 3;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmbeddingBookProvider embeddingBookProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID signalBookId;
    private UUID targetBookId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from recommendation_genres");
        jdbcTemplate.update("delete from user_profiles");
        jdbcTemplate.update("delete from recommendations");
        jdbcTemplate.update("delete from book_features");

        signalBookId = deterministicUuid("freshness-signal-book", 0);
        targetBookId = deterministicUuid("freshness-target-book", 0);

        insertBook(signalBookId, axisVector(0), 0.50d, "Signal Book");
        insertBook(targetBookId, axisVector(0), 0.00d, "Semantic Target");

        for (int index = 0; index < 5; index++) {
            insertBook(
                    deterministicUuid("freshness-popular-decoy", index),
                    axisVector(index + 1),
                    1.00d - (index * 0.01d),
                    "Popular Decoy " + index
            );
        }
    }

    @Test
    void benchmarksEventToRecommendationFreshness() throws Exception {
        Configuration configuration = Configuration.fromSystemProperties();
        List<FreshnessMeasurement> measurements = new ArrayList<>();
        List<FreshnessScenarioResult> scenarioResults = new ArrayList<>();

        for (Scenario scenario : configuration.scenarios()) {
            ScenarioExecution execution = runScenario(scenario);
            measurements.addAll(execution.measurements());
            scenarioResults.add(execution.result());
        }

        FreshnessReport report = new FreshnessReport(
                "issue-283-event-to-recommendation-freshness",
                configuration.profile(),
                System.getProperty("benchmark.commitSha", System.getenv().getOrDefault("GITHUB_SHA", "local-uncommitted")),
                SEED,
                Instant.now().toString(),
                "CreatedRatingEvent publish -> first authenticated /recommendations response with the deterministic semantic target at rank #1",
                "Real Kafka, production rating consumer/profile update, PostgreSQL+pgvector production ranking query and authenticated HTTP read. Polling is 10 ms and burst scenarios use round-robin observation, so latency includes observation granularity.",
                scenarioResults,
                measurements
        );

        Path output = Path.of(System.getProperty(
                "benchmark.outputDir",
                "services/recommendation-service/target/distributed-benchmark/" + configuration.profile()
        ));
        Files.createDirectories(output);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.resolve("recommendation-freshness.json").toFile(), report);
        Files.writeString(output.resolve("recommendation-freshness.md"), summary(report));

        assertThat(scenarioResults).allSatisfy(result -> {
            assertThat(result.observedRecommendations()).isEqualTo(result.submittedEvents());
            assertThat(result.failedEvents()).isZero();
        });
    }

    private ScenarioExecution runScenario(Scenario scenario) throws Exception {
        UUID baselineUser = deterministicUuid("freshness-baseline-" + scenario.id(), 0);
        assertThat(firstRecommendationId(baselineUser)).isNotEqualTo(targetBookId);

        List<PendingFreshness> pending = new ArrayList<>();
        long scenarioStartedNanos = System.nanoTime();

        for (int start = 0; start < scenario.events(); start += scenario.batchSize()) {
            int end = Math.min(start + scenario.batchSize(), scenario.events());
            List<CompletableFuture<?>> sends = new ArrayList<>();
            List<PendingFreshness> batch = new ArrayList<>();

            for (int index = start; index < end; index++) {
                UUID userId = deterministicUuid("freshness-user-" + scenario.id(), index);
                PendingFreshness measurement = new PendingFreshness(scenario.id(), index, userId, System.nanoTime());
                pending.add(measurement);
                batch.add(measurement);
                sends.add(kafkaTemplate.send(
                        KafkaTopics.CREATED_RATING,
                        userId.toString(),
                        new CreatedRatingEvent(userId, signalBookId, 5)
                ));
            }

            for (CompletableFuture<?> send : sends) {
                send.get(5, TimeUnit.SECONDS);
            }

            if (scenario.awaitEachBatch()) {
                awaitRecommendationChange(batch);
            }
        }

        awaitRecommendationChange(pending);

        double durationMillis = nanosToMillis(System.nanoTime() - scenarioStartedNanos);
        List<Double> successfulLatencies = pending.stream()
                .filter(PendingFreshness::observed)
                .map(PendingFreshness::latencyMillis)
                .toList();
        var latency = LatencyStatistics.summarize(successfulLatencies);
        int observed = successfulLatencies.size();

        FreshnessScenarioResult result = new FreshnessScenarioResult(
                scenario.id(),
                scenario.events(),
                observed,
                scenario.events() - observed,
                latency,
                durationMillis
        );

        return new ScenarioExecution(pending.stream().map(PendingFreshness::toMeasurement).toList(), result);
    }

    private void awaitRecommendationChange(List<PendingFreshness> pending) throws Exception {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            boolean complete = true;
            for (PendingFreshness item : pending) {
                if (item.observed()) {
                    continue;
                }
                complete = false;
                if (targetBookId.equals(firstRecommendationId(item.userId()))) {
                    item.markObserved();
                }
            }
            if (complete || pending.stream().allMatch(PendingFreshness::observed)) {
                return;
            }
            Thread.sleep(POLL_INTERVAL_MILLIS);
        }

        pending.stream()
                .filter(item -> !item.observed())
                .forEach(item -> item.failure = "recommendation-change-timeout");
    }

    private UUID firstRecommendationId(UUID userId) throws Exception {
        var result = mockMvc.perform(get("/recommendations")
                        .param("limit", Integer.toString(LIMIT))
                        .param("offset", "0")
                        .with(jwt().jwt(token -> token
                                .subject("freshness@vellumhub.test")
                                .claim("user_id", userId.toString())
                                .claim("roles", List.of("USER")))))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var payload = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(payload.isArray()).isTrue();
        assertThat(payload.size()).isPositive();
        return UUID.fromString(payload.get(0).get("id").asText());
    }

    private void insertBook(UUID bookId, float[] embedding, double popularity, String title) {
        jdbcTemplate.update(
                "insert into book_features (book_id, embedding, popularity_score, last_updated) values (?, cast(? as vector), ?, now())",
                bookId,
                vectorLiteral(embedding),
                popularity
        );
        jdbcTemplate.update(
                "insert into recommendations (book_id, title, description, release_year, cover_url, author) values (?, ?, ?, ?, ?, ?)",
                bookId,
                title,
                "Deterministic recommendation freshness benchmark",
                2026,
                "https://example.test/freshness/" + bookId + ".jpg",
                "VellumHub Benchmark"
        );
    }

    private static float[] axisVector(int axis) {
        float[] vector = new float[384];
        vector[Math.min(axis, vector.length - 1)] = 1.0f;
        return vector;
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) result.append(',');
            result.append(vector[index]);
        }
        return result.append(']').toString();
    }

    private static UUID deterministicUuid(String scope, int index) {
        return UUID.nameUUIDFromBytes((SEED + ":" + scope + ":" + index).getBytes(StandardCharsets.UTF_8));
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private static String summary(FreshnessReport report) {
        StringBuilder text = new StringBuilder();
        text.append("# Recommendation freshness benchmark\n\n");
        text.append("**Metric:** `CreatedRatingEvent publish -> first authenticated /recommendations response reflecting the interaction at rank #1`.\n\n");
        text.append("| Scenario | Events | Reflected | Failed | p50 | p95 | p99 | Duration |\n");
        text.append("|---|---:|---:|---:|---:|---:|---:|---:|\n");
        for (FreshnessScenarioResult scenario : report.scenarioResults()) {
            text.append(String.format(
                    "| %s | %d | %d | %d | %.2f ms | %.2f ms | %.2f ms | %.2f ms |%n",
                    scenario.scenario(), scenario.submittedEvents(), scenario.observedRecommendations(), scenario.failedEvents(),
                    scenario.latency().p50Millis(), scenario.latency().p95Millis(), scenario.latency().p99Millis(), scenario.scenarioDurationMillis()
            ));
        }
        text.append("\n## Boundary\n\n");
        text.append(report.notes()).append("\n\n");
        text.append("This is controlled CI/integration evidence, not a production SLA or maximum-capacity claim. ");
        text.append("The benchmark demonstrates recommendation freshness through the real event-driven update and HTTP serving path.\n");
        return text.toString();
    }

    private record Configuration(String profile, List<Scenario> scenarios) {
        static Configuration fromSystemProperties() {
            String profile = System.getProperty("benchmark.profile", "reference");
            if ("smoke".equalsIgnoreCase(profile)) {
                return new Configuration("smoke", List.of(new Scenario("smoke", 3, 3, false)));
            }
            if (!"reference".equalsIgnoreCase(profile)) {
                throw new IllegalArgumentException("Unsupported benchmark.profile: " + profile);
            }
            return new Configuration("reference", List.of(
                    new Scenario("sequential", 10, 1, true),
                    new Scenario("small-burst", 30, 10, false),
                    new Scenario("medium-burst", 90, 30, false)
            ));
        }
    }

    private record Scenario(String id, int events, int batchSize, boolean awaitEachBatch) {
    }

    private record FreshnessMeasurement(
            String scenario,
            int eventIndex,
            String userId,
            double latencyMillis,
            boolean recommendationReflected,
            String failure
    ) {
    }

    private record FreshnessScenarioResult(
            String scenario,
            int submittedEvents,
            int observedRecommendations,
            int failedEvents,
            DistributedBenchmarkReport.LatencySummary latency,
            double scenarioDurationMillis
    ) {
    }

    private record FreshnessReport(
            String benchmarkId,
            String profile,
            String commitSha,
            long seed,
            String generatedAt,
            String metricDefinition,
            String notes,
            List<FreshnessScenarioResult> scenarioResults,
            List<FreshnessMeasurement> measurements
    ) {
    }

    private record ScenarioExecution(List<FreshnessMeasurement> measurements, FreshnessScenarioResult result) {
    }

    private static final class PendingFreshness {
        private final String scenario;
        private final int eventIndex;
        private final UUID userId;
        private final long publishedNanos;
        private double latencyMillis;
        private boolean observed;
        private String failure;

        private PendingFreshness(String scenario, int eventIndex, UUID userId, long publishedNanos) {
            this.scenario = scenario;
            this.eventIndex = eventIndex;
            this.userId = userId;
            this.publishedNanos = publishedNanos;
        }

        UUID userId() {
            return userId;
        }

        boolean observed() {
            return observed;
        }

        double latencyMillis() {
            return latencyMillis;
        }

        void markObserved() {
            if (!observed) {
                observed = true;
                latencyMillis = nanosToMillis(System.nanoTime() - publishedNanos);
            }
        }

        FreshnessMeasurement toMeasurement() {
            return new FreshnessMeasurement(scenario, eventIndex, userId.toString(), latencyMillis, observed, failure);
        }
    }
}
