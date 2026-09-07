package com.vellumhub.recommendation_service.benchmark;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedBenchmarkReportWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void calculatesNearestRankLatencyPercentiles() {
        var summary = LatencyStatistics.summarize(List.of(
                1.0, 2.0, 3.0, 4.0, 5.0,
                6.0, 7.0, 8.0, 9.0, 10.0,
                11.0, 12.0, 13.0, 14.0, 15.0,
                16.0, 17.0, 18.0, 19.0, 20.0
        ));

        assertThat(summary.p50Millis()).isEqualTo(10.0);
        assertThat(summary.p95Millis()).isEqualTo(19.0);
        assertThat(summary.p99Millis()).isEqualTo(20.0);
        assertThat(summary.minMillis()).isEqualTo(1.0);
        assertThat(summary.maxMillis()).isEqualTo(20.0);
    }

    @Test
    void writesMachineReadableEvidenceAndScopedSummary() throws Exception {
        var scenario = new DistributedBenchmarkReport.ScenarioParameters("smoke", 3, 3, 0, false);
        var metadata = new DistributedBenchmarkReport.RunMetadata(
                "VellumHub",
                "issue-283-distributed-recommendation",
                "abc123",
                "smoke",
                2832026L,
                List.of(scenario),
                "CreatedRatingEvent",
                "created-rating",
                "recommendation-service",
                1,
                5,
                1,
                "21",
                "Linux amd64",
                4,
                "2026-09-07T00:00:00Z",
                "mvn test",
                "local benchmark"
        );
        var latency = new DistributedBenchmarkReport.LatencySummary(2.0, 3.0, 3.0, 1.0, 3.0);
        var raw = new DistributedBenchmarkReport.RawResults(
                List.of(new DistributedBenchmarkReport.EventMeasurement(
                        "smoke", "event-1", "user-1", "book-1",
                        "2026-09-07T00:00:00Z", "2026-09-07T00:00:00.003Z",
                        3.0, true, null
                )),
                List.of(new DistributedBenchmarkReport.ScenarioResult(
                        "smoke", 3, 3, 0, 0, 100.0, latency, 2, 5.0, 30.0
                )),
                List.of(new DistributedBenchmarkReport.ReadMeasurement(0, 200, 2.0, 10, null)),
                new DistributedBenchmarkReport.ReadAutonomyResult(
                        1, 1, 1.0, latency, "upstreams intentionally unavailable"
                )
        );

        new DistributedBenchmarkReportWriter().write(tempDir, metadata, raw);

        assertThat(tempDir.resolve("run-metadata.json")).exists();
        assertThat(tempDir.resolve("raw-results.json")).exists();
        assertThat(tempDir.resolve("summary.md")).exists();

        String summary = Files.readString(tempDir.resolve("summary.md"));
        assertThat(summary)
                .contains("not a production SLA/SLO")
                .contains("Event-to-projection convergence")
                .contains("Recommendation read autonomy")
                .contains("CI smoke validates convergence");
    }
}
