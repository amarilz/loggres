package com.amarildo.loggres.parser;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
final class PendingQuery {

    private final OffsetDateTime timestamp;
    private final long pid;
    private final String username;
    private final String database;
    private final String client;
    private final QueryType type;
    private final String preparedStatementName;
    private final String sql;
    private final Double durationMillis;
    private final Map<Integer, String> parameters = new LinkedHashMap<>();

    static PendingQuery from(ParsedLogEntry entry, QueryLogMessage message) {

        return new PendingQuery(
                entry.timestamp(),
                entry.pid(),
                entry.username(),
                entry.database(),
                entry.client(),
                message.type(),
                message.preparedStatementName(),
                message.sql(),
                message.durationMillis()
        );
    }

    void addParameters(Map<Integer, String> parameters) {

        this.parameters.putAll(parameters);
    }

    PostgresQuery build() {

        Map<Integer, String> immutableParameters = Map.copyOf(parameters);

        return new PostgresQuery(
                timestamp,
                pid,
                username,
                database,
                client,
                type,
                preparedStatementName,
                sql,
                immutableParameters,
                SqlParameterResolver.resolve(sql, immutableParameters),
                durationMillis
        );
    }
}
