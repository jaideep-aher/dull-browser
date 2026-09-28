package com.github.jaideepaher.slatebrowser.extensions

import com.github.jaideepaher.slatebrowser.dialog.BrowserDialog
import android.app.Dialog
import androidx.appcompat.app.AlertDialog

/**
 * Ensures that the dialog is appropriately sized and displays it.
 */
@Suppress("NOTHING_TO_INLINE")
inline fun AlertDialog.Builder.resizeAndShow(): Dialog =
    show().also { BrowserDialog.setDialogSize(context, it) }
