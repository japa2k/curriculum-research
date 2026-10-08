package io.github.luccastk.jobsearch.recommendation;

import io.github.luccastk.jobsearch.JobPosting;
import io.github.luccastk.jobsearch.PostedWithin;
import io.github.luccastk.jobsearch.RequestPacer;
import io.github.luccastk.jobsearch.SearchQuery;
import io.github.luccastk.jobsearch.linkedin.JobCardParser;
import io.github.luccastk.jobsearch.linkedin.JobDetail;
import io.github.luccastk.jobsearch.linkedin.JobDetailParser;
import io.github.luccastk.jobsearch.linkedin.LinkedInGuestClient;
import io.github.luccastk.jobsearch.linkedin.UpstreamException;
import io.github.luccastk.jobsearch.profile.Profile;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Runs the profile's searches (first page only), drops senior postings, reads up to {@code maxResults}
 * detail pages picked round-robin across tracks, and ranks the postings by how well they fit the profile.
 */
@Service
public class RecommendationService {

    /** Cards on one guest search page; the query's limit is unused because only the first page is read. */
    private static final int FIRST_PAGE_SIZE = 10;

    private static final Comparator<String> NUMERIC_ID =
            Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder());

    static final Comparator<RecommendedJob> RANKING = Comparator
            .comparing(RecommendedJob::category)
            .thenComparingInt(job -> job.missingSkills().size())
            .thenComparing(RecommendedJob::postedAt, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
            .thenComparing(RecommendedJob::id, NUMERIC_ID);

    private record Found(JobPosting posting, String track) {
    }

    private final Profile profile;
    private final LinkedInGuestClient client;
    private final JobCardParser cardParser;
    private final JobDetailParser detailParser;
    private final PostingScorer scorer;
    private final RequestPacer pacer;

    public RecommendationService(Profile profile, LinkedInGuestClient client, JobCardParser cardParser,
            JobDetailParser detailParser, PostingScorer scorer, RequestPacer pacer) {
        this.profile = profile;
        this.client = client;
        this.cardParser = cardParser;
        this.detailParser = detailParser;
        this.scorer = scorer;
        this.pacer = pacer;
    }

    /** Fails with {@link UpstreamException} only when every search fails. */
    public RecommendationsResponse recommend(PostedWithin postedWithin, int maxResults) {
        Map<String, Found> foundById = new LinkedHashMap<>();
        UpstreamException firstFailure = null;
        int failedSearches = 0;
        int searches = 0;
        boolean firstRequest = true;
        for (Profile.Search search : profile.searches()) {
            for (Profile.Location location : profile.locations()) {
                if (!firstRequest) {
                    pacer.pause();
                }
                firstRequest = false;
                searches++;
                SearchQuery query = new SearchQuery(
                        search.keywords(), location.location(), postedWithin, location.remote(), FIRST_PAGE_SIZE);
                try {
                    for (JobPosting posting : cardParser.parse(client.fetchPage(query, 0)).jobs()) {
                        foundById.putIfAbsent(posting.id(), new Found(posting, search.track()));
                    }
                } catch (UpstreamException e) {
                    failedSearches++;
                    if (firstFailure == null) {
                        firstFailure = e;
                    }
                }
            }
        }
        if (failedSearches == searches) {
            throw new UpstreamException("all " + searches + " LinkedIn searches failed; first failure: "
                    + firstFailure.getMessage(), firstFailure.status(), firstFailure);
        }

        boolean partial = failedSearches > 0;
        List<RecommendedJob> jobs = new ArrayList<>();
        List<Found> eligible = foundById.values().stream()
                .filter(found -> !SeniorityRules.isSeniorOrAbove(found.posting().title()))
                .toList();
        for (Found found : roundRobinByTrack(eligible, maxResults)) {
            pacer.pause();
            JobDetail detail = fetchDetail(found.posting().id());
            PostingScorer.Score score = scorer.score(found.posting().title(), detail);
            partial |= !score.descriptionAvailable();
            jobs.add(toJob(found, score));
        }
        jobs.sort(RANKING);
        return new RecommendationsResponse(jobs.size(), partial, jobs);
    }

    /**
     * Takes each track's first posting, then each track's second, and so on, so the first searches cannot
     * fill {@code maxResults} on their own. Tracks go in the order they first appear in the profile's
     * searches; postings within a track keep merge order.
     */
    private List<Found> roundRobinByTrack(List<Found> eligible, int maxResults) {
        Map<String, Deque<Found>> byTrack = new LinkedHashMap<>();
        profile.searches().forEach(search -> byTrack.putIfAbsent(search.track(), new ArrayDeque<>()));
        eligible.forEach(found -> byTrack.get(found.track()).add(found));

        List<Found> selected = new ArrayList<>();
        while (selected.size() < maxResults && byTrack.values().stream().anyMatch(queue -> !queue.isEmpty())) {
            for (Deque<Found> queue : byTrack.values()) {
                if (!queue.isEmpty() && selected.size() < maxResults) {
                    selected.add(queue.poll());
                }
            }
        }
        return selected;
    }

    /** {@code null} when the request failed; the client already logged it. */
    private JobDetail fetchDetail(String jobId) {
        try {
            return detailParser.parse(client.fetchDetail(jobId));
        } catch (UpstreamException e) {
            return null;
        }
    }

    private static RecommendedJob toJob(Found found, PostingScorer.Score score) {
        JobPosting posting = found.posting();
        return new RecommendedJob(
                posting.id(),
                posting.title(),
                posting.company(),
                posting.location(),
                posting.url(),
                posting.postedAt(),
                found.track(),
                score.seniority(),
                score.descriptionAvailable(),
                score.skills().matchedSkills(),
                score.skills().missingSkills(),
                score.category());
    }
}
