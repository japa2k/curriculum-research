package io.github.luccastk.jobsearch;

/** A validated job search; {@code location} is {@code null} when not given. */
public record SearchQuery(String keywords, String location, PostedWithin postedWithin, boolean remote, int maxResults) {

    static final int MAX_KEYWORDS_LENGTH = 100;
    static final int MAX_LOCATION_LENGTH = 100;
    static final int MAX_RESULTS_LIMIT = 100;

    /**
     * Validates raw search parameters, shared by the REST endpoint and the configured alert searches.
     *
     * @throws InvalidParameterException naming the first invalid parameter
     */
    public static SearchQuery of(String keywords, String location, PostedWithin postedWithin, String remote,
            int maxResults) {
        String trimmedKeywords = keywords == null ? "" : keywords.trim();
        if (trimmedKeywords.isEmpty()) {
            throw new InvalidParameterException("keywords is required");
        }
        if (trimmedKeywords.length() > MAX_KEYWORDS_LENGTH) {
            throw new InvalidParameterException("keywords must be at most " + MAX_KEYWORDS_LENGTH + " characters");
        }
        if (maxResults < 1 || maxResults > MAX_RESULTS_LIMIT) {
            throw new InvalidParameterException("maxResults must be between 1 and " + MAX_RESULTS_LIMIT);
        }
        String trimmedLocation = location == null || location.isBlank() ? null : location.trim();
        if (trimmedLocation != null && trimmedLocation.length() > MAX_LOCATION_LENGTH) {
            throw new InvalidParameterException("location must be at most " + MAX_LOCATION_LENGTH + " characters");
        }
        return new SearchQuery(
                trimmedKeywords, trimmedLocation, postedWithin, parseBoolean("remote", remote), maxResults);
    }

    /** Only {@code true} or {@code false}: Spring's converter would also accept 1/0, yes/no, on/off. */
    private static boolean parseBoolean(String name, String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new InvalidParameterException(name + " must be true or false");
    }
}
