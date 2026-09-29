package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Component
@NullMarked
public final class PostgresLogEntryReader {

    private static final Pattern ENTRY_START = Pattern.compile(
            "^\\d{4}-\\d{2}-\\d{2} " +
                    "\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\s+"
    );

    private static boolean isEntryStart(String line) {

        return ENTRY_START.matcher(line).find();
    }

    private static void emit(StringBuilder buffer, int lineNumber, Consumer<LogEntry> consumer) {

        if (buffer.isEmpty()) {
            return;
        }
        LogEntry logEntry = new LogEntry(lineNumber, buffer.toString());
        consumer.accept(logEntry);
    }

    public void read(InputStream input, Consumer<LogEntry> consumer) throws IOException {

        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(consumer, "consumer");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            read(reader, consumer);
        }
    }

    private void read(BufferedReader reader, Consumer<LogEntry> consumer) throws IOException {

        StringBuilder currentEntry = new StringBuilder();

        int lineNumber = 0;
        int entryStartLine = 0;

        String line;
        while ((line = reader.readLine()) != null) {
            lineNumber++;

            if (isEntryStart(line)) {
                emit(currentEntry, entryStartLine, consumer);

                currentEntry.setLength(0);
                currentEntry.append(line);
                entryStartLine = lineNumber;
                continue;
            }
            if (!currentEntry.isEmpty()) {
                currentEntry.append('\n').append(line);
            }
        }

        emit(currentEntry, entryStartLine, consumer);
    }
}
