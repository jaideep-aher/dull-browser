package com.github.jaideepaher.slatebrowser.di

import com.github.jaideepaher.slatebrowser.browser.BrowserContract
import com.github.jaideepaher.slatebrowser.browser.history.DefaultHistoryRecord
import com.github.jaideepaher.slatebrowser.browser.history.HistoryRecord
import com.github.jaideepaher.slatebrowser.browser.history.NoOpHistoryRecord
import com.github.jaideepaher.slatebrowser.browser.notification.DefaultTabCountNotifier
import com.github.jaideepaher.slatebrowser.browser.notification.IncognitoTabCountNotifier
import com.github.jaideepaher.slatebrowser.browser.notification.TabCountNotifier
import com.github.jaideepaher.slatebrowser.browser.search.IntentExtractor
import com.github.jaideepaher.slatebrowser.browser.tab.bundle.BundleStore
import com.github.jaideepaher.slatebrowser.browser.tab.bundle.DefaultBundleStore
import com.github.jaideepaher.slatebrowser.browser.tab.bundle.IncognitoBundleStore
import android.content.Intent
import dagger.Module
import dagger.Provides

/**
 * Constructs dependencies for the browser scope.
 */
@Module
class BrowserModule {

    @Provides
    @InitialAction
    fun providesInitialUrl(
        @InitialIntent initialIntent: Intent?,
        intentExtractor: IntentExtractor
    ): BrowserContract.Action? = intentExtractor.extractUrlFromIntent(initialIntent)

    @Provides
    fun providesHistoryRecord(
        @IncognitoMode incognitoMode: Boolean,
        defaultHistoryRecord: DefaultHistoryRecord
    ): HistoryRecord = if (incognitoMode) {
        NoOpHistoryRecord
    } else {
        defaultHistoryRecord
    }

    @Provides
    fun providesTabCountNotifier(
        @IncognitoMode incognitoMode: Boolean,
        incognitoTabCountNotifier: IncognitoTabCountNotifier
    ): TabCountNotifier = if (incognitoMode) {
        incognitoTabCountNotifier
    } else {
        DefaultTabCountNotifier
    }

    @Provides
    fun providesBundleStore(
        @IncognitoMode incognitoMode: Boolean,
        defaultBundleStore: DefaultBundleStore
    ): BundleStore = if (incognitoMode) {
        IncognitoBundleStore
    } else {
        defaultBundleStore
    }
}
