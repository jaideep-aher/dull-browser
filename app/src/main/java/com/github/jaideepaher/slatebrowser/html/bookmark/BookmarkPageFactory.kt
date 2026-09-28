package com.github.jaideepaher.slatebrowser.html.bookmark

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.compose.toRgbHexString
import com.github.jaideepaher.slatebrowser.concurrency.CoroutineDispatchers
import com.github.jaideepaher.slatebrowser.constant.FILE
import com.github.jaideepaher.slatebrowser.database.Bookmark
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkRepository
import com.github.jaideepaher.slatebrowser.di.FaviconCacheDir
import com.github.jaideepaher.slatebrowser.di.GeneratedHtmlDir
import com.github.jaideepaher.slatebrowser.extensions.safeUse
import com.github.jaideepaher.slatebrowser.favicon.FaviconModel
import com.github.jaideepaher.slatebrowser.html.HtmlPageFactory
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
import com.github.jaideepaher.slatebrowser.utils.ThemeUtils
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import javax.inject.Inject

class BookmarkPageFactory @Inject constructor(
    private val application: Application,
    private val bookmarkModel: BookmarkRepository,
    private val faviconModel: FaviconModel,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val bookmarkPageReader: BookmarkPageReader,
    private val themeProvider: ThemeProvider,
    @GeneratedHtmlDir private val generatedHtmlDir: ThreadSafeFileProvider,
    @FaviconCacheDir private val faviconCacheDir: ThreadSafeFileProvider,
) : HtmlPageFactory {

    private val title = application.getString(R.string.action_bookmarks)

    override suspend fun buildPage(): String = withContext(coroutineDispatchers.io) {
        val folderIcon = File(faviconCacheDir.file(), FOLDER_ICON)
        val defaultIcon = File(faviconCacheDir.file(), DEFAULT_ICON)
        val bookmarks = bookmarkModel.getAllBookmarksSorted()
        bookmarks.groupBy { it.folder }
            .mapValues { (folder, bookmarks) ->
                if (folder == Bookmark.Folder.Root) {
                    construct((bookmarks + bookmarkModel.getFoldersSorted()).map {
                        it.asViewModel(folderIcon, defaultIcon)
                    })
                } else {
                    construct(bookmarks.map {
                        it.asViewModel(folderIcon, defaultIcon)
                    })
                }
            }.forEach { (folder, content) ->
                FileWriter(createBookmarkPage(folder), false).use {
                    it.write(content)
                }
            }

        val colorScheme = themeProvider.colorScheme()
        cacheIcon(
            ThemeUtils.createThemedBitmap(
                application,
                R.drawable.ic_folder,
                colorScheme.onSurfaceVariant.toArgb()
            ),
            folderIcon
        )
        cacheIcon(
            faviconModel.createDefaultBitmapForTitle(null),
            defaultIcon
        )

        "$FILE${createBookmarkPage(null)}"
    }

    private fun cacheIcon(icon: Bitmap, file: File) = FileOutputStream(file).safeUse {
        icon.compress(Bitmap.CompressFormat.PNG, 100, it)
        icon.recycle()
    }

    private suspend fun construct(list: List<BookmarkViewModel>): String {
        val colorScheme = themeProvider.colorScheme()
        return parse(bookmarkPageReader.provideHtml()) andBuild {
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
            body {
                val repeatableElement = findId("repeated").removeElement()
                id("content") {
                    list.forEach { (title, url, iconUrl) ->
                        appendChild(repeatableElement.clone {
                            tag("a") { attr("href", url) }
                            tag("img") { attr("src", iconUrl) }
                            id("title") { appendText(title) }
                        })
                    }
                }
            }
        }
    }

    private suspend fun Bookmark.asViewModel(
        folderIconFile: File,
        defaultIconFile: File,
    ): BookmarkViewModel = when (this) {
        is Bookmark.Folder -> createViewModelForFolder(this, folderIconFile)
        is Bookmark.Entry -> createViewModelForBookmark(this, defaultIconFile)
    }

    private suspend fun createViewModelForFolder(
        folder: Bookmark.Folder,
        folderIconFile: File,
    ): BookmarkViewModel {
        val folderPage = createBookmarkPage(folder)
        val url = "$FILE$folderPage"

        return BookmarkViewModel(
            title = folder.title,
            url = url,
            iconUrl = folderIconFile.toString()
        )
    }

    private suspend fun createViewModelForBookmark(
        entry: Bookmark.Entry,
        defaultIconFile: File,
    ): BookmarkViewModel {
        val faviconFile = faviconModel.getFaviconPathForUrl(entry.url)
        val iconUrl = if (faviconFile != null) {
            if (!File(faviconFile).exists()) {
                val defaultFavicon = faviconModel.createDefaultBitmapForTitle(entry.title)
                faviconModel.cacheFaviconForUrl(defaultFavicon, entry.url)
            }

            faviconFile
        } else {
            defaultIconFile
        }

        return BookmarkViewModel(
            title = entry.title,
            url = entry.url,
            iconUrl = iconUrl.toString()
        )
    }

    /**
     * Create the bookmark page file.
     */
    private suspend fun createBookmarkPage(folder: Bookmark.Folder?): File {
        val prefix = if (folder?.title?.isNotBlank() == true) {
            "${folder.title}-"
        } else {
            ""
        }
        val generatedHtml = generatedHtmlDir.file()
        generatedHtml.mkdirs()
        return File(generatedHtml, prefix + FILENAME)
    }

    companion object {

        const val FILENAME = "bookmarks.html"

        private const val FOLDER_ICON = "folder.png"
        private const val DEFAULT_ICON = "default.png"

    }
}
