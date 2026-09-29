package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@NullMarked
public final class QueryLogMessageParser {

    private static final String DURATION_PREFIX = "duration:";
    private static final String STATEMENT_PREFIX = "statement:";
    private static final String EXECUTE_PREFIX = "execute ";

    private static boolean isDurationCharacter(char value) {

        return Character.isDigit(value) || value == '.';
    }

    private static int skipWhitespace(String value, int start) {

        int index = start;
        while (index < value.length()
                && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    public Optional<QueryLogMessage> parse(String message) {

        Optional<DurationMessage> durationMessage = parseDuration(message);
        if (durationMessage.isEmpty()) {
            return Optional.empty();
        }

        DurationMessage parsed = durationMessage.get();
        if (parsed.message().startsWith(STATEMENT_PREFIX)) {
            return parseStatement(parsed);
        }
        if (parsed.message().startsWith(EXECUTE_PREFIX)) {
            return parseExecute(parsed);
        }

        return Optional.empty();
    }

    private Optional<QueryLogMessage> parseStatement(DurationMessage message) {

        int sqlStart = skipWhitespace(message.message(), STATEMENT_PREFIX.length());
        QueryLogMessage queryLogMessage = new QueryLogMessage(
                QueryType.STATEMENT,
                null,
                message.message().substring(sqlStart),
                message.durationMillis());
        return Optional.of(queryLogMessage);
    }

    private Optional<QueryLogMessage> parseExecute(DurationMessage message) {

        String value = message.message();
        int nameStart = EXECUTE_PREFIX.length();
        int nameEnd = value.indexOf(':', nameStart);

        if (nameEnd < 0) {
            return Optional.empty();
        }

        String preparedStatementName = value.substring(nameStart, nameEnd);
        int sqlStart = skipWhitespace(value, nameEnd + 1);

        QueryLogMessage queryLogMessage = new QueryLogMessage(
                QueryType.EXECUTE,
                preparedStatementName,
                value.substring(sqlStart),
                message.durationMillis());
        return Optional.of(queryLogMessage);
    }

    private Optional<DurationMessage> parseDuration(String message) {

        if (!message.regionMatches(true, 0, DURATION_PREFIX, 0, DURATION_PREFIX.length())) {
            DurationMessage durationMessage = new DurationMessage(null, message);
            return Optional.of(durationMessage);
        }

        int start = skipWhitespace(message, DURATION_PREFIX.length());
        int end = start;
        while (end < message.length() && isDurationCharacter(message.charAt(end))) {
            end++;
        }

        if (end == start) {
            return Optional.empty();
        }

        int unitStart = skipWhitespace(message, end);

        if (!message.regionMatches(true, unitStart, "ms", 0, 2)) {
            return Optional.empty();
        }

        double duration;

        try {
            duration = Double.parseDouble(message.substring(start, end));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }

        int queryStart = skipWhitespace(message, unitStart + 2);
        DurationMessage durationMessage = new DurationMessage(duration, message.substring(queryStart));
        return Optional.of(durationMessage);
    }

    private record DurationMessage(
            @Nullable Double durationMillis,
            String message
    ) {
    }
}
