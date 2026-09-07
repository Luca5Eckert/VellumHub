package com.vellumhub.recommendation_service.benchmark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class LatencyStatistics {

    private LatencyStatistics() {
    }

    static DistributedBenchmarkReport.LatencySummary summarize(List<Double> valuesMillis) {
        if (valuesMillis == null || valuesMillis.isEmpty()) {
            return new DistributedBenchmarkReport.LatencySummary(0.0, 0.0, 0.0, 0.0, 0.0);
        }

        List<Double> sorted = new ArrayList<>(valuesMillis);
        sorted.sort(Comparator.naturalOrder());

        return new DistributedBenchmarkReport.LatencySummary(
                percentile(sorted, 0.50),
                percentile(sorted, 0.95),
                percentile(sorted, 0.99),
                sorted.getFirst(),
                sorted.getLast()
        );
    }

    private static double percentile(List<Double> sorted, double percentile) {
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(index);
    }
}
