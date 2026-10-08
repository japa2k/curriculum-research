package io.github.luccastk.jobsearch.linkedin;

import io.github.luccastk.jobsearch.SearchQuery;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Fetches job card pages and job detail pages from LinkedIn's public, unauthenticated guest endpoints.
 * No cookies or credentials are ever sent: the JDK client has no cookie handler by default.
 */
@Component
public class LinkedInGuestClient {

    static final String SEARCH_PATH = "/jobs-guest/jobs/api/seeMoreJobPostings/search";
    static final String DETAIL_PATH = "/jobs-guest/jobs/api/jobPosting/{id}";

    private static final Logger log = LoggerFactory.getLogger(LinkedInGuestClient.class);

    private final LinkedInProperties properties;
    private final RestClient restClient;

    public LinkedInGuestClient(RestClient.Builder builder, LinkedInProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.restClient = builder
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.userAgent())
                .build();
    }

    /** Returns the HTML fragment of the page starting at {@code start}; empty when LinkedIn sends no body. */
    public String fetchPage(SearchQuery query, int start) {
        String logLabel = "search keywords=" + query.keywords()
                + (query.location() == null ? "" : " location=" + query.location())
                + " start=" + start;
        return fetch(searchUri(query, start), logLabel, "start=" + start);
    }

    /** Returns the HTML fragment of one posting's detail page; empty when LinkedIn sends no body. */
    public String fetchDetail(String jobId) {
        URI uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(DETAIL_PATH)
                .buildAndExpand(jobId)
                .encode()
                .toUri();
        return fetch(uri, "detail jobId=" + jobId, "jobId=" + jobId);
    }

    /**
     * @param logLabel   names the request in WARN lines
     * @param errorLabel names it in the error reported to API clients; never caller input, which is not echoed
     */
    private String fetch(URI uri, String logLabel, String errorLabel) {
        try {
            String html = restClient.get().uri(uri).retrieve()
                    // Spring only rejects 4xx/5xx; redirects and LinkedIn's anti-bot 999 are failures too.
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new RestClientResponseException("Non-2xx response", response.getStatusCode(),
                                response.getStatusText(), response.getHeaders(), null, null);
                    })
                    .body(String.class);
            return html == null ? "" : html;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("LinkedIn request failed: {} status={}", logLabel, status);
            throw new UpstreamException("LinkedIn responded with HTTP " + status + " (" + errorLabel + ")", status, e);
        } catch (RestClientException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String exceptionType = cause.getClass().getSimpleName();
            log.warn("LinkedIn request failed: {} exception={}", logLabel, exceptionType);
            String reason = isTimeout(cause)
                    ? "timeout after " + properties.timeout().toMillis() + " ms"
                    : "connection error (" + exceptionType + ")";
            throw new UpstreamException("LinkedIn request failed: " + reason + " (" + errorLabel + ")", e);
        }
    }

    private URI searchUri(SearchQuery query, int start) {
        UriComponentsBuilder uri = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .path(SEARCH_PATH)
                .queryParam("keywords", "{keywords}");
        if (query.location() != null) {
            uri.queryParam("location", "{location}");
        }
        String timePostedRange = switch (query.postedWithin()) {
            case DAY -> "r86400";
            case WEEK -> "r604800";
            case MONTH -> "r2592000";
            case ANY -> null;
        };
        if (timePostedRange != null) {
            uri.queryParam("f_TPR", timePostedRange);
        }
        if (query.remote()) {
            uri.queryParam("f_WT", "2");
        }
        uri.queryParam("start", start);
        // Strict encoding of the expanded values, so "C++" or "R&D" reach LinkedIn intact.
        return uri.encode()
                .buildAndExpand(query.keywords(), query.location() == null ? "" : query.location())
                .toUri();
    }

    private static boolean isTimeout(Throwable cause) {
        return cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException;
    }
}
