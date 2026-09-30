package com.amarildo.loggres.parser;

import com.amarildo.loggres.sql.SqlNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlNormalizerTest {

    @Test
    void replacesNumbersStringsAndDatesWithoutChangingSqlShape() {
        assertEquals(
                "SELECT * FROM users WHERE id = ? AND name = ? AND created_at > ?",
                SqlNormalizer.normalize(
                        "SELECT * FROM users WHERE id = 42 AND name = 'Ada''s' AND created_at > '2026-09-14'"));
    }

    @Test
    void keepsPreparedPlaceholdersStable() {
        assertEquals("SELECT * FROM users WHERE id = $1", SqlNormalizer.normalize("SELECT * FROM users WHERE id = $1"));
    }

    @Test
    void normalizesVeryLongQueryWithoutRegexStackOverflow() {
        String sql = "SELECT * FROM permissions WHERE "
                + "permissions.name = 'admin' OR ".repeat(10_000)
                + "permissions.name = 'report'";

        assertEquals(sql.replaceAll("'[^']*'", "?").replaceAll("\\s+", " "),
                SqlNormalizer.normalize(sql));
    }
}
