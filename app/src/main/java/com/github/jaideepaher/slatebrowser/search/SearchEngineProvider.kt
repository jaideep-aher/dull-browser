package com.github.jaideepaher.slatebrowser.search

import com.github.jaideepaher.slatebrowser.preference.UserPreferencesDataStore
import com.github.jaideepaher.slatebrowser.search.engine.BaiduSearch
import com.github.jaideepaher.slatebrowser.search.engine.BaseSearchEngine
import com.github.jaideepaher.slatebrowser.search.engine.BingSearch
import com.github.jaideepaher.slatebrowser.search.engine.CustomSearch
import com.github.jaideepaher.slatebrowser.search.engine.DuckLiteSearch
import com.github.jaideepaher.slatebrowser.search.engine.DuckSearch
import com.github.jaideepaher.slatebrowser.search.engine.GoogleSearch
import com.github.jaideepaher.slatebrowser.search.engine.KagiSearch
import com.github.jaideepaher.slatebrowser.search.engine.NaverSearch
import com.github.jaideepaher.slatebrowser.search.engine.StartPageSearch
import com.github.jaideepaher.slatebrowser.search.engine.YahooSearch
import com.github.jaideepaher.slatebrowser.search.engine.YandexSearch
import com.github.jaideepaher.slatebrowser.search.suggestions.BaiduSuggestionsModel
import com.github.jaideepaher.slatebrowser.search.suggestions.DuckSuggestionsModel
import com.github.jaideepaher.slatebrowser.search.suggestions.GoogleSuggestionsModel
import com.github.jaideepaher.slatebrowser.search.suggestions.KagiSuggestionsModel
import com.github.jaideepaher.slatebrowser.search.suggestions.NaverSuggestionsModel
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
    private val baiduSuggestionsModel: Provider<BaiduSuggestionsModel>,
    private val naverSuggestionsModel: Provider<NaverSuggestionsModel>,
    private val kagiSuggestionsModel: Provider<KagiSuggestionsModel>,
) {

    /**
     * Provide the [SuggestionsRepository] that maps to the user's current preference.
     */
    suspend fun provideSearchSuggestions(): SuggestionsRepository =
        when (userPreferencesDataStore.searchSuggestionChoice.get()) {
            Suggestions.NONE -> NoOpSuggestionsRepository()
            Suggestions.GOOGLE -> googleSuggestionsModel.get()
            Suggestions.DUCK -> duckSuggestionsModel.get()
            Suggestions.BAIDU -> baiduSuggestionsModel.get()
            Suggestions.NAVER -> naverSuggestionsModel.get()
            Suggestions.KAGI -> kagiSuggestionsModel.get()
        }

    /**
     * Provide the [BaseSearchEngine] that maps to the user's current preference.
     */
    suspend fun provideSearchEngine(): BaseSearchEngine =
        when (userPreferencesDataStore.searchChoice.get()) {
            SearchEngineChoice.CUSTOM -> CustomSearch(userPreferencesDataStore.searchUrl.get())
            SearchEngineChoice.GOOGLE -> GoogleSearch()
            SearchEngineChoice.BING -> BingSearch()
            SearchEngineChoice.YAHOO -> YahooSearch()
            SearchEngineChoice.START_PAGE -> StartPageSearch()
            SearchEngineChoice.DUCK -> DuckSearch()
            SearchEngineChoice.DUCK_LITE -> DuckLiteSearch()
            SearchEngineChoice.BAIDU -> BaiduSearch()
            SearchEngineChoice.YANDEX -> YandexSearch()
            SearchEngineChoice.NAVER -> NaverSearch()
            SearchEngineChoice.KAGI -> KagiSearch()
        }

    /**
     * Provide a list of all supported search engines.
     */
    suspend fun provideAllSearchEngines(): List<BaseSearchEngine> = listOf(
        CustomSearch(userPreferencesDataStore.searchUrl.get()),
        GoogleSearch(),
        BingSearch(),
        YahooSearch(),
        StartPageSearch(),
        DuckSearch(),
        DuckLiteSearch(),
        BaiduSearch(),
        YandexSearch(),
        NaverSearch(),
        KagiSearch()
    )

}
