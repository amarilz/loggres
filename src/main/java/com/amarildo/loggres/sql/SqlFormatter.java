package com.amarildo.loggres.sql;

import com.manticore.jsqlformatter.JSQLFormatter;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

import java.util.Objects;
import java.util.regex.Pattern;

@UtilityClass
@NullMarked
public final class SqlFormatter {

    private static final int MAX_SQL_LENGTH = 100_000;

    private static final Pattern FORMATTABLE_STATEMENT = Pattern.compile(
            """
                    ^(?:SELECT|WITH|INSERT|UPDATE|DELETE|MERGE|
                       CREATE|ALTER|DROP|TRUNCATE)\\b
                    """,
            Pattern.CASE_INSENSITIVE
                    | Pattern.COMMENTS
    );

    public static String format(String sql) {

        Objects.requireNonNull(sql, "sql");
        if (!isFormattable(sql)) {
            return sql;
        }
        try {
            return JSQLFormatter.format(sql);
        } catch (Exception | StackOverflowError exception) {
            return sql;
        }
    }

    private static boolean isFormattable(String sql) {

        return sql.length() <= MAX_SQL_LENGTH
                && FORMATTABLE_STATEMENT
                .matcher(sql.stripLeading())
                .find();
    }
}
