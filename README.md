# Loggres

Loggres is a small web application for analyzing PostgreSQL query logs. It groups
similar queries, calculates execution-time statistics, highlights frequently run
or slow queries, and shows the parameter values used by prepared statements.

## Supported log format

For now, Loggres expects PostgreSQL log entries with this prefix:

```text
YYYY-MM-DD HH:mm:ss.SSS TIMEZONE [PID] USER@DATABASE CLIENT LEVEL: MESSAGE
```

The corresponding PostgreSQL setting is:

```conf
log_line_prefix = '%m [%p] %u@%d %r '
```

Supported levels are `LOG`, `DETAIL`, `ERROR`, `WARNING`, `NOTICE`, and `FATAL`.
Query analysis currently recognizes `LOG` entries containing:

```text
statement: SQL
duration: DURATION ms statement: SQL
execute PREPARED_STATEMENT: SQL
duration: DURATION ms execute PREPARED_STATEMENT: SQL
```

Prepared-statement parameters can follow in a `DETAIL` entry with the same PID:

```text
DETAIL: parameters: $1 = 'value', $2 = 42
```

Example:

```text
2026-09-14 17:06:23.315 CEST [1915315] app@mydb 127.0.0.1(42046) LOG: duration: 2.5 ms execute S_11: SELECT * FROM users WHERE id = $1
2026-09-14 17:06:23.315 CEST [1915315] app@mydb 127.0.0.1(42046) DETAIL: parameters: $1 = '42'
```

Multiline SQL statements are supported. Entries that do not match the expected
prefix are reported as unrecognized; non-query messages are otherwise ignored.
