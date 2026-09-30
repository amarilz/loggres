package com.amarildo.loggres.parser;

import org.jspecify.annotations.Nullable;

record QueryData(
        QueryType type,
        String preparedStatementName,
        String sql,
        @Nullable Double durationMillis
) {
}
