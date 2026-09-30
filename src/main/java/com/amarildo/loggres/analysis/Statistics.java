package com.amarildo.loggres.analysis;

import com.amarildo.loggres.parser.PostgresQuery;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

@NullMarked
public record Statistics(
        @Nullable Double total,
        @Nullable Double average,
        @Nullable Double min,
        @Nullable Double max,
        @Nullable Double p50,
        @Nullable Double p95,
        @Nullable Double p99
) {

    public static Statistics from(List<PostgresQuery> executions) {

        List<Double> durations = executions.stream()
                .map(PostgresQuery::durationMillis)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
        return fromSortedDurations(durations);
    }

    private static Statistics fromSortedDurations(List<Double> durations) {

        if (durations.isEmpty()) {
            return empty();
        }

        double total = durations.stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        return new Statistics(
                total,
                total / durations.size(),
                durations.getFirst(),
                durations.getLast(),
                percentile(durations, 0.50),
                percentile(durations, 0.95),
                percentile(durations, 0.99));
    }

    private static Statistics empty() {

        return new Statistics(null, null, null, null, null, null, null);
    }

    @Nullable
    public static Double percentile(List<Double> sortedValues, double percentile) {

        if (sortedValues.isEmpty()) {
            return null;
        }
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be between 0 and 1: " + percentile);
        }

        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, index));
    }
}
