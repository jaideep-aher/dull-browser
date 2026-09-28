package com.github.jaideepaher.slatebrowser.utils

import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.preference.DeveloperPreferenceStore
import kotlinx.coroutines.launch
import leakcanary.LeakCanary
import javax.inject.Inject

/**
 * Sets up LeakCanary.
 */
class LeakCanaryUtils @Inject constructor(
    private val developerPreferenceStore: DeveloperPreferenceStore,
    private val appCoroutineScope: AppCoroutineScope,
    private val coroutineDispatchers: CoroutineDispatchers,
) {

    /**
     * Setup LeakCanary
     */
    fun setup() {
        appCoroutineScope.launch(coroutineDispatchers.io) {
            LeakCanary.config = LeakCanary.config.copy(
                dumpHeap = developerPreferenceStore.useLeakCanary.get()
            )
        }
    }

}
