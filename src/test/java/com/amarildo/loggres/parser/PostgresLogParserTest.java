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
    void associatesStandaloneDurationWithPreviousStatement() throws IOException {
        var result = parse(
                "2026-10-14 12:30:09.463 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  statement: SET application_name = 'PostgreSQL JDBC Driver'",
                "2026-10-14 12:30:09.463 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.153 ms");

        assertEquals(1, result.queries().size());
        assertEquals("SET application_name = 'PostgreSQL JDBC Driver'", result.queries().getFirst().sql());
        assertEquals(0.153, result.queries().getFirst().durationMillis());
    }

    @Test
    void emptyStatementDoesNotReplacePreviousQueryDuration() throws IOException {
        var result = parse(
                "2026-10-14 12:30:09.463 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  statement: SET application_name = 'PostgreSQL JDBC Driver'",
                "2026-10-14 12:30:09.463 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.153 ms",
                "2026-10-14 12:30:09.494 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  statement: ",
                "2026-10-14 12:30:09.494 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.055 ms");

        assertEquals(1, result.queries().size());
        assertEquals("SET application_name = 'PostgreSQL JDBC Driver'", result.queries().getFirst().sql());
        assertEquals(0.153, result.queries().getFirst().durationMillis());
    }

    @Test
    void associatesStandaloneDurationWithExtendedProtocolExecute() throws IOException {
        var result = parse(
                "2026-10-14 12:30:09.494 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.190 ms  parse <unnamed>: SHOW TRANSACTION ISOLATION LEVEL",
                "2026-10-14 12:30:09.494 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.009 ms  bind <unnamed>: SHOW TRANSACTION ISOLATION LEVEL",
                "2026-10-14 12:30:09.494 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  execute <unnamed>: SHOW TRANSACTION ISOLATION LEVEL",
                "2026-10-14 12:30:09.494 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.030 ms");

        assertEquals(1, result.queries().size());
        assertEquals("SHOW TRANSACTION ISOLATION LEVEL", result.queries().getFirst().sql());
        assertEquals(0.030, result.queries().getFirst().durationMillis());
    }

    @Test
    void ignoresStandaloneDurationWithoutQuery() throws IOException {
        var result = parse(
                "2026-10-14 12:30:09.463 UTC [64] botdbuser@botdb 127.0.0.1(58308) LOG:  duration: 0.153 ms");

        assertTrue(result.queries().isEmpty());
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
