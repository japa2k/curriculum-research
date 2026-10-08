package io.github.luccastk.jobsearch.recommendation;

import io.github.luccastk.jobsearch.InvalidParameterException;
import io.github.luccastk.jobsearch.PostedWithin;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class RecommendationController {

    static final int MAX_RESULTS_LIMIT = 50;

    private final RecommendationService service;

    public RecommendationController(RecommendationService service) {
        this.service = service;
    }

    @GetMapping(path = "/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationsResponse recommendations(
            @RequestParam(defaultValue = "WEEK") PostedWithin postedWithin,
            @RequestParam(defaultValue = "30") int maxResults) {
        if (maxResults < 1 || maxResults > MAX_RESULTS_LIMIT) {
            throw new InvalidParameterException("maxResults must be between 1 and " + MAX_RESULTS_LIMIT);
        }
        return service.recommend(postedWithin, maxResults);
    }
}
