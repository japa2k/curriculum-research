package io.github.luccastk.jobsearch.studyplan;

import io.github.luccastk.jobsearch.InvalidParameterException;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class StudyPlanController {

    private static final Pattern JOB_ID = Pattern.compile("[0-9]{1,20}");

    private final StudyPlanService service;

    public StudyPlanController(StudyPlanService service) {
        this.service = service;
    }

    @GetMapping(path = "/{id}/study-plan", produces = MediaType.APPLICATION_JSON_VALUE)
    public StudyPlan studyPlan(@PathVariable String id) {
        if (!JOB_ID.matcher(id).matches()) {
            throw new InvalidParameterException("id must be 1 to 20 decimal digits");
        }
        return service.generate(id);
    }
}
