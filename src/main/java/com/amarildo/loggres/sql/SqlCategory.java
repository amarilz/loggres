package com.amarildo.loggres.sql;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

@NullMarked
public enum SqlCategory {
    SELECT,
    INSERT,
    UPDATE,
    DELETE,
    BEGIN,
    COMMIT,
    ROLLBACK,
    DDL,
    OTHER;

    public static SqlCategory classify(@Nullable String sql) {
        if (sql == null || sql.isBlank()) {
            return OTHER;
        }

        String keyword = firstKeyword(sql);

        return switch (keyword) {
            case "SELECT" -> SELECT;
            case "INSERT" -> INSERT;
            case "UPDATE" -> UPDATE;
            case "DELETE" -> DELETE;

            case "BEGIN", "START" -> BEGIN;

            case "COMMIT" -> COMMIT;
            case "ROLLBACK" -> ROLLBACK;

            case "CREATE",
                 "ALTER",
                 "DROP",
                 "TRUNCATE",
                 "COMMENT",
                 "GRANT",
                 "REVOKE" -> DDL;

            default -> OTHER;
        };
    }

    private static String firstKeyword(String sql) {
        String stripped = sql.stripLeading();

        int separator = findFirstWhitespace(stripped);

        String keyword = separator == -1
                ? stripped
                : stripped.substring(0, separator);

        return keyword.toUpperCase(Locale.ROOT);
    }

    private static int findFirstWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
