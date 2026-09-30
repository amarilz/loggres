package com.amarildo.loggres.parser;

public record QueryLogMessage(
        QueryType type,
        String preparedStatementName,
        String sql,
        Double durationMillis
) {
}
