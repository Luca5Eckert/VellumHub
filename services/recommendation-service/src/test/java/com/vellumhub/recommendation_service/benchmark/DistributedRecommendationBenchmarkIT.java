package com.vellumhub.recommendation_service.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vellumhub.kafka.contracts.KafkaConsumerGroups;
import com.vellumhub.kafka.contracts.KafkaTopics;
import com.vellumhub.kafka.contracts.book.CreateBookEvent;
import com.vellumhub.kafka.contracts.engagement.CreatedRatingEvent;
import com.vellumhub.recommendation_service.module.book_feature.domain.port.EmbeddingBookProvider;
import com.vellumhub.recommendation_service.support.DistributedIntegrationTestSupport;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@Tag("distributed-benchmark")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class DistributedRecommendationBenchmarkIT extends DistributedIntegrationTestSupport {

    private static final long SEED = 2_832_026L;
    private static final Duration BENCHMARK_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration PROJECTION_POLL_INTERVAL = Duration.ofMillis(10);
    private static final int RECOMMENDATION_CATALOG_SIZE = 15;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmbeddingBookProvider embeddingBookProvider;

    private AdminClient adminClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from recommendation_genres");
        jdbcTemplate.update("delete from user_profiles");
        jdbcTemplate.update("delete from recommendations");
        jdbcTemplate.update("delete from book_features");

        float[] embedding = new float[384];
        Arrays.fill(embedding, 0.25f);
        when(embeddingBookProvider.of(anyString(), anyString(), anyString(), anyList()))
                .thenReturn(embedding);

        adminClient = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()
        ));
    }

    @AfterEach
    void tearDown() {
        if (adminClient != null) {
            adminClient.close(Duration.ofSeconds(5));
        }
    }

    @Test
    void benchmarksEventConvergenceAndReadAutonomy() throws Exception {
        BenchmarkConfiguration configuration = BenchmarkConfiguration.fromSystemProperties();
        List<DistributedBenchmarkReport.EventMeasurement> eventMeasurements = new ArrayList<>();
        List<DistributedBenchmarkReport.ScenarioResult> scenarioResults = new ArrayList<>();

        for (DistributedBenchmarkReport.ScenarioParameters scenario : configuration.scenarios()) {
            ScenarioExecution execution = runPropagationScenario(scenario);
            eventMeasurements.addAll(execution.measurements());
            scenarioResults.add(execution.result());
        }

        ReadExecution readExecution = runReadAutonomy(configuration);

        DistributedBenchmarkReport.RunMetadata metadata = metadata(configuration);
        DistributedBenchmarkReport.RawResults rawResults = new DistributedBenchmarkReport.RawResults(
                List.copyOf(eventMeasurements),
                List.copyOf(scenarioResults),
                readExecution.measurements(),
                readExecution.result()
        );

        Path output = Path.of(System.getProperty(
                "benchmark.outputDir",
                "services/recommendation-service/target/distributed-benchmark/" + configuration.profile()
        ));
        new DistributedBenchmarkReportWriter().write(output, metadata, rawResults);

        assertThat(scenarioResults)
                .allSatisfy(result -> {
                    assertThat(result.projectedEvents()).isEqualTo(result.submittedEvents());
                    assertThat(result.failedEvents()).isZero();
                    assertThat(result.unexpectedDltEvents()).isZero();
                    assertThat(result.catchUpTimeMillis()).isLessThan(BENCHMARK_TIMEOUT.toMillis());
                });
        assertThat(readExecution.result().successRate()).isEqualTo(1.0);
        assertThat(output.resolve("run-metadata.json")).exists();
        assertThat(output.resolve("raw-results.json")).exists();
        assertThat(output.resolve("summary.md")).exists();
    }

    private ScenarioExecution runPropagationScenario(
            DistributedBenchmarkReport.ScenarioParameters scenario
    ) throws Exception {
        UUID bookId = deterministicUuid("signal-book-" + scenario.id(), 0);
        insertSignalBook(bookId);

        long dltBefore = topicEndOffset(KafkaTopics.CREATED_RATING + KafkaTopics.DLT_SUFFIX);
        List<PendingEvent> pending = new ArrayList<>();
        long scenarioStartedNanos = System.nanoTime();
        long lastPublishedNanos = scenarioStartedNanos;
        long maximumLag = 0L;

        for (int start = 0; start < scenario.events(); start += scenario.batchSize()) {
            int end = Math.min(start + scenario.batchSize(), scenario.events());
            List<PublishAttempt> attempts = new ArrayList<>();

            for (int index = start; index < end; index++) {
                UUID userId = deterministicUuid("signal-user-" + scenario.id(), index);
                UUID correlationId = deterministicUuid("signal-correlation-" + scenario.id(), index);
                PendingEvent event = new PendingEvent(
                        scenario.id(),
                        correlationId,
                        userId,
                        bookId,
                        Instant.now().toString(),
                        System.nanoTime()
                );
                pending.add(event);
                lastPublishedNanos = event.publishedNanos();

                CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(
                        KafkaTopics.CREATED_RATING,
                        userId.toString(),
                        new CreatedRatingEvent(userId, bookId, 5)
                );
                attempts.add(new PublishAttempt(event, future));
            }

            for (PublishAttempt attempt : attempts) {
                try {
                    attempt.future().get(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    attempt.event().markFailure("publish: " + exception.getClass().getSimpleName());
                }
            }

            maximumLag = Math.max(maximumLag, consumerLag(KafkaTopics.CREATED_RATING));

            if (scenario.awaitProjectionPerEvent()) {
                maximumLag = Math.max(maximumLag, awaitProjections(attempts.stream()
                        .map(PublishAttempt::event)
                        .toList()));
            } else {
                markProjected(pending);
            }

            if (scenario.pauseBetweenBatchesMillis() > 0 && end < scenario.events()) {
                Thread.sleep(scenario.pauseBetweenBatchesMillis());
            }
        }

        maximumLag = Math.max(maximumLag, awaitProjections(pending));
        double catchUpMillis = awaitConsumerCatchUp(lastPublishedNanos);

        long scenarioFinishedNanos = System.nanoTime();
        List<DistributedBenchmarkReport.EventMeasurement> measurements = pending.stream()
                .map(PendingEvent::toMeasurement)
                .toList();

        int projected = (int) pending.stream().filter(PendingEvent::projected).count();
        int failures = scenario.events() - projected;
        long dltAfter = topicEndOffset(KafkaTopics.CREATED_RATING + KafkaTopics.DLT_SUFFIX);
        double durationMillis = nanosToMillis(scenarioFinishedNanos - scenarioStartedNanos);
        double eventsPerSecond = durationMillis == 0.0
                ? 0.0
                : projected / (durationMillis / 1_000.0);

        var latency = LatencyStatistics.summarize(
                pending.stream()
                        .filter(PendingEvent::projected)
                        .map(PendingEvent::latencyMillis)
                        .toList()
        );

        var result = new DistributedBenchmarkReport.ScenarioResult(
                scenario.id(),
                scenario.events(),
                projected,
                failures,
                Math.max(0L, dltAfter - dltBefore),
                eventsPerSecond,
                latency,
                maximumLag,
                catchUpMillis,
                durationMillis
        );

        return new ScenarioExecution(measurements, result);
    }

    private ReadExecution runReadAutonomy(BenchmarkConfiguration configuration) throws Exception {
        UUID readUserId = deterministicUuid("read-user", 0);
        List<UUID> books = materializeRecommendationCatalog();

        PendingEvent profileSignal = new PendingEvent(
                "read-autonomy-setup",
                deterministicUuid("read-correlation", 0),
                readUserId,
                books.getFirst(),
                Instant.now().toString(),
                System.nanoTime()
        );
        kafkaTemplate.send(
                KafkaTopics.CREATED_RATING,
                readUserId.toString(),
                new CreatedRatingEvent(readUserId, books.getFirst(), 5)
        ).get(5, TimeUnit.SECONDS);
        awaitProjections(List.of(profileSignal));

        for (int index = 0; index < configuration.readWarmupRequests(); index++) {
            DistributedBenchmarkReport.ReadMeasurement warmup = recommendationRead(readUserId, -1 - index);
            assertThat(warmup.httpStatus()).isEqualTo(200);
            assertThat(warmup.resultCount()).isPositive();
        }

        List<DistributedBenchmarkReport.ReadMeasurement> measurements = new ArrayList<>();
        for (int index = 0; index < configuration.readMeasuredRequests(); index++) {
            measurements.add(recommendationRead(readUserId, index));
        }

        int successful = (int) measurements.stream()
                .filter(measurement -> measurement.httpStatus() == 200 && measurement.failure() == null)
                .count();
        var latency = LatencyStatistics.summarize(
                measurements.stream()
                        .filter(measurement -> measurement.failure() == null)
                        .map(DistributedBenchmarkReport.ReadMeasurement::latencyMillis)
                        .toList()
        );

        String upstreamMode = "User, Catalog, and Engagement are intentionally not started; "
                + "their test endpoints resolve to 127.0.0.1:1. Reads execute only against Recommendation local projections.";

        return new ReadExecution(
                List.copyOf(measurements),
                new DistributedBenchmarkReport.ReadAutonomyResult(
                        measurements.size(),
                        successful,
                        successful / (double) measurements.size(),
                        latency,
                        upstreamMode
                )
        );
    }

    private List<UUID> materializeRecommendationCatalog() throws Exception {
        List<UUID> bookIds = new ArrayList<>();
        List<CompletableFuture<SendResult<String, Object>>> sends = new ArrayList<>();

        for (int index = 0; index < RECOMMENDATION_CATALOG_SIZE; index++) {
            UUID bookId = deterministicUuid("read-book", index);
            bookIds.add(bookId);
            CreateBookEvent event = new CreateBookEvent(
                    bookId,
                    "Benchmark Book " + index,
                    "Materialized local recommendation state " + index,
                    2026,
                    "https://example.test/benchmark/" + index + ".jpg",
                    "VellumHub Benchmark",
                    List.of("distributed-systems", "benchmark")
            );
            sends.add(kafkaTemplate.send(KafkaTopics.CREATED_BOOK, bookId.toString(), event));
        }

        for (CompletableFuture<SendResult<String, Object>> send : sends) {
            send.get(5, TimeUnit.SECONDS);
        }

        long deadline = System.nanoTime() + BENCHMARK_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            Integer features = jdbcTemplate.queryForObject(
                    "select count(*) from book_features where book_id in (" + placeholders(bookIds.size()) + ")",
                    Integer.class,
                    bookIds.toArray()
            );
            Integer recommendations = jdbcTemplate.queryForObject(
                    "select count(*) from recommendations where book_id in (" + placeholders(bookIds.size()) + ")",
                    Integer.class,
                    bookIds.toArray()
            );
            if (features != null && recommendations != null
                    && features == bookIds.size() && recommendations == bookIds.size()) {
                return List.copyOf(bookIds);
            }
            Thread.sleep(PROJECTION_POLL_INTERVAL.toMillis());
        }

        throw new IllegalStateException("Timed out materializing recommendation catalog");
    }

    private DistributedBenchmarkReport.ReadMeasurement recommendationRead(UUID userId, int index) {
        long started = System.nanoTime();
        try {
            var result = mockMvc.perform(get("/recommendations")
                            .param("limit", "10")
                            .param("offset", "0")
                            .with(jwt().jwt(token -> token
                                    .subject("benchmark@vellumhub.test")
                                    .claim("user_id", userId.toString())
                                    .claim("roles", List.of("USER")))))
                    .andReturn();

            double latencyMillis = nanosToMillis(System.nanoTime() - started);
            int status = result.getResponse().getStatus();
            int resultCount = status == 200
                    ? objectMapper.readTree(result.getResponse().getContentAsString()).size()
                    : 0;
            return new DistributedBenchmarkReport.ReadMeasurement(
                    index, status, latencyMillis, resultCount, null
            );
        } catch (Exception exception) {
            return new DistributedBenchmarkReport.ReadMeasurement(
                    index,
                    0,
                    nanosToMillis(System.nanoTime() - started),
                    0,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private long awaitProjections(List<PendingEvent> events) throws Exception {
        long maximumLag = 0L;
        long deadline = System.nanoTime() + BENCHMARK_TIMEOUT.toNanos();

        while (System.nanoTime() < deadline) {
            markProjected(events);
            maximumLag = Math.max(maximumLag, consumerLag(KafkaTopics.CREATED_RATING));
            boolean complete = events.stream()
                    .filter(event -> event.failure() == null)
                    .allMatch(PendingEvent::projected);
            if (complete) {
                return maximumLag;
            }
            Thread.sleep(PROJECTION_POLL_INTERVAL.toMillis());
        }

        events.stream()
                .filter(event -> !event.projected() && event.failure() == null)
                .forEach(event -> event.markFailure("projection-timeout"));
        return maximumLag;
    }

    private void markProjected(List<PendingEvent> events) {
        for (PendingEvent event : events) {
            if (event.projected() || event.failure() != null) {
                continue;
            }
            Long count = jdbcTemplate.queryForObject(
                    "select count(*) from user_profiles where user_id = ?",
                    Long.class,
                    event.userId()
            );
            if (count != null && count == 1L) {
                event.markProjected();
            }
        }
    }

    private double awaitConsumerCatchUp(long lastPublishedNanos) throws Exception {
        long deadline = System.nanoTime() + BENCHMARK_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (consumerLag(KafkaTopics.CREATED_RATING) == 0L) {
                return nanosToMillis(System.nanoTime() - lastPublishedNanos);
            }
            Thread.sleep(PROJECTION_POLL_INTERVAL.toMillis());
        }
        return BENCHMARK_TIMEOUT.toMillis();
    }

    private long consumerLag(String topic) throws Exception {
        var description = adminClient.describeTopics(List.of(topic))
                .allTopicNames()
                .get(5, TimeUnit.SECONDS)
                .get(topic);
        Map<TopicPartition, OffsetSpec> latestRequests = new LinkedHashMap<>();
        description.partitions().forEach(partition ->
                latestRequests.put(new TopicPartition(topic, partition.partition()), OffsetSpec.latest())
        );

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = adminClient
                .listOffsets(latestRequests)
                .all()
                .get(5, TimeUnit.SECONDS);
        Map<TopicPartition, OffsetAndMetadata> committed = adminClient
                .listConsumerGroupOffsets(KafkaConsumerGroups.RECOMMENDATION_SERVICE)
                .partitionsToOffsetAndMetadata()
                .get(5, TimeUnit.SECONDS);

        long lag = 0L;
        for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : latest.entrySet()) {
            OffsetAndMetadata offset = committed.get(entry.getKey());
            long committedOffset = offset == null ? 0L : offset.offset();
            lag += Math.max(0L, entry.getValue().offset() - committedOffset);
        }
        return lag;
    }

    private long topicEndOffset(String topic) throws Exception {
        if (!adminClient.listTopics().names().get(5, TimeUnit.SECONDS).contains(topic)) {
            return 0L;
        }
        var description = adminClient.describeTopics(List.of(topic))
                .allTopicNames()
                .get(5, TimeUnit.SECONDS)
                .get(topic);
        Map<TopicPartition, OffsetSpec> requests = new HashMap<>();
        description.partitions().forEach(partition ->
                requests.put(new TopicPartition(topic, partition.partition()), OffsetSpec.latest())
        );
        return adminClient.listOffsets(requests)
                .all()
                .get(5, TimeUnit.SECONDS)
                .values()
                .stream()
                .mapToLong(ListOffsetsResult.ListOffsetsResultInfo::offset)
                .sum();
    }

    private void insertSignalBook(UUID bookId) {
        jdbcTemplate.update(
                """
                insert into book_features (book_id, embedding, popularity_score, last_updated)
                values (?, cast(? as vector), ?, now())
                """,
                bookId,
                vectorLiteral(0.25f),
                1.0d
        );
    }

    private DistributedBenchmarkReport.RunMetadata metadata(BenchmarkConfiguration configuration) {
        String commitSha = System.getProperty(
                "benchmark.commitSha",
                System.getenv().getOrDefault("GITHUB_SHA", "local-uncommitted")
        );
        String command = System.getProperty(
                "benchmark.command",
                "mvn -pl services/recommendation-service -am "
                        + "-Dtest=DistributedRecommendationBenchmarkIT "
                        + "-Dsurefire.failIfNoSpecifiedTests=false "
                        + "-Dgroups=distributed-benchmark "
                        + "-Dbenchmark.profile=" + configuration.profile() + " test"
        );
        return new DistributedBenchmarkReport.RunMetadata(
                "VellumHub",
                "issue-283-distributed-recommendation",
                commitSha,
                configuration.profile(),
                SEED,
                configuration.scenarios(),
                CreatedRatingEvent.class.getSimpleName(),
                KafkaTopics.CREATED_RATING,
                KafkaConsumerGroups.RECOMMENDATION_SERVICE,
                configuration.readWarmupRequests(),
                configuration.readMeasuredRequests(),
                1,
                System.getProperty("java.version"),
                System.getProperty("os.name") + " " + System.getProperty("os.arch"),
                Runtime.getRuntime().availableProcessors(),
                Instant.now().toString(),
                command,
                "Real Kafka + pgvector/PostgreSQL + Flyway + production consumers. "
                        + "Deterministic test embedding is used only while materializing book projections. "
                        + "Latency includes projection polling granularity and is not a production SLA."
        );
    }

    private static UUID deterministicUuid(String scope, int index) {
        return UUID.nameUUIDFromBytes(
                (SEED + ":" + scope + ":" + index).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String vectorLiteral(float value) {
        StringBuilder vector = new StringBuilder("[");
        for (int index = 0; index < 384; index++) {
            if (index > 0) {
                vector.append(',');
            }
            vector.append(value);
        }
        return vector.append(']').toString();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private record PublishAttempt(
            PendingEvent event,
            CompletableFuture<SendResult<String, Object>> future
    ) {
    }

    private record ScenarioExecution(
            List<DistributedBenchmarkReport.EventMeasurement> measurements,
            DistributedBenchmarkReport.ScenarioResult result
    ) {
    }

    private record ReadExecution(
            List<DistributedBenchmarkReport.ReadMeasurement> measurements,
            DistributedBenchmarkReport.ReadAutonomyResult result
    ) {
    }

    private record BenchmarkConfiguration(
            String profile,
            List<DistributedBenchmarkReport.ScenarioParameters> scenarios,
            int readWarmupRequests,
            int readMeasuredRequests
    ) {
        private static BenchmarkConfiguration fromSystemProperties() {
            String profile = System.getProperty("benchmark.profile", "reference");
            if ("smoke".equalsIgnoreCase(profile)) {
                int events = intProperty("benchmark.smoke.events", 3);
                return new BenchmarkConfiguration(
                        "smoke",
                        List.of(new DistributedBenchmarkReport.ScenarioParameters(
                                "smoke",
                                events,
                                intProperty("benchmark.smoke.batchSize", events),
                                longProperty("benchmark.smoke.pauseMs", 0L),
                                false
                        )),
                        intProperty("benchmark.read.warmup", 1),
                        intProperty("benchmark.read.requests", 5)
                );
            }

            if (!"reference".equalsIgnoreCase(profile)) {
                throw new IllegalArgumentException("Unsupported benchmark.profile: " + profile);
            }

            return new BenchmarkConfiguration(
                    "reference",
                    List.of(
                            new DistributedBenchmarkReport.ScenarioParameters(
                                    "sequential",
                                    intProperty("benchmark.sequential.events", 10),
                                    1,
                                    longProperty("benchmark.sequential.pauseMs", 0L),
                                    true
                            ),
                            new DistributedBenchmarkReport.ScenarioParameters(
                                    "small-burst",
                                    intProperty("benchmark.small.events", 30),
                                    intProperty("benchmark.small.batchSize", 10),
                                    longProperty("benchmark.small.pauseMs", 0L),
                                    false
                            ),
                            new DistributedBenchmarkReport.ScenarioParameters(
                                    "medium-burst",
                                    intProperty("benchmark.medium.events", 90),
                                    intProperty("benchmark.medium.batchSize", 30),
                                    longProperty("benchmark.medium.pauseMs", 0L),
                                    false
                            )
                    ),
                    intProperty("benchmark.read.warmup", 3),
                    intProperty("benchmark.read.requests", 30)
            );
        }

        private static int intProperty(String key, int defaultValue) {
            return Integer.parseInt(System.getProperty(key, Integer.toString(defaultValue)));
        }

        private static long longProperty(String key, long defaultValue) {
            return Long.parseLong(System.getProperty(key, Long.toString(defaultValue)));
        }
    }

    private static final class PendingEvent {
        private final String scenario;
        private final UUID correlationId;
        private final UUID userId;
        private final UUID bookId;
        private final String publishedAt;
        private final long publishedNanos;
        private String projectedAt;
        private double latencyMillis;
        private String failure;

        private PendingEvent(
                String scenario,
                UUID correlationId,
                UUID userId,
                UUID bookId,
                String publishedAt,
                long publishedNanos
        ) {
            this.scenario = scenario;
            this.correlationId = correlationId;
            this.userId = userId;
            this.bookId = bookId;
            this.publishedAt = publishedAt;
            this.publishedNanos = publishedNanos;
        }

        void markProjected() {
            if (projectedAt == null) {
                projectedAt = Instant.now().toString();
                latencyMillis = nanosToMillis(System.nanoTime() - publishedNanos);
            }
        }

        void markFailure(String failure) {
            this.failure = failure;
        }

        boolean projected() {
            return projectedAt != null;
        }

        String failure() {
            return failure;
        }

        UUID userId() {
            return userId;
        }

        long publishedNanos() {
            return publishedNanos;
        }

        double latencyMillis() {
            return latencyMillis;
        }

        DistributedBenchmarkReport.EventMeasurement toMeasurement() {
            return new DistributedBenchmarkReport.EventMeasurement(
                    scenario,
                    correlationId.toString(),
                    userId.toString(),
                    bookId.toString(),
                    publishedAt,
                    projectedAt,
                    latencyMillis,
                    projected(),
                    failure
            );
        }
    }
}
