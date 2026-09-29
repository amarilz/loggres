package com.amarildo.loggres.parser;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresLogParserTest {

    @Test
    void associatesParametersAndReadsDuration() throws IOException {
        var result = parse(
                "2026-09-14 17:06:23.315 CEST [1915315] botdbuser@botdb 172.20.192.96(42046) LOG:  duration: 2.5 ms execute S_11: SELECT * FROM users WHERE id = $1",
                "2026-09-14 17:06:23.315 CEST [1915315] botdbuser@botdb 172.20.192.96(42046) DETAIL:  parameters: $1 = '42'");

        var query = result.queries().getFirst();
        assertEquals("SELECT * FROM users WHERE id = $1", query.sql());
        assertEquals("SELECT * FROM users WHERE id = '42'", query.resolvedSql());
        assertEquals(2.5, query.durationMillis());
        assertNotNull(query.timestamp());
    }

    @Test
    void parsesVeryLongStatementWithoutRegexStackOverflow() throws IOException {
        String sql = "SELECT '" + "x".repeat(10_000_000) + "'";
        var result = parse(
                "2026-09-14 17:06:23.315 CEST [1915315] botdbuser@botdb 172.20.192.96(42046) LOG:  statement: " + sql);

        assertTrue(result.queries().getFirst().sql().length() > 10_000_000);
    }

    private LogParseResult parse(String... entries) throws IOException {
        byte[] log = String.join("\n", entries).getBytes(StandardCharsets.UTF_8);
        return new PostgresLogPipeline().parse(new ByteArrayInputStream(log), "test.log");
    }
}
