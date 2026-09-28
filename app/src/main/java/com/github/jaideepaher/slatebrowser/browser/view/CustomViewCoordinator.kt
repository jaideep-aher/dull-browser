package com.github.jaideepaher.slatebrowser.browser.view

import com.github.jaideepaher.slatebrowser.di.CustomFrame
import android.view.View
import android.widget.FrameLayout
import javax.inject.Inject

/**
 * Handle showing and hiding a custom view within a frame layout.
 */
class CustomViewCoordinator @Inject constructor(
    @CustomFrame private val frameLayout: FrameLayout
) {

    fun showCustomView(view: View) {
        frameLayout.addView(view)
    }

    fun hideCustomView() {
        frameLayout.removeAllViews()
    }

}
