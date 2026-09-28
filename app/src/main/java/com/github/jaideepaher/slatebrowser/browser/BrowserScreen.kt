package com.github.jaideepaher.slatebrowser.browser

import com.github.jaideepaher.slatebrowser.BrowserUiEvent
import com.github.jaideepaher.slatebrowser.browser.compose.BottomTabs
import com.github.jaideepaher.slatebrowser.browser.compose.CustomView
import com.github.jaideepaher.slatebrowser.browser.compose.DesktopTabs
import com.github.jaideepaher.slatebrowser.browser.compose.DrawerTabs
import com.github.jaideepaher.slatebrowser.browser.ui.TabConfiguration
import com.github.jaideepaher.slatebrowser.search.SuggestionsModel
import android.widget.FrameLayout
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow

@Composable
fun BrowserScreen(
    tabConfigurationStateProvider: StateFlow<TabConfiguration?>,
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    browserViewState: BrowserComposeState,
    presenter: BrowserPresenter,
    browserFrameLayout: FrameLayout,
    customFrameLayout: FrameLayout,
    suggestionsModel: SuggestionsModel,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    browserViewState.ephemeral?.let {
        LaunchedEffect(it.message) {
            when (snackbarHostState.showSnackbar(
                message = it.message,
                actionLabel = it.actionLabel,
                duration = SnackbarDuration.Short,
            )) {
                SnackbarResult.Dismissed -> presenter.onEvent(BrowserUiEvent.SnackbarDismissed)
                SnackbarResult.ActionPerformed -> presenter.onEvent(BrowserUiEvent.SnackbarActionPerformed)
            }
        }
    }
    if (browserViewState.showCustomView) {
        CustomView(
            useBlackStatusBarStateFlow,
            browserViewState,
            customFrameLayout,
            snackbarHostState
        )
    } else {
        val tabConfiguration = tabConfigurationStateProvider.collectAsState()
        when (tabConfiguration.value) {
            TabConfiguration.DESKTOP -> DesktopTabs(
                useBlackStatusBarStateFlow,
                browserFrameLayout,
                browserViewState,
                presenter,
                suggestionsModel,
                snackbarHostState
            )

            TabConfiguration.DRAWER_SIDE -> DrawerTabs(
                useBlackStatusBarStateFlow,
                browserFrameLayout,
                browserViewState,
                presenter,
                suggestionsModel,
                snackbarHostState
            )

            TabConfiguration.DRAWER_BOTTOM -> BottomTabs(
                useBlackStatusBarStateFlow,
                browserFrameLayout,
                browserViewState,
                presenter,
                suggestionsModel,
                snackbarHostState
            )

            null -> Unit
        }
    }
}


