package io.github.luccastk.jobsearch;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobSearchController {

    private final JobSearchService service;

    public JobSearchController(JobSearchService service) {
        this.service = service;
    }

    @GetMapping(path = "/search", produces = MediaType.APPLICATION_JSON_VALUE)
    public SearchResponse search(
            @RequestParam(required = false) String keywords,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "ANY") PostedWithin postedWithin,
            @RequestParam(defaultValue = "false") String remote,
            @RequestParam(defaultValue = "25") int maxResults) {
        return service.search(SearchQuery.of(keywords, location, postedWithin, remote, maxResults));
    }
}
