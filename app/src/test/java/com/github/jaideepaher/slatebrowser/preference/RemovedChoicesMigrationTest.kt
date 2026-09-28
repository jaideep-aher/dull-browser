package com.github.jaideepaher.slatebrowser.preference

import com.github.jaideepaher.slatebrowser.AppTheme
import com.github.jaideepaher.slatebrowser.search.SearchEngineChoice
import com.github.jaideepaher.slatebrowser.search.Suggestions
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class RemovedChoicesMigrationTest {

    private val search = intPreferencesKey("search")
    private val suggestions = intPreferencesKey("searchSuggestionsChoice")
    private val theme = intPreferencesKey("Theme")
    private val searchUrl = stringPreferencesKey("searchurl")

    @Test
    fun `removed search engine and suggestions fall back to Google`() = runTest {
        val kagi = 12
        val baidu = 3
        val migrated = RemovedChoicesMigration.migrate(
            preferencesOf(search to kagi, suggestions to baidu, searchUrl to "https://example.com/?q=")
        )

        assertThat(migrated[search]).isEqualTo(SearchEngineChoice.GOOGLE.value)
        assertThat(migrated[suggestions]).isEqualTo(Suggestions.GOOGLE.value)
        assertThat(migrated[searchUrl]).isNull()
    }

    @Test
    fun `black theme becomes dark`() = runTest {
        val migrated = RemovedChoicesMigration.migrate(preferencesOf(theme to 2))

        assertThat(migrated[theme]).isEqualTo(AppTheme.DARK.value)
    }

    @Test
    fun `remaining choices are left alone`() = runTest {
        val current = preferencesOf(
            search to SearchEngineChoice.BING.value,
            suggestions to Suggestions.NONE.value,
            theme to AppTheme.SYSTEM.value,
        )

        assertThat(RemovedChoicesMigration.shouldMigrate(current)).isFalse()
        assertThat(RemovedChoicesMigration.migrate(current)).isEqualTo(current)
    }
}
