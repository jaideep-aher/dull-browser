package com.github.jaideepaher.slatebrowser.settings.screens

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkExporter
import com.github.jaideepaher.slatebrowser.database.bookmark.BookmarkRepository
import com.github.jaideepaher.slatebrowser.focus.Bookmarks
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableOnClick
import com.github.jaideepaher.slatebrowser.settings.framework.ClickableState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsBottomSheetChooserState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsDialogConfirmationState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsSnackBarState
import javax.inject.Inject

class BookmarkSettingsScreen @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val bookmarkExporter: BookmarkExporter,
    private val bookmarkRepository: BookmarkRepository,
    private val focusBookmarks: Bookmarks,
) {
    fun createSettingsFrameworkState(): SettingsFrameworkState = SettingsFrameworkState(
        title = resourceProvider.stringResource(R.string.bookmark_settings),
        content = listOf(
            ClickableState(
                title = resourceProvider.stringResource(R.string.settings_quick_links),
                summary = { focusBookmarks.quickLinkCount.toString() },
                onClick = ClickableOnClick.ItemSelector(
                    produceState = {
                        SettingsBottomSheetChooserState(
                            title = resourceProvider.stringResource(R.string.settings_quick_links),
                            values = (0..Bookmarks.MAX_QUICK_LINKS).map { it.toString() },
                            selected = focusBookmarks.quickLinkCount,
                        )
                    },
                    onSelected = { count ->
                        ClickableOnClick.Action {
                            focusBookmarks.quickLinkCount = count
                        }
                    }
                )
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.export_bookmarks),
                onClick = ClickableOnClick.FileCreator(
                    fileName = "ExportedBookmarks.txt",
                    onCreated = {
                        if (it != null) {
                            ClickableOnClick.Snackbar {
                                val exportFile = bookmarkExporter.exportBookmarksToUri(it)
                                if (exportFile == null) {
                                    SettingsSnackBarState(
                                        resourceProvider.stringResource(R.string.bookmark_export_failure)
                                    )
                                } else {
                                    SettingsSnackBarState(
                                        resourceProvider.stringResource(
                                            R.string.bookmark_export_path
                                        ) + " $exportFile"
                                    )
                                }
                            }
                        } else {
                            ClickableOnClick.Snackbar {
                                SettingsSnackBarState(resourceProvider.stringResource(R.string.action_message_canceled))
                            }
                        }
                    }
                )
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.import_backup),
                onClick = ClickableOnClick.FileChooser(
                    mimeType = "text/*",
                    onSelected = {
                        if (it != null) {
                            ClickableOnClick.Snackbar {
                                val imported = bookmarkExporter.importBookmarksFromUri(it)
                                if (imported == null) {
                                    SettingsSnackBarState(
                                        resourceProvider.stringResource(R.string.import_bookmark_error)
                                    )
                                } else {
                                    SettingsSnackBarState(
                                        "${imported.size} " + resourceProvider.stringResource(
                                            R.string.message_import
                                        )
                                    )
                                }
                            }
                        } else {
                            ClickableOnClick.Snackbar {
                                SettingsSnackBarState(resourceProvider.stringResource(R.string.action_message_canceled))
                            }
                        }
                    }
                )
            ),
            ClickableState(
                title = resourceProvider.stringResource(R.string.action_delete_all_bookmarks),
                onClick = ClickableOnClick.Confirmation(
                    produceState = {
                        SettingsDialogConfirmationState(
                            title = resourceProvider.stringResource(R.string.action_delete),
                            message = resourceProvider.stringResource(R.string.action_delete_all_bookmarks),
                            negativeAction = resourceProvider.stringResource(R.string.no),
                            positiveAction = resourceProvider.stringResource(R.string.yes),
                        )
                    },
                    onConfirmed = {
                        ClickableOnClick.Action {
                            if (it) {
                                bookmarkRepository.deleteAllBookmarks()
                            }
                        }
                    }
                )
            )
        )
    )
}
