package com.github.jaideepaher.slatebrowser.search

import com.github.jaideepaher.slatebrowser.preference.UserPreferencesDataStore
import com.github.jaideepaher.slatebrowser.search.engine.BaseSearchEngine
import com.github.jaideepaher.slatebrowser.search.engine.BingSearch
import com.github.jaideepaher.slatebrowser.search.engine.DuckSearch
import com.github.jaideepaher.slatebrowser.search.engine.GoogleSearch
import com.github.jaideepaher.slatebrowser.search.suggestions.DuckSuggestionsModel
import com.github.jaideepaher.slatebrowser.search.suggestions.GoogleSuggestionsModel
import com.github.jaideepaher.slatebrowser.search.suggestions.NoOpSuggestionsRepository
import com.github.jaideepaher.slatebrowser.search.suggestions.SuggestionsRepository
import dagger.Reusable
import javax.inject.Inject
import javax.inject.Provider

/**
 * The model that provides the search engine based
 * on the user's preference.
 */
@Reusable
class SearchEngineProvider @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val googleSuggestionsModel: Provider<GoogleSuggestionsModel>,
    private val duckSuggestionsModel: Provider<DuckSuggestionsModel>,
) {

    /**
     * Provide the [SuggestionsRepository] that maps to the user's current preference.
     */
    suspend fun provideSearchSuggestions(): SuggestionsRepository =
        when (userPreferencesDataStore.searchSuggestionChoice.get()) {
            Suggestions.NONE -> NoOpSuggestionsRepository()
            Suggestions.GOOGLE -> googleSuggestionsModel.get()
            Suggestions.DUCK -> duckSuggestionsModel.get()
        }

    /**
     * Provide the [BaseSearchEngine] that maps to the user's current preference.
     */
    suspend fun provideSearchEngine(): BaseSearchEngine =
        userPreferencesDataStore.searchChoice.get().asSearchEngine()

    /**
     * Provide a list of all supported search engines, in the same order as [SearchEngineChoice].
     */
    fun provideAllSearchEngines(): List<BaseSearchEngine> =
        SearchEngineChoice.entries.map { it.asSearchEngine() }

}

/**
 * The engine a [SearchEngineChoice] searches with.
 */
fun SearchEngineChoice.asSearchEngine(): BaseSearchEngine = when (this) {
    SearchEngineChoice.GOOGLE -> GoogleSearch()
    SearchEngineChoice.DUCK -> DuckSearch()
    SearchEngineChoice.BING -> BingSearch()
}
