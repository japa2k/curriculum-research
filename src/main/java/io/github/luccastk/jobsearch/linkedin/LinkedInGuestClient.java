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
 * Fetches one page of job cards from LinkedIn's public, unauthenticated guest search endpoint.
 * No cookies or credentials are ever sent: the JDK client has no cookie handler by default.
 */
@Component
public class LinkedInGuestClient {

    static final String SEARCH_PATH = "/jobs-guest/jobs/api/seeMoreJobPostings/search";

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
        try {
            String html = restClient.get().uri(searchUri(query, start)).retrieve()
                    // Spring only rejects 4xx/5xx; redirects and LinkedIn's anti-bot 999 are failures too.
                    .onStatus(status -> !status.is2xxSuccessful(), (request, response) -> {
                        throw new RestClientResponseException("Non-2xx response", response.getStatusCode(),
                                response.getStatusText(), response.getHeaders(), null, null);
                    })
                    .body(String.class);
            return html == null ? "" : html;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.warn("LinkedIn page request failed: start={} status={}", start, status);
            throw new UpstreamException("LinkedIn responded with HTTP " + status + " (start=" + start + ")", e);
        } catch (RestClientException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            String exceptionType = cause.getClass().getSimpleName();
            log.warn("LinkedIn page request failed: start={} exception={}", start, exceptionType);
            String reason = isTimeout(cause)
                    ? "timeout after " + properties.timeout().toMillis() + " ms"
                    : "connection error (" + exceptionType + ")";
            throw new UpstreamException("LinkedIn request failed: " + reason + " (start=" + start + ")", e);
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
