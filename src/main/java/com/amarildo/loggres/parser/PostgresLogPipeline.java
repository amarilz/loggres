package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@NullMarked
public final class PostgresLogPipeline {

    private final PostgresLogEntryReader reader = new PostgresLogEntryReader();
    private final PostgresLogEntryParser entryParser = new PostgresLogEntryParser();

    public LogParseResult parse(InputStream input, String filename) throws IOException {

        List<PostgresQuery> queries = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();
        PostgresQueryAssembler assembler = new PostgresQueryAssembler();
        reader.read(
                input,
                rawEntry -> processEntry(rawEntry, filename, assembler, queries, issues)
        );
        queries.addAll(assembler.finish());
        return new LogParseResult(queries, issues);
    }

    private void processEntry(LogEntry rawEntry, String filename, PostgresQueryAssembler assembler,
                              List<PostgresQuery> queries, List<ParseIssue> issues) {

        Optional<ParsedLogEntry> parsed = entryParser.parse(rawEntry);
        if (parsed.isEmpty()) {
            ParseIssue unrecognizedLogFormat = new ParseIssue(filename, rawEntry.lineNumber(), "Unrecognized log format");
            issues.add(unrecognizedLogFormat);
            return;
        }
        queries.addAll(assembler.accept(parsed.get()));
    }
}
