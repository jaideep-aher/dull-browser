package com.github.jaideepaher.slatebrowser.html.homepage

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.compose.toRgbHexString
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.constant.FILE
import com.github.jaideepaher.slatebrowser.constant.UTF8
import com.github.jaideepaher.slatebrowser.di.GeneratedHtmlDir
import com.github.jaideepaher.slatebrowser.html.HtmlPageFactory
import com.github.jaideepaher.slatebrowser.html.jsoup.andBuild
import com.github.jaideepaher.slatebrowser.html.jsoup.body
import com.github.jaideepaher.slatebrowser.html.jsoup.charset
import com.github.jaideepaher.slatebrowser.html.jsoup.parse
import com.github.jaideepaher.slatebrowser.html.jsoup.style
import com.github.jaideepaher.slatebrowser.html.jsoup.tag
import com.github.jaideepaher.slatebrowser.html.jsoup.title
import com.github.jaideepaher.slatebrowser.focus.Bookmarks
import com.github.jaideepaher.slatebrowser.focus.Countdowns
import com.github.jaideepaher.slatebrowser.focus.Feature
import com.github.jaideepaher.slatebrowser.focus.Stats
import com.github.jaideepaher.slatebrowser.preference.UserPreferencesDataStore
import com.github.jaideepaher.slatebrowser.search.SearchEngineProvider
import com.github.jaideepaher.slatebrowser.theme.ThemeProvider
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import android.app.Application
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import javax.inject.Inject

/**
 * A factory for the home page.
 */
class HomePageFactory @Inject constructor(
    private val application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val homePageReader: HomePageReader,
    private val themeProvider: ThemeProvider,
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val coroutineDispatchers: CoroutineDispatchers,
    @GeneratedHtmlDir private val generatedHtmlDir: ThreadSafeFileProvider,
    private val countdowns: Countdowns,
    private val bookmarks: Bookmarks,
    private val stats: Stats,
) : HtmlPageFactory {

    private val title = application.getString(R.string.home)

    override suspend fun buildPage(): String = withContext(coroutineDispatchers.io) {
        val colorScheme = themeProvider.colorScheme()
        val (_, queryUrl, titleRes) = searchEngineProvider.provideSearchEngine()
        val searchHint = application.getString(R.string.new_tab_search_hint, application.getString(titleRes))
        val clock = when (userPreferencesDataStore.newTabClock.get()) {
            NewTabClock.OFF -> "off"
            NewTabClock.TWELVE_HOUR -> "12"
            NewTabClock.TWENTY_FOUR_HOUR -> "24"
        }
        val scheme = if (themeProvider.isDarkTheme()) "dark" else "light"
        val content = parse(homePageReader.provideHtml()) andBuild {
            title { title }
            style { content ->
                content.replace(
                    "color-scheme: {SCHEME}",
                    "color-scheme: $scheme;"
                ).replace(
                    "--body-bg: {COLOR}",
                    "--body-bg: #${colorScheme.surface.toRgbHexString()};"
                ).replace(
                    "--box-bg: {COLOR}",
                    "--box-bg: #${colorScheme.surfaceContainer.toRgbHexString()};"
                ).replace(
                    "--box-txt: {COLOR}",
                    "--box-txt: #${colorScheme.onSurface.toRgbHexString()};"
                ).replace(
                    "--muted: {COLOR}",
                    "--muted: #${colorScheme.onSurfaceVariant.toRgbHexString()};"
                ).replace(
                    "--border: {COLOR}",
                    "--border: #${colorScheme.outlineVariant.toRgbHexString()};"
                )
            }
            charset { UTF8 }
            body {
                tag("script") {
                    html(
                        html()
                            .replace($$"${BASE_URL}", queryUrl)
                            .replace($$"${CLOCK}", clock)
                            .replace($$"${SEARCH_HINT}", searchHint.replace("\"", "\\\""))
                            .replace($$"${COUNTDOWN}", countdownLabel().replace("\"", "\\\""))
                            .replace($$"${MILESTONE}", milestoneLabel())
                            .replace($$"${QUICK_LINKS}", quickLinksJson())
                            .replace("&", "\\u0026")
                    )
                }
            }
        }
        val page = createHomePage()
        FileWriter(page, false).use {
            it.write(content)
        }

        "$FILE$page"
    }

    /**
     * Create the home page file.
     */
    private suspend fun createHomePage(): File {
        val generatedHtml = generatedHtmlDir.file()
        generatedHtml.mkdirs()
        return File(generatedHtml, FILENAME)
    }

    private fun countdownLabel(): String =
        if (Feature.COUNTDOWNS.isUnlocked) countdowns.nextLabel.orEmpty() else ""

    private fun milestoneLabel(): String {
        if (!Feature.MILESTONES.isUnlocked) return ""
        return stats.snapshot.noticeMilestone?.toString().orEmpty()
    }

    private fun quickLinksJson(): String {
        if (!Feature.BOOKMARKS.isUnlocked) return "[]"
        return bookmarks.quickLinks.joinToString(prefix = "[", postfix = "]") { link ->
            val host = runCatching { java.net.URI(link.url).host }.getOrNull().orEmpty()
            """{"url":"${link.url.escapeJs()}","title":"${link.title.escapeJs()}","host":"${host.escapeJs()}"}"""
        }
    }

    private fun String.escapeJs() = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    companion object {

        const val FILENAME = "homepage.html"

    }

}
