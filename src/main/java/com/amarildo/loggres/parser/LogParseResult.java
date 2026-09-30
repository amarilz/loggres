package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;

import java.util.List;

@NullMarked
public record LogParseResult(
        List<PostgresQuery> queries,
        List<ParseIssue> issues
) {

    public LogParseResult {
        queries = List.copyOf(queries);
        issues = List.copyOf(issues);
    }
}
