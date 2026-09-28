package com.github.jaideepaher.slatebrowser.browser.history

import com.github.jaideepaher.slatebrowser.concurrency.AppCoroutineScope
import com.github.jaideepaher.slatebrowser.database.history.HistoryRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The default history record that records the history in a permanent data store.
 */
class DefaultHistoryRecord @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val appCoroutineScope: AppCoroutineScope,
) : HistoryRecord {
    override fun visit(title: String, url: String) {
        appCoroutineScope.launch {
            historyRepository.visitHistoryEntry(url, title)
        }
    }
}
