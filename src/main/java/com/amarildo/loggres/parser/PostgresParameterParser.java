package com.amarildo.loggres.parser;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

import java.util.LinkedHashMap;
import java.util.Map;

@UtilityClass
@NullMarked
public final class PostgresParameterParser {

    private static final String PREFIX = "parameters:";

    public static Map<Integer, String> parse(String message) {

        if (!message.startsWith(PREFIX)) {
            return Map.of();
        }

        String input = message.substring(PREFIX.length()).trim();
        if (input.isEmpty()) {
            return Map.of();
        }

        Map<Integer, String> parameters = new LinkedHashMap<>();
        int index = 0;
        while (index < input.length()) {
            index = findNextParameter(input, index);

            if (index >= input.length()) {
                break;
            }

            int numberStart = ++index;
            while (index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (numberStart == index) {
                continue;
            }

            int parameterNumber = Integer.parseInt(input.substring(numberStart, index));
            index = skipWhitespace(input, index);
            if (index >= input.length() || input.charAt(index) != '=') {
                break;
            }

            index = skipWhitespace(input, index + 1);
            int valueStart = index;
            boolean insideQuote = false;

            while (index < input.length()) {
                char current = input.charAt(index);

                if (current == '\'') {
                    if (insideQuote && index + 1 < input.length() && input.charAt(index + 1) == '\'') {
                        index += 2;
                        continue;
                    }
                    insideQuote = !insideQuote;
                }

                if (!insideQuote && current == ',') {
                    break;
                }

                index++;
            }

            String value = input.substring(valueStart, index).trim();
            parameters.put(parameterNumber, value);
            if (index < input.length()) {
                index++;
            }
        }

        return Map.copyOf(parameters);
    }

    private static int findNextParameter(String input, int start) {

        int index = start;
        while (index < input.length()
                && input.charAt(index) != '$') {
            index++;
        }
        return index;
    }

    private static int skipWhitespace(String input, int start) {

        int index = start;
        while (index < input.length()
                && Character.isWhitespace(input.charAt(index))) {
            index++;
        }
        return index;
    }
}
