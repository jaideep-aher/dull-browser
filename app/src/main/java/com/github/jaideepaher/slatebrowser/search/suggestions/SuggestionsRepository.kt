package com.github.jaideepaher.slatebrowser.search.suggestions

import com.github.jaideepaher.slatebrowser.database.SearchSuggestion

/**
 * A repository for search suggestions.
 */
interface SuggestionsRepository {

    /**
     * Fetches the search suggestion results for the provided query.
     *
     * @param rawQuery the raw query to retrieve the results for.
     * @return The list of results for the query.
     */
    suspend fun resultsForSearch(rawQuery: String): List<SearchSuggestion>

}
