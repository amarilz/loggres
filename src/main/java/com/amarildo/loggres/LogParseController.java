package com.amarildo.loggres;

import com.amarildo.loggres.analysis.Filters;
import com.amarildo.loggres.analysis.WorkloadAnalysisService;
import com.amarildo.loggres.parser.LogParseResult;
import com.amarildo.loggres.parser.LogParseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class LogParseController {

    private final LogParseService logParseService;
    private final WorkloadAnalysisService analysisService;

    @Autowired
    public LogParseController(LogParseService logParseService, WorkloadAnalysisService analysisService) {

        this.logParseService = logParseService;
        this.analysisService = analysisService;
    }

    @PostMapping(value = "/logs/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public WorkloadAnalysisService.AnalysisResponse parse(@RequestParam("files") List<MultipartFile> files) {

        LogParseResult result = logParseService.parse(files);
        return analysisService.create(result.queries(), result.issues());
    }

    @GetMapping("/analysis/{analysisId}/summary")
    public WorkloadAnalysisService.Summary summary(@PathVariable UUID analysisId, @ModelAttribute Filters filters) {

        return analysisService.summary(analysisId, filters);
    }

    @GetMapping("/analysis/{analysisId}/dashboard")
    public WorkloadAnalysisService.Dashboard dashboard(@PathVariable UUID analysisId) {

        return analysisService.dashboard(analysisId);
    }

    @GetMapping("/analysis/{analysisId}/queries")
    public WorkloadAnalysisService.QueryPage queries(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "frequency") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @ModelAttribute Filters filters
    ) {

        return analysisService.queries(analysisId, filters, sort, page, size);
    }

    @GetMapping("/analysis/{analysisId}/queries/{queryId}")
    public WorkloadAnalysisService.Aggregate query(@PathVariable UUID analysisId, @PathVariable UUID queryId) {

        return analysisService.detail(analysisId, queryId);
    }

    @GetMapping("/analysis/{analysisId}/queries/{queryId}/executions")
    public WorkloadAnalysisService.ExecutionPage executions(
            @PathVariable UUID analysisId,
            @PathVariable UUID queryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {

        return analysisService.executions(analysisId, queryId, page, size);
    }

    @GetMapping("/analysis/{analysisId}/queries/{queryId}/parameter-occurrences")
    public List<WorkloadAnalysisService.ParameterOccurrence>
    parameterOccurrences(@PathVariable UUID analysisId, @PathVariable UUID queryId) {

        return analysisService.parameterOccurrences(analysisId, queryId);
    }

    @GetMapping("/analysis/{analysisId}/timeline")
    public List<WorkloadAnalysisService.Bucket> timeline(
            @PathVariable UUID analysisId,
            @RequestParam(defaultValue = "60") int bucketSeconds,
            @RequestParam(required = false) UUID queryId,
            @ModelAttribute Filters filters
    ) {

        return analysisService.timeline(analysisId, filters, queryId, bucketSeconds);
    }
}
