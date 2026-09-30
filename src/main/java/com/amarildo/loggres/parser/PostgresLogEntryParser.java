package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@NullMarked
public final class PostgresLogEntryParser {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS z", Locale.ENGLISH);

    private static final Pattern LOG_HEADER_PATTERN = Pattern.compile(
            """
                    ^(?<timestamp>\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+
                    (?<timezone>\\S+)\\s+
                    \\[(?<pid>\\d+)]\\s+
                    (?<user>[^@\\s]+)@(?<database>\\S+)\\s+
                    (?<client>\\S+)\\s+
                    (?<level>LOG|DETAIL|ERROR|WARNING|NOTICE|FATAL):
                    """,
            Pattern.COMMENTS
    );

    private static String extractMessage(String rawEntry, int headerEnd) {

        int messageStart = skipWhitespace(rawEntry, headerEnd);
        return rawEntry.substring(messageStart);
    }

    private static OffsetDateTime parseTimestamp(String timestamp, String timezone) {

        return ZonedDateTime.parse(timestamp + " " + timezone, TIMESTAMP_FORMATTER).toOffsetDateTime();
    }

    private static int skipWhitespace(String value, int start) {

        int index = start;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    public Optional<ParsedLogEntry> parse(LogEntry entry) {

        Objects.requireNonNull(entry, "entry");
        Matcher matcher = LOG_HEADER_PATTERN.matcher(entry.content());
        if (!matcher.find()) {
            return Optional.empty();
        }

        ParsedLogEntry parsedLogEntry = new ParsedLogEntry(
                entry.lineNumber(),
                parseTimestamp(matcher.group("timestamp"), matcher.group("timezone")),
                Long.parseLong(matcher.group("pid")),
                matcher.group("user"),
                matcher.group("database"),
                matcher.group("client"),
                LogLevel.valueOf(matcher.group("level")),
                extractMessage(entry.content(), matcher.end()));
        return Optional.of(parsedLogEntry);
    }
}
