package io.github.luccastk.curriculumresearch.infra.linkedin;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import io.github.luccastk.curriculumresearch.domain.job.JobDescriptionSource;
import io.github.luccastk.curriculumresearch.domain.job.JobPosting;
import io.github.luccastk.curriculumresearch.domain.job.JobSource;

/** Scrapes LinkedIn's public guest job search and posting pages, which need no login. */
@Component
public class LinkedInJobSource implements JobSource, JobDescriptionSource {
    private static final String GUEST_ENDPOINT = "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search";
    private static final String POSTING_ENDPOINT = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    private static final int TIMEOUT_MILLIS = 10_000;

    @Override
    public List<JobPosting> searchJobs(String keywords, String location, int start) {
        String url = UriComponentsBuilder.fromUriString(GUEST_ENDPOINT)
                .queryParam("keywords", keywords)
                .queryParam("location", location)
                .queryParam("start", start)
                .encode()
                .toUriString();
        return scrape(url).select("li").stream()
                .map(this::toJobPosting)
                .filter(job -> !job.url().isEmpty())
                .toList();
    }

    @Override
    public String fetchDescription(JobPosting job) {
        String description = description(scrape(POSTING_ENDPOINT + job.id()));
        if (description.isEmpty()) {
            throw new IllegalStateException("LinkedIn posting " + job.id() + " has no description");
        }
        return description;
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
