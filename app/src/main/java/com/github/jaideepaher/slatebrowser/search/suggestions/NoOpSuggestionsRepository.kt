package com.github.jaideepaher.slatebrowser.search.suggestions

import com.github.jaideepaher.slatebrowser.database.SearchSuggestion

/**
 * A search suggestions repository that doesn't fetch any results.
 */
class NoOpSuggestionsRepository : SuggestionsRepository {

    override suspend fun resultsForSearch(rawQuery: String) = emptyList<SearchSuggestion>()
}
