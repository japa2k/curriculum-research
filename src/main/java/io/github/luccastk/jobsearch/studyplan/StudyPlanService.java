package io.github.luccastk.jobsearch.studyplan;

import io.github.luccastk.jobsearch.linkedin.JobDetail;
import io.github.luccastk.jobsearch.linkedin.JobDetailParser;
import io.github.luccastk.jobsearch.linkedin.LinkedInGuestClient;
import io.github.luccastk.jobsearch.linkedin.UpstreamException;
import io.github.luccastk.jobsearch.profile.Profile;
import io.github.luccastk.jobsearch.recommendation.PostingScorer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** Reads one posting, scores it like the recommendations do, and asks the Claude CLI for a study plan. */
@Service
public class StudyPlanService {

    private static final String JOB_URL = "https://www.linkedin.com/jobs/view/";

    private final LinkedInGuestClient client;
    private final JobDetailParser parser;
    private final PostingScorer scorer;
    private final Profile profile;
    private final ClaudeCli cli;

    public StudyPlanService(LinkedInGuestClient client, JobDetailParser parser, PostingScorer scorer, Profile profile,
            ClaudeCli cli) {
        this.client = client;
        this.parser = parser;
        this.scorer = scorer;
        this.profile = profile;
        this.cli = cli;
    }

    /**
     * @throws JobNotFoundException         when LinkedIn answers 404 for the posting
     * @throws UpstreamException            when the detail request fails otherwise or the page has no description
     * @throws StudyPlanGenerationException when the CLI fails
     */
    public StudyPlan generate(String jobId) {
        JobDetail detail = parser.parse(fetchDetail(jobId));
        if (detail.description() == null) {
            throw new UpstreamException("LinkedIn detail page for job " + jobId + " has no description", null);
        }
        String title = detail.title() == null ? "" : detail.title();
        PostingScorer.Score score = scorer.score(title, detail);
        Profile.StudyPlanSettings budget = profile.studyPlan();
        String plan = cli.run(StudyPlanPrompt.build(detail, profile.knownSkills(), score.skills().missingSkills(),
                budget));
        return new StudyPlan(
                jobId,
                detail.title(),
                detail.company(),
                JOB_URL + jobId,
                score.seniority(),
                score.skills().matchedSkills(),
                score.skills().missingSkills(),
                budget.weeklyHours(),
                budget.maxWeeks(),
                plan);
    }

    private String fetchDetail(String jobId) {
        try {
            return client.fetchDetail(jobId);
        } catch (UpstreamException e) {
            if (e.status() == HttpStatus.NOT_FOUND.value()) {
                throw new JobNotFoundException("LinkedIn has no job posting " + jobId + " (HTTP 404)", e);
            }
            throw e;
        }
    }
}
