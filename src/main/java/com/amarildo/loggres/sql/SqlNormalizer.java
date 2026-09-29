package com.amarildo.loggres.sql;

import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NullMarked;

@UtilityClass
@NullMarked
public final class SqlNormalizer {

    private static final char PLACEHOLDER = '?';

    public static String normalize(String sql) {

        StringBuilder result = new StringBuilder(sql.length());
        boolean pendingSpace = false;
        int index = 0;

        while (index < sql.length()) {
            char current = sql.charAt(index);

            if (Character.isWhitespace(current)) {
                pendingSpace = !result.isEmpty();
                index++;
                continue;
            }

            if (current == '\'') {
                appendPendingSpace(result, pendingSpace);
                pendingSpace = false;
                result.append(PLACEHOLDER);
                index = skipStringLiteral(sql, index + 1);
                continue;
            }

            if (isNumberStart(sql, index)) {
                appendPendingSpace(result, pendingSpace);
                pendingSpace = false;
                result.append(PLACEHOLDER);
                index = skipNumber(sql, index);
                continue;
            }

            appendPendingSpace(result, pendingSpace);
            pendingSpace = false;

            result.append(current);
            index++;
        }

        return result.toString().trim();
    }

    private static int skipStringLiteral(String sql, int index) {

        while (index < sql.length()) {
            if (sql.charAt(index) != '\'') {
                index++;
                continue;
            }
            if (index + 1 < sql.length() && sql.charAt(index + 1) == '\'') {
                index += 2;
                continue;
            }
            return index + 1;
        }
        return index;
    }

    private static boolean isNumberStart(String sql, int index) {

        char current = sql.charAt(index);
        if ((current == '-' || current == '+') && index + 1 < sql.length()) {
            current = sql.charAt(index + 1);
        }
        if (!Character.isDigit(current)) {
            return false;
        }
        if (index > 0 && isWordCharacter(sql.charAt(index - 1))) {
            return false;
        }
        int end = skipNumber(sql, index);
        return end == sql.length() || !isWordCharacter(sql.charAt(end));
    }

    private static int skipNumber(String sql, int index) {

        if (sql.charAt(index) == '-' || sql.charAt(index) == '+') {
            index++;
        }
        while (index < sql.length() && Character.isDigit(sql.charAt(index))) {
            index++;
        }
        if (index < sql.length() && sql.charAt(index) == '.') {
            do {
                index++;
            } while (index < sql.length() && Character.isDigit(sql.charAt(index)));
        }
        return index;
    }

    private static boolean isWordCharacter(char value) {
        return Character.isLetterOrDigit(value)
                || value == '_'
                || value == '$';
    }

    private static void appendPendingSpace(StringBuilder result, boolean pendingSpace) {

        if (pendingSpace && !result.isEmpty()) {
            result.append(' ');
        }
    }
}
