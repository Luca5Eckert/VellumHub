package com.vellumhub.recommendation_service.benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

final class DistributedBenchmarkReportWriter {

    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    void write(
            Path outputDirectory,
            DistributedBenchmarkReport.RunMetadata metadata,
            DistributedBenchmarkReport.RawResults results
    ) throws IOException {
        Files.createDirectories(outputDirectory);
        objectMapper.writeValue(outputDirectory.resolve("run-metadata.json").toFile(), metadata);
        objectMapper.writeValue(outputDirectory.resolve("raw-results.json").toFile(), results);
        Files.writeString(outputDirectory.resolve("summary.md"), summary(metadata, results), StandardCharsets.UTF_8);
    }

    String summary(
            DistributedBenchmarkReport.RunMetadata metadata,
            DistributedBenchmarkReport.RawResults results
    ) {
        StringBuilder markdown = new StringBuilder()
                .append("# VellumHub Distributed Recommendation Benchmark\n\n")
                .append("> Reproducible local/integration benchmark. Results are not a production SLA/SLO or a maximum-capacity claim.\n\n")
                .append("Commit: `").append(metadata.commitSha()).append("`  \n")
                .append("Profile: `").append(metadata.benchmarkProfile()).append("`  \n")
                .append("Signal: `").append(metadata.eventType()).append("` on `").append(metadata.kafkaTopic()).append("`  \n")
                .append("Consumer group: `").append(metadata.consumerGroup()).append("`  \n")
                .append("Seed: `").append(metadata.seed()).append("`\n\n")
                .append("## Event-to-projection convergence\n\n")
                .append("| Scenario | Submitted | Projected | Failed | DLT | Events/s | p50 | p95 | p99 | Max lag | Catch-up | Duration |\n")
                .append("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");

        for (DistributedBenchmarkReport.ScenarioResult scenario : results.scenarioResults()) {
            markdown.append("| ").append(scenario.scenario())
                    .append(" | ").append(scenario.submittedEvents())
                    .append(" | ").append(scenario.projectedEvents())
                    .append(" | ").append(scenario.failedEvents())
                    .append(" | ").append(scenario.unexpectedDltEvents())
                    .append(" | ").append(format(scenario.observedEventsPerSecond()))
                    .append(" | ").append(millis(scenario.projectionLatency().p50Millis()))
                    .append(" | ").append(millis(scenario.projectionLatency().p95Millis()))
                    .append(" | ").append(millis(scenario.projectionLatency().p99Millis()))
                    .append(" | ").append(scenario.maximumConsumerLag())
                    .append(" | ").append(millis(scenario.catchUpTimeMillis()))
                    .append(" | ").append(millis(scenario.scenarioDurationMillis()))
                    .append(" |\n");
        }

        DistributedBenchmarkReport.ReadAutonomyResult autonomy = results.readAutonomy();
        markdown.append("\n## Recommendation read autonomy\n\n")
                .append("Upstream condition: ").append(autonomy.upstreamMode()).append("  \n")
                .append("Successful reads: ").append(autonomy.successfulReads()).append("/")
                .append(autonomy.requestedReads()).append(" (")
                .append(percent(autonomy.successRate())).append(")  \n")
                .append("Latency: p50 ").append(millis(autonomy.latency().p50Millis()))
                .append(" · p95 ").append(millis(autonomy.latency().p95Millis()))
                .append(" · p99 ").append(millis(autonomy.latency().p99Millis())).append("\n\n")
                .append("## Protocol boundaries\n\n")
                .append("- Kafka and PostgreSQL/pgvector are real Testcontainers boundaries; Flyway creates the production schema.\n")
                .append("- Rating events are consumed by the production Recommendation listener and are correlated in test state through unique user IDs; no per-event production metric labels are added.\n")
                .append("- Projection timestamps are observed by polling local Recommendation state; reported event latency therefore includes polling granularity.\n")
                .append("- The embedding boundary is deterministic for book-projection setup so model inference does not contaminate event propagation measurements.\n")
                .append("- Read autonomy is exercised through the authenticated HTTP endpoint while User, Catalog, and Engagement are intentionally not running/reachable.\n")
                .append("- CI smoke validates convergence and read availability only; it does not enforce machine-dependent latency or throughput thresholds.\n\n")
                .append("## Reproduce\n\n```bash\n")
                .append(metadata.executionCommand())
                .append("\n```\n");

        return markdown.toString();
    }

    private static String millis(double value) {
        return String.format(Locale.ROOT, "%.2f ms", value);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String percent(double value) {
        return String.format(Locale.ROOT, "%.2f%%", value * 100.0);
    }
}
