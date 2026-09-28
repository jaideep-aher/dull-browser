package com.github.jaideepaher.slatebrowser.browser.compose

import com.github.jaideepaher.slatebrowser.BrowserUiEvent
import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.browser.BrowserComposeState
import com.github.jaideepaher.slatebrowser.browser.BrowserPresenter
import com.github.jaideepaher.slatebrowser.browser.compose.sheets.BookmarksBottomSheet
import com.github.jaideepaher.slatebrowser.search.SuggestionsModel
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomTabs(
    useBlackStatusBarStateFlow: StateFlow<Boolean?>,
    frameLayout: FrameLayout,
    browserViewState: BrowserComposeState,
    presenter: BrowserPresenter,
    suggestionsModel: SuggestionsModel,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(56.dp)
            )
        }
    ) { innerPadding ->
        BrowserStatusBar(
            browserComposeState = browserViewState,
            useBlackStatusBarStateFlow = useBlackStatusBarStateFlow,
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BookmarksBottomSheet(browserViewState, presenter)
            TabNavigationBar(browserViewState, presenter, suggestionsModel)
            BrowserFindInPage(browserViewState, presenter)
            AndroidView(
                factory = { frameLayout },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceDim)
                    .weight(1f, false),
            )
            TabsBottomSheet(browserViewState, presenter)
            BrowserDialogs(browserViewState, presenter)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabNavigationBar(
    browserViewState: BrowserComposeState,
    presenter: BrowserPresenter,
    suggestionsModel: SuggestionsModel,
) {
    Column(
        modifier = Modifier.height(56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrowserSearchBar(browserViewState, presenter, suggestionsModel)
            TabCountButton(browserViewState) {
                presenter.onEvent(BrowserUiEvent.TabCountClick)
            }
            BrowserOverflowMenu(presenter, browserViewState)
        }
        // Divider and progress sit under the row so they separate the bar from the page.
        Box(
            contentAlignment = Alignment.BottomCenter
        ) {
            HorizontalDivider()
            BrowserProgressIndicator(browserViewState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsBottomSheet(
    browserViewState: BrowserComposeState,
    presenter: BrowserPresenter,
) {
    val lazyListState = rememberLazyListState()
    if (browserViewState.scrollToTab != -1) {
        LaunchedEffect(browserViewState.scrollToTab) {
            lazyListState.scrollToItem(browserViewState.scrollToTab)
            presenter.onEvent(BrowserUiEvent.TabScroll)
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(browserViewState.openTabs) }
    if (showBottomSheet != browserViewState.openTabs) {
        if (showBottomSheet) {
            LaunchedEffect(null) {
                sheetState.hide()
                showBottomSheet = false
            }
        } else {
            showBottomSheet = true
            LaunchedEffect(null) {
                sheetState.show()
            }
        }
    }
    if (!showBottomSheet) return
    ModalBottomSheet(
        dragHandle = {},
        sheetState = sheetState,
        onDismissRequest = { presenter.onEvent(BrowserUiEvent.TabDrawerMoved(isOpen = false)) }
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            IconButton(
                enabled = browserViewState.isBackEnabled,
                onClick = { presenter.onEvent(BrowserUiEvent.BackClick) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_back),
                    contentDescription = ""
                )
            }
            IconButton(
                enabled = browserViewState.isForwardEnabled,
                onClick = { presenter.onEvent(BrowserUiEvent.ForwardClick) }
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_forward),
                    contentDescription = ""
                )
            }
            IconButton(onClick = { presenter.onEvent(BrowserUiEvent.HomeClick) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_home),
                    contentDescription = ""
                )
            }
            IconButton(onClick = { presenter.onEvent(BrowserUiEvent.ToolsClick) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_page_tools),
                    contentDescription = ""
                )
            }
            IconButton(
                enabled = browserViewState.isBookmarkEnabled,
                onClick = { presenter.onEvent(BrowserUiEvent.StarClick) }
            ) {
                BookmarkIcon(browserViewState.isBookmarked)
            }
            IconButton(onClick = { presenter.onEvent(BrowserUiEvent.NewTabClick) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_action_plus),
                    contentDescription = ""
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            state = lazyListState,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            itemsIndexed(
                items = browserViewState.tabs,
                key = { _, item -> item.id },
                contentType = { _, item -> item.isSelected },
            ) { index, tab ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .animateItem(
                            fadeInSpec = null,
                            fadeOutSpec = null
                        )
                        .combinedClickable(
                            onClick = { presenter.onEvent(BrowserUiEvent.TabClick(index)) },
                            onLongClick = { presenter.onEvent(BrowserUiEvent.TabLongClick(index)) }
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (tab.isSelected) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        text = tab.title
                    )
                    IconButton(
                        modifier = Modifier.size(36.dp),
                        onClick = { presenter.onEvent(BrowserUiEvent.TabClose(index)) }
                    ) {
                        Icon(
                            modifier = Modifier.size(20.dp),
                            painter = painterResource(R.drawable.ic_action_delete),
                            contentDescription = stringResource(R.string.close_tab)
                        )
                    }
                }
            }
        }
    }
}
