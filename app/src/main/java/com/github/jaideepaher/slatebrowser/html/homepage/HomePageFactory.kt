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
    application: Application,
    private val searchEngineProvider: SearchEngineProvider,
    private val homePageReader: HomePageReader,
    private val themeProvider: ThemeProvider,
    private val coroutineDispatchers: CoroutineDispatchers,
    @GeneratedHtmlDir private val generatedHtmlDir: ThreadSafeFileProvider,
) : HtmlPageFactory {

    private val title = application.getString(R.string.home)

    override suspend fun buildPage(): String = withContext(coroutineDispatchers.io) {
        val colorScheme = themeProvider.colorScheme()
        val (_, queryUrl, _) = searchEngineProvider.provideSearchEngine()
        val content = parse(homePageReader.provideHtml()) andBuild {
            title { title }
            style { content ->
                content.replace(
                    "--body-bg: {COLOR}",
                    "--body-bg: #${colorScheme.surface.toRgbHexString()};"
                ).replace(
                    "--box-bg: {COLOR}",
                    "--box-bg: #${colorScheme.surfaceContainer.toRgbHexString()};"
                ).replace(
                    "--box-txt: {COLOR}",
                    "--box-txt: #${colorScheme.onSurfaceVariant.toRgbHexString()};"
                )
            }
            charset { UTF8 }
            body {
                tag("script") {
                    html(
                        html()
                            .replace($$"${BASE_URL}", queryUrl)
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

    companion object {

        const val FILENAME = "homepage.html"

    }

}
