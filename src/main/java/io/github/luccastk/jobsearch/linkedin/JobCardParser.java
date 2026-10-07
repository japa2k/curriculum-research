package io.github.luccastk.jobsearch.linkedin;

import io.github.luccastk.jobsearch.JobPosting;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

/** Parses the HTML fragment of job cards returned by LinkedIn's guest search endpoint. */
@Component
public class JobCardParser {

    private static final Pattern JOB_URN = Pattern.compile("^urn:li:jobPosting:(\\d+)$");

    /** {@code cardCount} counts every card on the page, including those skipped as unusable. */
    public record Page(int cardCount, List<JobPosting> jobs) {
    }

    public Page parse(String html) {
        Elements cards = Jsoup.parseBodyFragment(html).select("div.base-card");
        List<JobPosting> jobs = new ArrayList<>();
        for (Element card : cards) {
            String id = jobId(card);
            String title = text(card, "h3.base-search-card__title");
            if (id == null || title == null) {
                continue;
            }
            jobs.add(new JobPosting(
                    id,
                    title,
                    text(card, "h4.base-search-card__subtitle"),
                    text(card, "span.job-search-card__location"),
                    url(card),
                    postedAt(card)));
        }
        return new Page(cards.size(), jobs);
    }

    private static String jobId(Element card) {
        Matcher matcher = JOB_URN.matcher(card.attr("data-entity-urn").trim());
        return matcher.matches() ? matcher.group(1) : null;
    }

    private static String text(Element card, String selector) {
        Element element = card.selectFirst(selector);
        if (element == null) {
            return null;
        }
        String text = element.text().trim();
        return text.isEmpty() ? null : text;
    }

    /** The card link without its query string and fragment. */
    private static String url(Element card) {
        Element link = card.selectFirst("a.base-card__full-link[href]");
        return link == null ? null : link.attr("href").split("[?#]", 2)[0];
    }

    private static LocalDate postedAt(Element card) {
        Element time = card.selectFirst("time[datetime]");
        if (time == null) {
            return null;
        }
        try {
            return LocalDate.parse(time.attr("datetime").trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
