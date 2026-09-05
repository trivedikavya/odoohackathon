package com.urbanfurniture.accounting.common;

/**
 * Normalises free-text search input.
 * <p>
 * An absent filter becomes an empty string rather than {@code null}: a
 * {@code LIKE '%%'} matches everything, and keeping the parameter non-null
 * means PostgreSQL can always infer its type. Binding a bare null into a
 * {@code lower(...)}/{@code concat(...)} expression fails with
 * "function lower(bytea) does not exist".
 */
public final class SearchTerms {

    private SearchTerms() {
    }

    public static String normalize(String search) {
        return (search == null || search.isBlank()) ? "" : search.trim();
    }
}
