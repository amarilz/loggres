package com.amarildo.loggres.analysis;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Locale;

@UtilityClass
@NullMarked
public final class AggregateComparators {

    public static final Comparator<WorkloadAnalysisService.Aggregate> BY_FREQUENCY = Comparator.comparingLong(WorkloadAnalysisService.Aggregate::executionCount).reversed();
    public static final Comparator<WorkloadAnalysisService.Aggregate> BY_TOTAL = Comparator.comparing(
            WorkloadAnalysisService.Aggregate::totalDurationMillis,
            Comparator.nullsLast(Comparator.reverseOrder()));
    public static final Comparator<WorkloadAnalysisService.Aggregate> BY_AVERAGE = Comparator.comparing(
            WorkloadAnalysisService.Aggregate::averageDurationMillis,
            Comparator.nullsLast(Comparator.reverseOrder()));
    public static final Comparator<WorkloadAnalysisService.Aggregate> BY_MAX = Comparator.comparing(
            WorkloadAnalysisService.Aggregate::maxDurationMillis,
            Comparator.nullsLast(Comparator.reverseOrder()));
    public static final Comparator<WorkloadAnalysisService.Aggregate> BY_LENGTH = Comparator.comparingInt(WorkloadAnalysisService.Aggregate::queryLength).reversed();

    public static Comparator<WorkloadAnalysisService.Aggregate> forSort(@Nullable String sort) {
        if (sort == null || sort.isBlank()) {
            return BY_FREQUENCY;
        }

        return switch (sort.toLowerCase(Locale.ROOT)) {
            case "total" -> BY_TOTAL;
            case "average" -> BY_AVERAGE;
            case "max" -> BY_MAX;
            case "length" -> BY_LENGTH;
            case "frequency" -> BY_FREQUENCY;
            default -> BY_FREQUENCY;
        };
    }
}
