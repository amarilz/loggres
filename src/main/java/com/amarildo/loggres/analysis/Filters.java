package com.amarildo.loggres.analysis;

import com.amarildo.loggres.parser.PostgresQuery;
import com.amarildo.loggres.sql.SqlCategory;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

@NullMarked
public record Filters(
        String database,
        String username,
        String client,
        String type,
        String text,
        @Nullable Double minDuration,
        @Nullable Double maxDuration,
        @Nullable OffsetDateTime from,
        @Nullable OffsetDateTime to
) {

    private static boolean matches(String filter, String value) {
        return isBlank(filter) || Objects.equals(filter, value);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    public Predicate<PostgresQuery> predicate() {
        return query -> matches(database, query.database())
                && matches(username, query.username())
                && matches(client, query.client())
                && matchesType(query)
                && matchesText(query)
                && matchesMinDuration(query)
                && matchesMaxDuration(query)
                && matchesFrom(query)
                && matchesTo(query);
    }

    private boolean matchesType(PostgresQuery query) {
        if (isBlank(type)) {
            return true;
        }
        return SqlCategory.classify(query.sql())
                .name()
                .equalsIgnoreCase(type);
    }

    private boolean matchesText(PostgresQuery query) {
        if (isBlank(text)) {
            return true;
        }
        return query.sql()
                .toLowerCase(Locale.ROOT)
                .contains(text.toLowerCase(Locale.ROOT));
    }

    private boolean matchesMinDuration(PostgresQuery query) {
        if (minDuration == null) {
            return true;
        }
        Double duration = query.durationMillis();
        return duration != null && duration >= minDuration;
    }

    private boolean matchesMaxDuration(PostgresQuery query) {
        if (maxDuration == null) {
            return true;
        }
        Double duration = query.durationMillis();
        return duration != null && duration <= maxDuration;
    }

    private boolean matchesFrom(PostgresQuery query) {
        return from == null || !query.timestamp().isBefore(from);
    }

    private boolean matchesTo(PostgresQuery query) {
        return to == null || !query.timestamp().isAfter(to);
    }
}
