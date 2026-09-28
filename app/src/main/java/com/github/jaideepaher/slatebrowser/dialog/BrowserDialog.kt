/*
 * Copyright 7/31/2016 Anthony Restaino
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jaideepaher.slatebrowser.dialog

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.extensions.dimen
import com.github.jaideepaher.slatebrowser.extensions.resizeAndShow
import com.github.jaideepaher.slatebrowser.utils.DeviceUtils
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

object BrowserDialog {

    @JvmStatic
    fun showPositiveNegativeDialog(
        activity: Activity,
        @StringRes title: Int,
        @StringRes message: Int,
        messageArguments: Array<Any>? = null,
        positiveButton: DialogItem,
        negativeButton: DialogItem,
        onCancel: () -> Unit
    ) {
        val messageValue = if (messageArguments != null) {
            activity.getString(message, *messageArguments)
        } else {
            activity.getString(message)
        }
        AlertDialog.Builder(activity).apply {
            setTitle(title)
            setMessage(messageValue)
            setOnCancelListener { onCancel() }
            setPositiveButton(positiveButton.title) { _, _ -> positiveButton.onClick() }
            setNegativeButton(negativeButton.title) { _, _ -> negativeButton.onClick() }
        }.resizeAndShow()
    }

    @JvmStatic
    fun setDialogSize(context: Context, dialog: Dialog) {
        var maxWidth = context.dimen(R.dimen.dialog_max_size)
        val padding = context.dimen(R.dimen.dialog_padding)
        val screenSize = DeviceUtils.getScreenWidth(context)
        if (maxWidth > screenSize - 2 * padding) {
            maxWidth = screenSize - 2 * padding
        }
        dialog.window?.setLayout(maxWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

}
