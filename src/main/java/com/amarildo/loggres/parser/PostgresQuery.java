package com.amarildo.loggres.parser;

import java.time.OffsetDateTime;
import java.util.Map;

public record PostgresQuery(
        OffsetDateTime timestamp,
        long pid,
        String username,
        String database,
        String client,
        QueryType type,
        String preparedStatementName,
        String sql,
        Map<Integer, String> parameters,
        String resolvedSql,
        Double durationMillis
) {
}
