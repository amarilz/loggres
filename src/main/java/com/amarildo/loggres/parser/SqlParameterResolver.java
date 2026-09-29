package com.amarildo.loggres.parser;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@UtilityClass
@NullMarked
public final class SqlParameterResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\d+)");

    public static String resolve(String sql, Map<Integer, String> parameters) {

        if (parameters.isEmpty()) {
            return sql;
        }

        Matcher matcher = PLACEHOLDER.matcher(sql);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            int parameterNumber = Integer.parseInt(matcher.group(1));
            String value = parameters.get(parameterNumber);
            if (value == null) {
                value = matcher.group();
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
