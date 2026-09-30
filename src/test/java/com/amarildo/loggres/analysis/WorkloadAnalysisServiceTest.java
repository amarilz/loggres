package com.amarildo.loggres.analysis;

import com.amarildo.loggres.parser.PostgresQuery;
import com.amarildo.loggres.parser.QueryType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkloadAnalysisServiceTest {

    @Test
    void aggregatesLogicalQueriesAndCalculatesDurationStatistics() {
        OffsetDateTime start = OffsetDateTime.parse("2026-09-14T17:06:23.315+02:00");
        List<PostgresQuery> executions = List.of(
                query(start, 10.0, "1"),
                query(start.plusSeconds(1), 20.0, "2"),
                query(start.plusSeconds(2), 30.0, "3"));

        WorkloadAnalysisService service = new WorkloadAnalysisService();
        var response = service.create(executions, List.of());
        var page = service.queries(response.analysisId(),
                new Filters(null, null, null, null, null, null, null, null, null),
                "frequency",
                0, 25);
        var aggregate = page.queries().getFirst();

        assertEquals(3, response.summary().executionCount());
        assertEquals(1, response.summary().uniqueQueryCount());
        assertEquals(3, aggregate.executionCount());
        assertEquals("SELECT * FROM users WHERE id = $1".length(), aggregate.queryLength());
        assertEquals(60.0, aggregate.totalDurationMillis());
        assertEquals(20.0, aggregate.averageDurationMillis());
        assertEquals(10.0, aggregate.minDurationMillis());
        assertEquals(30.0, aggregate.maxDurationMillis());
    }

    @Test
    void sortsAggregatesByQueryLengthDescending() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-14T17:06:23.315+02:00");
        List<PostgresQuery> executions = List.of(
                queryWithSql(timestamp, "SELECT 1", 10.0),
                queryWithSql(timestamp.plusSeconds(1), "SELECT * FROM users WHERE id = 1", 20.0));

        WorkloadAnalysisService service = new WorkloadAnalysisService();
        var response = service.create(executions, List.of());
        var page = service.queries(response.analysisId(),
                new Filters(null, null, null, null, null, null, null, null, null),
                "length", 0, 25);

        assertEquals("SELECT * FROM users WHERE id = 1".length(), page.queries().getFirst().queryLength());
    }

    @Test
    void countsExactResolvedParameterCombinationsByFrequency() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-14T17:06:23.315+02:00");
        List<PostgresQuery> executions = List.of(
                query(timestamp, 10.0, "5541010"),
                query(timestamp.plusSeconds(1), 11.0, "5541010"),
                query(timestamp.plusSeconds(2), 12.0, "42"));

        WorkloadAnalysisService service = new WorkloadAnalysisService();
        var response = service.create(executions, List.of());
        var queryId = service.queries(response.analysisId(),
                new Filters(null, null, null, null, null, null, null, null, null),
                "frequency", 0, 25).queries().getFirst().id();

        var occurrences = service.parameterOccurrences(response.analysisId(), queryId);

        assertEquals(2, occurrences.size());
        assertEquals(2, occurrences.getFirst().executionCount());
        assertEquals("SELECT * FROM users WHERE id = 5541010", occurrences.getFirst().resolvedSql());
        assertEquals(1, occurrences.getLast().executionCount());
    }

    @Test
    void buildsDashboardRankingsAndCountsOutliersAndIssues() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-09-14T17:06:23.315+02:00");
        List<PostgresQuery> executions = List.of(
                queryWithSql(timestamp, "SELECT frequent", 10.0),
                queryWithSql(timestamp.plusSeconds(1), "SELECT frequent", 10.0),
                queryWithSql(timestamp.plusSeconds(2), "SELECT slow", 20.0),
                queryWithSql(timestamp.plusSeconds(3), "SELECT normal", 20.0),
                queryWithSql(timestamp.plusSeconds(4), "SELECT normal", 20.0),
                queryWithSql(timestamp.plusSeconds(5), "SELECT frequent", 10.0),
                queryWithSql(timestamp.plusSeconds(6), "SELECT outlier", 100.0));

        WorkloadAnalysisService service = new WorkloadAnalysisService();
        var response = service.create(executions, List.of("unrecognized", "unrecognized"));
        var dashboard = service.dashboard(response.analysisId());

        assertEquals(2, dashboard.unrecognizedRowCount());
        assertEquals(1, dashboard.outlierCount());
        assertEquals("SELECT frequent", dashboard.topByFrequency().getFirst().normalizedSql());
        assertEquals("SELECT outlier", dashboard.topByTotalTime().getFirst().normalizedSql());
        assertEquals("SELECT outlier", dashboard.topByAverageDuration().getFirst().normalizedSql());
        assertEquals(timestamp, dashboard.firstExecution());
        assertEquals(timestamp.plusSeconds(6), dashboard.lastExecution());
    }

    private PostgresQuery query(OffsetDateTime timestamp, double duration, String id) {
        return queryWithSql(timestamp, "SELECT * FROM users WHERE id = $1", duration,
                Map.of(1, id), "SELECT * FROM users WHERE id = " + id);
    }

    private PostgresQuery queryWithSql(OffsetDateTime timestamp, String sql, double duration) {
        return queryWithSql(timestamp, sql, duration, Map.of(), sql);
    }

    private PostgresQuery queryWithSql(OffsetDateTime timestamp, String sql, double duration,
                                       Map<Integer, String> parameters, String resolvedSql) {
        return new PostgresQuery(timestamp, 1, "app", "db", "127.0.0.1", QueryType.EXECUTE,
                "S_1", sql, parameters, resolvedSql, duration);
    }
}
