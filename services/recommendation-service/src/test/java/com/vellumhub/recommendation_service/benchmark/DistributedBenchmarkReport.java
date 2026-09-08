package com.vellumhub.recommendation_service.benchmark;

import java.util.List;

public final class DistributedBenchmarkReport {

    private DistributedBenchmarkReport() {
    }

    public record LatencySummary(
            double p50Millis,
            double p95Millis,
            double p99Millis,
            double minMillis,
            double maxMillis
    ) {
    }

    public record ScenarioParameters(
            String id,
            int events,
            int batchSize,
            long pauseBetweenBatchesMillis,
            boolean awaitProjectionPerEvent
    ) {
    }

    public record RunMetadata(
            String project,
            String benchmarkId,
            String commitSha,
            String benchmarkProfile,
            long seed,
            List<ScenarioParameters> scenarios,
            String eventType,
            String kafkaTopic,
            String consumerGroup,
            int readWarmupRequests,
            int readMeasuredRequests,
            int repetitions,
            String javaVersion,
            String operatingSystem,
            int availableProcessors,
            String generatedAt,
            String executionCommand,
            String notes
    ) {
    }

    public record EventMeasurement(
            String scenario,
            String correlationId,
            String userId,
            String bookId,
            String publishedAt,
            String projectedAt,
            double latencyMillis,
            boolean projected,
            String failure
    ) {
    }

    public record ScenarioResult(
            String scenario,
            int submittedEvents,
            int projectedEvents,
            int failedEvents,
            long unexpectedDltEvents,
            double observedEventsPerSecond,
            LatencySummary projectionLatency,
            long maximumConsumerLag,
            double catchUpTimeMillis,
            double scenarioDurationMillis
    ) {
    }

    public record ReadMeasurement(
            int requestIndex,
            int httpStatus,
            double latencyMillis,
            int resultCount,
            String failure
    ) {
    }

    public record ReadAutonomyResult(
            int requestedReads,
            int successfulReads,
            double successRate,
            LatencySummary latency,
            String upstreamMode
    ) {
    }

    public record RawResults(
            List<EventMeasurement> eventMeasurements,
            List<ScenarioResult> scenarioResults,
            List<ReadMeasurement> readMeasurements,
            ReadAutonomyResult readAutonomy
    ) {
    }
}
