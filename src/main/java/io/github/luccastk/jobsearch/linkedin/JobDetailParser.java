package io.github.luccastk.jobsearch.linkedin;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** Parses the HTML fragment returned by LinkedIn's guest job detail endpoint. */
@Component
public class JobDetailParser {

    private static final String SENIORITY_LEVEL = "seniority level";

    public JobDetail parse(String html) {
        Element page = Jsoup.parseBodyFragment(html).body();
        return new JobDetail(
                text(page.selectFirst("h2.top-card-layout__title")),
                text(page.selectFirst("a.topcard__org-name-link")),
                text(page.selectFirst("div.show-more-less-html__markup")),
                seniorityLevel(page));
    }

    private static String seniorityLevel(Element page) {
        for (Element item : page.select("li.description__job-criteria-item")) {
            String header = text(item.selectFirst("h3.description__job-criteria-subheader"));
            if (header != null && header.equalsIgnoreCase(SENIORITY_LEVEL)) {
                return text(item.selectFirst("span.description__job-criteria-text"));
            }
        }
        return null;
    }

    private static String text(Element element) {
        if (element == null) {
            return null;
        }
        String text = element.text().trim();
        return text.isEmpty() ? null : text;
    }
}
