package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@NullMarked
public final class PostgresQueryAssembler {

    private static final String PARAMETERS_PREFIX = "parameters:";
    private final QueryLogMessageParser queryParser = new QueryLogMessageParser();
    private final Map<Long, PendingQuery> pending = new HashMap<>();

    public List<PostgresQuery> accept(ParsedLogEntry entry) {

        return switch (entry.level()) {
            case LOG -> acceptLog(entry);
            case DETAIL -> acceptDetail(entry);

            case ERROR,
                 WARNING,
                 NOTICE,
                 FATAL -> List.of();
        };
    }

    public List<PostgresQuery> finish() {

        List<PostgresQuery> result = pending.values().stream()
                .map(PendingQuery::build)
                .toList();
        pending.clear();
        return result;
    }

    private List<PostgresQuery> acceptLog(ParsedLogEntry entry) {

        Optional<QueryLogMessage> parsed = queryParser.parse(entry.message());

        if (parsed.isEmpty()) {
            return List.of();
        }

        List<PostgresQuery> completed = flush(entry.pid());
        PendingQuery from = PendingQuery.from(entry, parsed.get());
        pending.put(entry.pid(), from);

        return completed;
    }

    private List<PostgresQuery> acceptDetail(ParsedLogEntry entry) {

        if (!entry.message().startsWith(PARAMETERS_PREFIX)) {
            return List.of();
        }

        PendingQuery query = pending.remove(entry.pid());
        if (query == null) {
            return List.of();
        }

        Map<Integer, String> parse = PostgresParameterParser.parse(entry.message());
        query.addParameters(parse);
        return List.of(query.build());
    }

    private List<PostgresQuery> flush(long pid) {

        PendingQuery query = pending.remove(pid);
        if (query == null) {
            return List.of();
        }
        return List.of(query.build());
    }
}
