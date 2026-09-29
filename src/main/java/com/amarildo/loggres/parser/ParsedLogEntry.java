package com.amarildo.loggres.parser;

import java.time.OffsetDateTime;

public record ParsedLogEntry(
        int lineNumber,
        OffsetDateTime timestamp,
        long pid,
        String username,
        String database,
        String client,
        LogLevel level,
        String message
) {
}
