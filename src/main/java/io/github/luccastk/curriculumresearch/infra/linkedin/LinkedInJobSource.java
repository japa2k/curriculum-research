package io.github.luccastk.curriculumresearch.infra.linkedin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.luccastk.curriculumresearch.domain.job.ApplyMethod;
import io.github.luccastk.curriculumresearch.domain.job.ApplyMethodSource;
import io.github.luccastk.curriculumresearch.domain.job.JobDescriptionSource;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobSource;

/** Scrapes LinkedIn's public guest job search and posting pages, which need no login. */
@Component
public class LinkedInJobSource implements JobSource, JobDescriptionSource, ApplyMethodSource {
    private static final String GUEST_ENDPOINT = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String POSTING_ENDPOINT = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final int TIMEOUT_MILLIS = 10_000;

    @Override
    public List<JobPosting> searchJobs(String keywords, String location, int start) {
        return scrape(searchUrl(keywords, location, start)).select("li").stream()
                .map(this::toJobPosting)
                .filter(job -> !job.url().isEmpty())
                .toList();
    }

    /**
     * Asks for Easy Apply postings only ({@code f_AL}), which are applied to without leaving
     * LinkedIn. The guest search does not always honour it, so {@link #applyMethod} checks each one.
     */
    static String searchUrl(String keywords, String location, int start) {
        return UriComponentsBuilder.fromUriString(GUEST_ENDPOINT)
                .queryParam("keywords", keywords)
                .queryParam("location", location)
                .queryParam("f_AL", true)
                .queryParam("start", start)
                .encode()
                .toUriString();
    }

    @Override
    public String fetchDescription(JobPosting job) {
        String description = description(scrape(POSTING_ENDPOINT + job.id()));
        if (description.isEmpty()) {
            throw new IllegalStateException("LinkedIn posting " + job.id() + " has no description");
        }
        return description;
    }

    /** A posting taken down gives {@link ApplyMethod#UNKNOWN}; a page that is not the posting throws. */
    @Override
    public ApplyMethod applyMethod(JobPosting job) {
        Document page;
        try {
            page = scrape(POSTING_ENDPOINT + job.id());
        } catch (UncheckedIOException e) {
            if (e.getCause() instanceof HttpStatusException status && status.getStatusCode() == 404) {
                return ApplyMethod.UNKNOWN;
            }
            throw e;
        }
        if (page.selectFirst(".top-card-layout__title") == null) {
            // LinkedIn answers rate-limited guests with a script-only challenge page.
            throw new IllegalStateException("LinkedIn did not return the page of posting " + job.id());
        }
        return applyMethod(page);
    }

    /**
     * Read from the tracking name of the posting's Apply button: {@code public_jobs_apply-link-onsite}
     * (or {@code -simple_onsite}) for Easy Apply, {@code public_jobs_apply-link-offsite} for the
     * company's site. A closed posting has neither.
     */
    static ApplyMethod applyMethod(Document page) {
        if (!page.select("[data-tracking-control-name^=public_jobs_apply-link-offsite]").isEmpty()) {
            return ApplyMethod.EXTERNAL;
        }
        if (!page.select("[data-tracking-control-name=public_jobs_apply-link-onsite], "
                + "[data-tracking-control-name=public_jobs_apply-link-simple_onsite]").isEmpty()) {
            return ApplyMethod.EASY_APPLY;
        }
        return ApplyMethod.UNKNOWN;
    }

    /** The description's text, keeping its paragraphs and list items on separate lines. */
    static String description(Document page) {
        Element markup = page.selectFirst("div.show-more-less-html__markup");
        if (markup == null) {
            return "";
        }
        markup.select("br").after("\n");
        markup.select("p, li, h1, h2, h3, h4, ul, ol").before("\n");
        markup.select("li").prepend("- ");
        return markup.wholeText().lines()
                .map(String::strip)
                .filter(line -> !line.isEmpty())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    private Document scrape(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MILLIS)
                    .get();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scrape " + url, e);
        }
    }

    private JobPosting toJobPosting(Element card) {
        String url = card.select("a.base-card__full-link").attr("href");
        int query = url.indexOf('?');
        return new JobPosting(
                card.select("h3.base-search-card__title").text(),
                card.select("h4.base-search-card__subtitle").text(),
                card.select("span.job-search-card__location").text(),
                query >= 0 ? url.substring(0, query) : url);
    }
}
