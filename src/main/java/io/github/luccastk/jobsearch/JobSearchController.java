package io.github.luccastk.jobsearch;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    static final int MAX_KEYWORDS_LENGTH = 100;
    static final int MAX_RESULTS_LIMIT = 100;

    private final JobSearchService service;

    public JobSearchController(JobSearchService service) {
        this.service = service;
    }

    @GetMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse search(
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "ANY") PostedWithin postedWithin,
            @RequestParam(defaultValue = "false") boolean remote,
            @RequestParam(defaultValue = "25") int maxResults) {
        String trimmedKeywords = keywords == null ? "" : keywords.trim();
        if (trimmedKeywords.isEmpty()) {
            throw new InvalidParameterException("keywords is required");
        }
        if (trimmedKeywords.length() > MAX_KEYWORDS_LENGTH) {
            throw new InvalidParameterException("keywords must be at most " + MAX_KEYWORDS_LENGTH + " characters");
        }
        if (maxResults < 1 || maxResults > MAX_RESULTS_LIMIT) {
            throw new InvalidParameterException("maxResults must be between 1 and " + MAX_RESULTS_LIMIT);
        }
        String trimmedLocation = location == null || location.isBlank() ? null : location.trim();
        return service.search(new SearchQuery(trimmedKeywords, trimmedLocation, postedWithin, remote, maxResults));
    }
}
