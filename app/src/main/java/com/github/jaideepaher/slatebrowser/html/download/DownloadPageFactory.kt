package com.github.jaideepaher.slatebrowser.html.download

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.compose.toRgbHexString
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.constant.FILE
import com.github.jaideepaher.slatebrowser.database.downloads.DownloadEntry
import com.github.jaideepaher.slatebrowser.database.downloads.DownloadsRepository
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
 * The factory for the downloads page.
 */
class DownloadPageFactory @Inject constructor(
    private val application: Application,
    private val manager: DownloadsRepository,
    private val listPageReader: ListPageReader,
    private val themeProvider: ThemeProvider,
    private val coroutineDispatchers: CoroutineDispatchers,
    @GeneratedHtmlDir private val generatedHtmlDir: ThreadSafeFileProvider,
) : HtmlPageFactory {

    override suspend fun buildPage(): String = withContext(coroutineDispatchers.io) {
        val colorScheme = themeProvider.colorScheme()
        val downloads = manager.getAllDownloads()
        val content = parse(listPageReader.provideHtml()) andBuild {
            title { application.getString(R.string.action_downloads) }
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
                val repeatableElement = findId("repeated").removeElement()
                id("content") {
                    downloads.forEach { download ->
                        appendChild(repeatableElement.clone {
                            tag("a") { attr("href", download.location) }
                            id("title") { text(createFileTitle(download)) }
                            id("url") { text(download.url) }
                        })
                    }
                }
            }
        }
        val page = createDownloadsPageFile()
        FileWriter(page, false).use { it.write(content) }

        "$FILE$page"
    }

    private suspend fun createDownloadsPageFile(): File {
        val generatedHtml = generatedHtmlDir.file()
        generatedHtml.mkdirs()
        return File(generatedHtml, FILENAME)
    }

    private fun createFileTitle(downloadItem: DownloadEntry): String {
        val contentSize = if (downloadItem.contentSize.isNotBlank()) {
            "[${downloadItem.contentSize}]"
        } else {
            ""
        }

        return "${downloadItem.title} $contentSize"
    }

    companion object {

        const val FILENAME = "downloads.html"

    }

}
