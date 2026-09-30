package com.amarildo.loggres.parser;

public record LogEntry(
        int lineNumber,
        String content
) {
}
