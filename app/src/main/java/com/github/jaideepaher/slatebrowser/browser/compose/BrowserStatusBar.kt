package com.github.jaideepaher.slatebrowser.browser.compose

import com.github.jaideepaher.slatebrowser.browser.BrowserComposeState
import com.github.jaideepaher.slatebrowser.browser.BrowserViewState
import com.github.jaideepaher.slatebrowser.compose.StatusBar
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BrowserStatusBar(
    browserComposeState: BrowserComposeState,
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
) {
    StatusBar(
        paintSurfaceColor = browserComposeState.toolbarVisibility != BrowserViewState.ToolbarVisibility.FIXED,
        useBlackStatusBarStateFlow = useBlackStatusBarStateFlow,
    )
}
