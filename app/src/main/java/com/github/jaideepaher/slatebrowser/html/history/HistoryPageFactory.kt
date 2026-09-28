package com.github.jaideepaher.slatebrowser.html.history

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.compose.toRgbHexString
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.constant.FILE
import com.github.jaideepaher.slatebrowser.database.history.HistoryRepository
import com.github.jaideepaher.slatebrowser.di.GeneratedHtmlDir
import com.github.jaideepaher.slatebrowser.html.HtmlPageFactory
import com.github.jaideepaher.slatebrowser.html.ListPageReader
import com.github.jaideepaher.slatebrowser.html.jsoup.andBuild
import com.github.jaideepaher.slatebrowser.html.jsoup.body
import com.github.jaideepaher.slatebrowser.html.jsoup.clone
import com.github.jaideepaher.slatebrowser.html.jsoup.findId
import com.github.jaideepaher.slatebrowser.html.jsoup.id
import com.github.jaideepaher.slatebrowser.html.jsoup.parse
import com.github.jaideepaher.slatebrowser.html.jsoup.removeElement
import com.github.jaideepaher.slatebrowser.html.jsoup.style
import com.github.jaideepaher.slatebrowser.html.jsoup.tag
import com.github.jaideepaher.slatebrowser.html.jsoup.title
import com.github.jaideepaher.slatebrowser.theme.ThemeProvider
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import android.app.Application
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * Factory for the history page.
 */
class HistoryPageFactory @Inject constructor(
    private val listPageReader: ListPageReader,
    application: Application,
    private val historyRepository: HistoryRepository,
    private val themeProvider: ThemeProvider,
    private val coroutineDispatchers: CoroutineDispatchers,
    @GeneratedHtmlDir private val generatedHtmlDir: ThreadSafeFileProvider,
) : HtmlPageFactory {

    private val title = application.getString(R.string.action_history)

    override suspend fun buildPage(): String = withContext(coroutineDispatchers.io) {
        val colorScheme = themeProvider.colorScheme()
        val list = historyRepository.lastHundredVisitedHistoryEntries()
        val content = parse(listPageReader.provideHtml()) andBuild {
            title { title }
            style { content ->
                content.replace(
                    "--body-bg: {COLOR}",
                    "--body-bg: #${colorScheme.surface.toRgbHexString()};"
                ).replace(
                    "--divider-color: {COLOR}",
                    "--divider-color: #${colorScheme.outlineVariant.toRgbHexString()};"
                ).replace(
                    "--title-color: {COLOR}",
                    "--title-color: #${colorScheme.onSurface.toRgbHexString()};"
                ).replace(
                    "--subtitle-color: {COLOR}",
                    "--subtitle-color: #${colorScheme.onSurfaceVariant.toRgbHexString()};"
                )
            }
            body {
                val repeatedElement = findId("repeated").removeElement()
                id("content") {
                    list.forEach {
                        appendChild(repeatedElement.clone {
                            tag("a") { attr("href", it.url) }
                            id("title") { text(it.title) }
                            id("url") { text(it.url) }
                        })
                    }
                }
            }
        }

        val page = createHistoryPage()
        FileWriter(page, false).use { it.write(content) }

        "$FILE$page"
    }

    /**
     * Use this observable to immediately delete the history page. This will clear the cached
     * history page that was stored on file.
     *
     * @return a completable that deletes the history page when subscribed to.
     */
    suspend fun deleteHistoryPage(): Unit = withContext(coroutineDispatchers.io) {
        with(createHistoryPage()) {
            if (exists()) {
                delete()
            }
        }
    }

    private suspend fun createHistoryPage(): File {
        val generatedHtml = generatedHtmlDir.file()
        generatedHtml.mkdirs()
        return File(generatedHtml, FILENAME)
    }

    companion object {
        const val FILENAME = "history.html"
    }

}
