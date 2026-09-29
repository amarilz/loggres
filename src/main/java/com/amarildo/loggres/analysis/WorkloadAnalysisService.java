package com.amarildo.loggres.analysis;

import com.amarildo.loggres.parser.PostgresQuery;
import com.amarildo.loggres.sql.QueryIdentity;
import com.amarildo.loggres.sql.SqlCategory;
import com.amarildo.loggres.sql.SqlFormatter;
import com.amarildo.loggres.sql.SqlNormalizer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@NullMarked
public class WorkloadAnalysisService {

    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_BUCKET_SECONDS = 86_400;
    private static final int DASHBOARD_TOP_LIMIT = 5;
    private static final double OUTLIER_MULTIPLIER = 3.0;

    private final Map<UUID, Analysis> analyses = new ConcurrentHashMap<>();

    private static OffsetDateTime bucket(OffsetDateTime timestamp, int seconds) {
        long epoch = timestamp.toEpochSecond();
        long bucketEpoch = epoch - Math.floorMod(epoch, seconds);

        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(bucketEpoch),
                timestamp.getOffset()
        );
    }

    @Nullable
    private static OffsetDateTime minTimestamp(List<PostgresQuery> executions) {
        return executions.stream()
                .map(PostgresQuery::timestamp)
                .min(OffsetDateTime::compareTo)
                .orElse(null);
    }

    @Nullable
    private static OffsetDateTime maxTimestamp(List<PostgresQuery> executions) {
        return executions.stream()
                .map(PostgresQuery::timestamp)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
    }

    private static List<Double> sortedDurations(List<PostgresQuery> executions) {
        return executions.stream()
                .map(PostgresQuery::durationMillis)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    private static <T> long distinctCount(
            List<PostgresQuery> executions,
            Function<PostgresQuery, T> extractor
    ) {
        return executions.stream()
                .map(extractor)
                .distinct()
                .count();
    }

    private static <T> Map<T, Long> frequencies(
            List<PostgresQuery> executions,
            Function<PostgresQuery, T> extractor
    ) {
        return executions.stream()
                .collect(Collectors.groupingBy(extractor, Collectors.counting()));
    }

    private static <T> PageSlice<T> paginate(List<T> values, int requestedPage, int requestedSize) {
        int page = Math.max(0, requestedPage);
        int size = Math.clamp(requestedSize, 1, MAX_PAGE_SIZE);

        long requestedFrom = (long) page * size;
        int from = (int) Math.min(requestedFrom, values.size());
        int to = Math.min(from + size, values.size());

        return new PageSlice<>(values.subList(from, to), page, size);
    }

    public AnalysisResponse create(List<PostgresQuery> executions, List<?> issues) {
        var analysis = Analysis.create(executions, issues.size());
        analyses.put(analysis.id(), analysis);

        return new AnalysisResponse(
                analysis.id(),
                buildSummary(analysis.executions()),
                issues
        );
    }

    public Summary summary(UUID analysisId, Filters filters) {
        var analysis = getAnalysis(analysisId);

        var executions = analysis.executions().stream()
                .filter(filters.predicate())
                .toList();

        return buildSummary(executions);
    }

    public Dashboard dashboard(UUID analysisId) {
        var analysis = getAnalysis(analysisId);
        var aggregates = buildAggregates(analysis.executions());

        var durations = sortedDurations(analysis.executions());
        var median = Statistics.percentile(durations, 0.50);
        var outlierThreshold = median == null
                ? null
                : median * OUTLIER_MULTIPLIER;

        long outlierCount = outlierThreshold == null
                ? 0
                : analysis.executions().stream()
                .map(PostgresQuery::durationMillis)
                .filter(Objects::nonNull)
                .filter(duration -> duration > outlierThreshold)
                .count();

        return new Dashboard(
                topQueries(aggregates, AggregateComparators.BY_TOTAL),
                topQueries(aggregates, AggregateComparators.BY_AVERAGE),
                topQueries(aggregates, AggregateComparators.BY_FREQUENCY),
                outlierCount,
                analysis.unrecognizedRowCount(),
                minTimestamp(analysis.executions()),
                maxTimestamp(analysis.executions())
        );
    }

    public QueryPage queries(
            UUID analysisId,
            Filters filters,
            String sort,
            int page,
            int size
    ) {
        var analysis = getAnalysis(analysisId);

        var aggregates = buildAggregates(
                analysis.executions().stream()
                        .filter(filters.predicate())
                        .toList()
        );

        var sorted = aggregates.stream()
                .sorted(AggregateComparators.forSort(sort)
                        .thenComparing(Aggregate::normalizedSql))
                .toList();

        var slice = paginate(sorted, page, size);

        return new QueryPage(
                slice.values(),
                sorted.size(),
                slice.page(),
                slice.size()
        );
    }

    public Aggregate detail(UUID analysisId, UUID queryId) {
        return buildAggregates(getAnalysis(analysisId).executions()).stream()
                .filter(aggregate -> aggregate.id().equals(queryId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Query non trovata: " + queryId));
    }

    public ExecutionPage executions(
            UUID analysisId,
            UUID queryId,
            int page,
            int size
    ) {
        var analysis = getAnalysis(analysisId);

        var executions = analysis.group(queryId).stream()
                .sorted(Comparator.comparing(PostgresQuery::timestamp))
                .toList();

        var slice = paginate(executions, page, size);

        return new ExecutionPage(
                slice.values(),
                executions.size(),
                slice.page(),
                slice.size()
        );
    }

    public List<ParameterOccurrence> parameterOccurrences(
            UUID analysisId,
            UUID queryId
    ) {
        return getAnalysis(analysisId)
                .group(queryId)
                .stream()
                .collect(Collectors.groupingBy(
                        PostgresQuery::resolvedSql,
                        Collectors.counting()
                ))
                .entrySet()
                .stream()
                .map(entry -> new ParameterOccurrence(
                        entry.getKey(),
                        entry.getValue()
                ))
                .sorted(
                        Comparator.comparingLong(ParameterOccurrence::executionCount)
                                .reversed()
                                .thenComparing(ParameterOccurrence::resolvedSql)
                )
                .toList();
    }

    public List<Bucket> timeline(
            UUID analysisId,
            Filters filters,
            @Nullable UUID queryId,
            int bucketSeconds
    ) {
        var analysis = getAnalysis(analysisId);

        int bucketSize = Math.clamp(
                bucketSeconds,
                1,
                MAX_BUCKET_SECONDS
        );

        return analysis.executions().stream()
                .filter(filters.predicate())
                .filter(query -> queryId == null || QueryIdentity.idFor(query.sql()).equals(queryId))
                .collect(Collectors.groupingBy(
                        query -> bucket(query.timestamp(), bucketSize),
                        TreeMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    var statistics = Statistics.from(entry.getValue());

                    return new Bucket(
                            entry.getKey(),
                            entry.getValue().size(),
                            statistics.total(),
                            statistics.average()
                    );
                })
                .toList();
    }

    private Summary buildSummary(List<PostgresQuery> executions) {
        if (executions.isEmpty()) {
            return new Summary(0, 0, null, null, 0, 0, 0, 0, null, null, null, List.of());
        }

        var firstExecution = minTimestamp(executions);
        var lastExecution = maxTimestamp(executions);
        var statistics = Statistics.from(executions);

        assert firstExecution != null;
        double elapsedSeconds = Math.max(Duration.between(firstExecution, lastExecution).toMillis() / 1000.0, 0.001);

        return new Summary(
                executions.size(),
                executions.stream()
                        .map(PostgresQuery::sql)
                        .map(SqlNormalizer::normalize)
                        .distinct()
                        .count(),
                firstExecution,
                lastExecution,
                executions.size() / elapsedSeconds,
                distinctCount(executions, PostgresQuery::username),
                distinctCount(executions, PostgresQuery::database),
                distinctCount(executions, PostgresQuery::client),
                statistics.total(),
                statistics.average(),
                statistics.max(),
                buildBreakdown(executions)
        );
    }

    private List<Breakdown> buildBreakdown(List<PostgresQuery> executions) {
        return executions.stream()
                .collect(Collectors.groupingBy(
                        query -> SqlCategory.classify(query.sql()),
                        TreeMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .map(entry -> {
                    var values = entry.getValue();

                    return new Breakdown(
                            entry.getKey().name(),
                            values.size(),
                            values.size() * 100.0 / executions.size(),
                            Statistics.from(values).total()
                    );
                })
                .toList();
    }

    private List<Aggregate> buildAggregates(List<PostgresQuery> executions) {
        return executions.stream()
                .collect(Collectors.groupingBy(
                        query -> SqlNormalizer.normalize(query.sql()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .values()
                .stream()
                .map(this::buildAggregate)
                .toList();
    }

    private Aggregate buildAggregate(List<PostgresQuery> executions) {
        var first = executions.getFirst();
        var normalizedSql = SqlNormalizer.normalize(first.sql());
        var statistics = Statistics.from(executions);

        return new Aggregate(
                QueryIdentity.idForNormalized(normalizedSql),
                normalizedSql,
                SqlFormatter.format(normalizedSql),
                first.sql().length(),
                executions.size(),
                statistics.total(),
                statistics.average(),
                statistics.min(),
                statistics.max(),
                statistics.p50(),
                statistics.p95(),
                statistics.p99(),
                frequencies(executions, PostgresQuery::username),
                frequencies(executions, PostgresQuery::database),
                distinctCount(executions, PostgresQuery::client),
                minTimestamp(executions),
                maxTimestamp(executions),
                first.sql(),
                executions.stream()
                        .map(PostgresQuery::preparedStatementName)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList(),
                SqlCategory.classify(first.sql()).name()
        );
    }

    private List<DashboardQuery> topQueries(
            List<Aggregate> aggregates,
            Comparator<Aggregate> comparator
    ) {
        return aggregates.stream()
                .sorted(comparator.thenComparing(Aggregate::normalizedSql))
                .limit(DASHBOARD_TOP_LIMIT)
                .map(query -> new DashboardQuery(
                        query.id(),
                        query.normalizedSql(),
                        query.executionCount(),
                        query.totalDurationMillis(),
                        query.averageDurationMillis(),
                        query.maxDurationMillis()
                ))
                .toList();
    }

    private Analysis getAnalysis(UUID id) {
        var analysis = analyses.get(id);

        if (analysis == null) {
            throw new IllegalArgumentException("Analisi non trovata: " + id);
        }

        return analysis;
    }

    private record PageSlice<T>(
            List<T> values,
            int page,
            int size
    ) {
    }

    record Analysis(
            UUID id,
            List<PostgresQuery> executions,
            Map<UUID, List<PostgresQuery>> groups,
            long unrecognizedRowCount
    ) {

        static Analysis create(List<PostgresQuery> executions, long unrecognizedRowCount) {

            Objects.requireNonNull(executions, "executions");
            List<PostgresQuery> immutableExecutions = List.copyOf(executions);

            Map<UUID, List<PostgresQuery>> mutableGroups = immutableExecutions.stream()
                    .collect(Collectors.groupingBy(
                            query -> QueryIdentity.idFor(query.sql()),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));

            Map<UUID, List<PostgresQuery>> immutableGroups = mutableGroups.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            Map.Entry::getKey,
                            entry -> List.copyOf(entry.getValue())
                    ));

            return new Analysis(UUID.randomUUID(), immutableExecutions, immutableGroups, unrecognizedRowCount);
        }

        List<PostgresQuery> group(UUID queryId) {

            Objects.requireNonNull(queryId, "queryId");
            return groups.getOrDefault(queryId, List.of());
        }
    }

    public record Summary(
            long executionCount,
            long uniqueQueryCount,
            @Nullable OffsetDateTime firstExecution,
            @Nullable OffsetDateTime lastExecution,
            double queriesPerSecond,
            long distinctUsers,
            long distinctDatabases,
            long distinctClients,
            @Nullable Double totalDurationMillis,
            @Nullable Double averageDurationMillis,
            @Nullable Double maxDurationMillis,
            List<Breakdown> breakdown
    ) {
    }

    public record Aggregate(
            UUID id,
            String normalizedSql,
            String formattedSql,
            int queryLength,
            long executionCount,
            @Nullable Double totalDurationMillis,
            @Nullable Double averageDurationMillis,
            @Nullable Double minDurationMillis,
            @Nullable Double maxDurationMillis,
            @Nullable Double p50DurationMillis,
            @Nullable Double p95DurationMillis,
            @Nullable Double p99DurationMillis,
            Map<String, Long> users,
            Map<String, Long> databases,
            long distinctClients,
            @Nullable OffsetDateTime firstExecution,
            @Nullable OffsetDateTime lastExecution,
            String originalSql,
            List<String> preparedStatementNames,
            String category
    ) {
    }

    public record Dashboard(
            List<DashboardQuery> topByTotalTime,
            List<DashboardQuery> topByAverageDuration,
            List<DashboardQuery> topByFrequency,
            long outlierCount,
            long unrecognizedRowCount,
            @Nullable OffsetDateTime firstExecution,
            @Nullable OffsetDateTime lastExecution
    ) {
    }

    public record Breakdown(
            String category,
            long executionCount,
            double percentage,
            @Nullable Double totalDurationMillis
    ) {
    }

    public record DashboardQuery(
            UUID id,
            String normalizedSql,
            long executionCount,
            @Nullable Double totalDurationMillis,
            @Nullable Double averageDurationMillis,
            @Nullable Double maxDurationMillis
    ) {
    }

    public record AnalysisResponse(
            UUID analysisId,
            Summary summary,
            List<?> issues
    ) {
    }

    public record QueryPage(
            List<Aggregate> queries,
            long total,
            int page,
            int size
    ) {
    }

    public record ExecutionPage(
            List<PostgresQuery> executions,
            long total,
            int page,
            int size
    ) {
    }

    public record ParameterOccurrence(
            String resolvedSql,
            long executionCount
    ) {
    }

    public record Bucket(
            OffsetDateTime timestamp,
            long executionCount,
            @Nullable Double totalDurationMillis,
            @Nullable Double averageDurationMillis
    ) {
    }
}
