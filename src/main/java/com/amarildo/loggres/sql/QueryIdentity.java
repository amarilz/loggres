package com.amarildo.loggres.sql;

import lombok.experimental.UtilityClass;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@UtilityClass
public final class QueryIdentity {

    public static UUID idFor(String sql) {
        return idForNormalized(SqlNormalizer.normalize(sql));
    }

    public static UUID idForNormalized(String normalizedSql) {
        return UUID.nameUUIDFromBytes(normalizedSql.getBytes(StandardCharsets.UTF_8));
    }
}
