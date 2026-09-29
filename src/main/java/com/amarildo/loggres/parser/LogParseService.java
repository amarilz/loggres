package com.amarildo.loggres.parser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LogParseService {

    private final PostgresLogPipeline pipeline;

    @Autowired
    public LogParseService(PostgresLogPipeline pipeline) {

        this.pipeline = pipeline;
    }

    private static String filename(MultipartFile file) {

        String filename = file.getOriginalFilename();
        return filename == null || filename.isBlank()
                ? "<unknown>"
                : filename;
    }

    private static String errorMessage(Exception exception) {

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getClass().getSimpleName();
        }
        return "Unable to process file: " + message;
    }

    public LogParseResult parse(List<MultipartFile> files) {

        List<PostgresQuery> queries = new ArrayList<>();
        List<ParseIssue> issues = new ArrayList<>();

        for (MultipartFile file : files) {
            parseFile(file, queries, issues);
        }
        queries.sort(Comparator.comparing(PostgresQuery::timestamp));
        return new LogParseResult(queries, issues);
    }

    private void parseFile(MultipartFile file, List<PostgresQuery> queries, List<ParseIssue> issues) {

        String filename = filename(file);
        if (file.isEmpty()) {
            ParseIssue emptyFile = new ParseIssue(filename, 0, "Empty file");
            issues.add(emptyFile);
            return;
        }

        try (InputStream input = file.getInputStream()) {
            LogParseResult result = pipeline.parse(input, filename);
            queries.addAll(result.queries());
            issues.addAll(result.issues());
        } catch (IOException | RuntimeException exception) {
            ParseIssue parseIssue = new ParseIssue(filename, 0, errorMessage(exception));
            issues.add(parseIssue);
        }
    }
}
