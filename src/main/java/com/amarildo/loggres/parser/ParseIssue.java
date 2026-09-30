package com.amarildo.loggres.parser;

import org.jspecify.annotations.NullMarked;

@NullMarked
public record ParseIssue(
        String file,
        int line,
        String message
) {
}
