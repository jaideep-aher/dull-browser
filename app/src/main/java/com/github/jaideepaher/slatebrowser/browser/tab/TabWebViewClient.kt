package com.github.jaideepaher.slatebrowser.browser.tab

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.adblock.AdBlocker
import com.github.jaideepaher.slatebrowser.adblock.allowlist.AllowListModel
import com.github.jaideepaher.slatebrowser.adblock.siteblock.BlockedPage
import com.github.jaideepaher.slatebrowser.adblock.siteblock.NavigationHops
import com.github.jaideepaher.slatebrowser.adblock.siteblock.SiteBlocker
import com.github.jaideepaher.slatebrowser.browser.tab.settings.TabSettings
import com.github.jaideepaher.slatebrowser.focus.Feature
import com.github.jaideepaher.slatebrowser.focus.FocusClock
import com.github.jaideepaher.slatebrowser.focus.FocusCoordinator
import com.github.jaideepaher.slatebrowser.focus.PauseList
import com.github.jaideepaher.slatebrowser.focus.PauseRequest
import com.github.jaideepaher.slatebrowser.focus.PauseRules
import com.github.jaideepaher.slatebrowser.focus.ReadLater
import com.github.jaideepaher.slatebrowser.focus.Stats
import com.github.jaideepaher.slatebrowser.html.homepage.HomePageFactory
import com.github.jaideepaher.slatebrowser.concurrency.TabCoroutineScope
import com.github.jaideepaher.slatebrowser.databinding.DialogAuthRequestBinding
import com.github.jaideepaher.slatebrowser.databinding.DialogSslWarningBinding
import com.github.jaideepaher.slatebrowser.di.FaviconCacheDir
import com.github.jaideepaher.slatebrowser.di.GeneratedHtmlDir
import com.github.jaideepaher.slatebrowser.extensions.resizeAndShow
import com.github.jaideepaher.slatebrowser.js.TextReflow
import com.github.jaideepaher.slatebrowser.log.Logger
import com.github.jaideepaher.slatebrowser.ssl.SslState
import com.github.jaideepaher.slatebrowser.ssl.SslWarningPreferences
import com.github.jaideepaher.slatebrowser.theme.ThemeProvider
import com.github.jaideepaher.slatebrowser.utils.ThreadSafeFileProvider
import com.github.jaideepaher.slatebrowser.utils.isSpecialUrl
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.LayoutInflater
import android.webkit.HttpAuthHandler
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.webkit.WebViewAssetLoader.InternalStoragePathHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import kotlin.math.abs

/**
 * A [WebViewClient] that supports the tab adaptation.
 */
class TabWebViewClient @AssistedInject constructor(
    private val adBlocker: Deferred<@JvmSuppressWildcards AdBlocker>,
    private val siteBlocker: Deferred<@JvmSuppressWildcards SiteBlocker>,
    private val allowListModel: AllowListModel,
    private val urlHandler: UrlHandler,
    @Assisted private val headers: Map<String, String>,
    private val sslWarningPreferences: SslWarningPreferences,
    private val textReflow: TextReflow,
    private val themeProvider: ThemeProvider,
    private val logger: Logger,
    @FaviconCacheDir private val faviconCacheDirThreadSafeFileProvider: ThreadSafeFileProvider,
    @GeneratedHtmlDir private val generatedHtmlDirThreadSafeFileProvider: ThreadSafeFileProvider,
    @Assisted("cache") private val cacheStoragePathHandler: InternalStoragePathHandler,
    @Assisted("files") private val filesStoragePathHandler: InternalStoragePathHandler,
    @Assisted private val tabCoroutineScope: TabCoroutineScope,
    @Assisted private val tabSettings: TabSettings,
    private val pauseList: PauseList,
    private val stats: Stats,
    private val readLater: ReadLater,
    private val focusCoordinator: FocusCoordinator,
    private val clock: FocusClock,
    private val homePageFactory: HomePageFactory,
) : WebViewClient() {

    /**
     * The factory for constructing the client.
     */
    @AssistedFactory
    interface Factory {

        /**
         * Create the client.
         */
        fun create(
            headers: Map<String, String>,
            @Assisted("cache") cacheStoragePathHandler: InternalStoragePathHandler,
            @Assisted("files") filesStoragePathHandler: InternalStoragePathHandler,
            tabCoroutineScope: TabCoroutineScope,
            tabSettings: TabSettings,
        ): TabWebViewClient
    }

    private val cache by lazy {
        runBlocking {
            faviconCacheDirThreadSafeFileProvider.file()
        }
    }

    private val files by lazy {
        runBlocking {
            generatedHtmlDirThreadSafeFileProvider.file()
        }
    }

    /**
     * Emits changes to the current URL.
     */
    val urlSharedFlow: MutableSharedFlow<String> = MutableSharedFlow()

    /**
     * The current SSL state of the page.
     */
    val sslStateFlow: MutableStateFlow<SslState> = MutableStateFlow(SslState.None)

    /**
     * Emits changes to the can go back state of the browser.
     */
    val goBackSharedFlow: MutableSharedFlow<Boolean> = MutableSharedFlow()

    /**
     * Emits changes to the can go forward state of the browser.
     */
    val goForwardSharedFlow: MutableSharedFlow<Boolean> = MutableSharedFlow()

    /**
     * Emit when the tab has started loading a new page
     */
    val startedSharedFlow = MutableSharedFlow<Unit>()

    /**
     * Emit when the tab has finished rendering its content.
     */
    val finishedSharedFlow = MutableSharedFlow<Unit>()

    /**
     * The latest search query entered by the user, or the latest loaded URL, whichever event
     * happened later.
     */
    var searchQuery: String = ""

    /**
     * The text selection in the search query, either the start and end of the selection if the
     * values are different, or the cursor position if they are the same.
     */
    var searchQuerySelection: Pair<Int, Int> = Pair(0, 0)

    private var currentUrl: String = ""

    /** Host just replaced, so the following start event does not replace it again. */
    private var suppressedHost: String? = null

    /** Bumps on each load so a late DNS answer is dropped. */
    private var navigationToken = 0

    /** Stops the committed-page replacement from loading the blocked document in a loop. */
    private var lastBlockHost: String? = null
    private var lastBlockAt = 0L
    private val pauseGrace = mutableMapOf<String, java.time.Instant>()
    val pauseRequestFlow = MutableStateFlow<PauseRequest?>(null)
    private var isReflowRunning: Boolean = false
    private var zoomScale: Float = 0.0F
    private var urlWithSslError: String? = null

    @Volatile
    private var darkTheme: Boolean = false

    init {
        tabCoroutineScope.launch {
            themeProvider.appThemeValues().collect {
                darkTheme = themeProvider.isDarkTheme()
            }
        }
    }

    private fun shouldBlockRequest(pageUrl: String, requestUri: Uri) =
        !allowListModel.isUrlAllowedAds(pageUrl) &&
            runBlocking { adBlocker.await().isAd(requestUri) }

    private fun isSiteBlocked(requestUri: Uri, consultResolver: Boolean = false) =
        runBlocking { siteBlocker.await().isBlocked(requestUri, consultResolver) }

    /**
     * The host that should be refused, when this navigation or a hop inside it is blocked.
     */
    private fun blockedHost(uri: Uri, consultResolver: Boolean): String? {
        if (isSiteBlocked(uri, consultResolver)) {
            return uri.host.orEmpty()
        }
        // Also any host hidden in the link, such as a search-result redirect.
        for (hop in NavigationHops.extract(uri.toString())) {
            val hopUri = Uri.parse(hop)
            if (hopUri.host.equals(uri.host, ignoreCase = true) &&
                hopUri.scheme.equals(uri.scheme, ignoreCase = true)
            ) {
                continue
            }
            if (isSiteBlocked(hopUri, consultResolver = false)) {
                return hopUri.host?.takeIf { it.isNotEmpty() } ?: uri.host
            }
        }
        return null
    }

    fun takeContinueUrl(): String? {
        val request = pauseRequestFlow.value ?: return null
        if (!request.isReady(clock.now())) return null
        stats.recordPause(wentBack = false)
        pauseGrace[request.site] = PauseRules.graceUntil(clock.now())
        pauseRequestFlow.value = null
        return request.url
    }

    fun leavePause() {
        if (pauseRequestFlow.value == null) return
        stats.recordPause(wentBack = true)
        pauseRequestFlow.value = null
    }

    private fun handleBlockedAction(view: WebView, uri: Uri): Boolean {
        if (uri.scheme != BlockedPage.SCHEME) return false
        when (uri.toString()) {
            BlockedPage.BACK -> {
                if (view.canGoBack()) view.goBack() else goHome(view)
            }
            BlockedPage.LATER -> {
                val url = currentUrl.takeIf { it.startsWith("http") } ?: view.url.orEmpty()
                if (Feature.READ_LATER.isUnlocked) {
                    readLater.add(url, view.title.orEmpty())
                }
                if (view.canGoBack()) view.goBack() else goHome(view)
            }
            BlockedPage.HOME -> goHome(view)
            BlockedPage.DISMISS_MILESTONE -> {
                stats.dismissMilestoneNotice()
                goHome(view)
            }
            else -> return false
        }
        return true
    }

    private fun goHome(view: WebView) {
        tabCoroutineScope.launch {
            val page = homePageFactory.buildPage()
            view.loadUrl(page)
        }
    }

    private fun pauseIfNeeded(view: WebView, uri: Uri): Boolean {
        if (!Feature.MINDFUL_PAUSE.isUnlocked) return false
        val host = uri.host ?: return false
        val site = pauseList.site(host) ?: return false
        val now = clock.now()
        if (PauseRules.inGrace(pauseGrace[site], now)) return false
        pauseGrace.remove(site)
        val earlier = stats.pausesToday(site)
        stats.recordPauseShown(site)
        pauseRequestFlow.value = PauseRequest(
            url = uri.toString(),
            site = site,
            delaySeconds = PauseRules.delay(earlier),
            shownAt = now,
        )
        view.stopLoading()
        tabCoroutineScope.launch { urlSharedFlow.emit(uri.toString()) }
        return true
    }

    /** Replaces a page that already committed on a blocked host. */
    private fun replaceCommitted(view: WebView, url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val listed = blockedHost(uri, consultResolver = false)
        if (listed != null) {
            showBlockedPage(view, listed)
            return true
        }
        if (pauseIfNeeded(view, uri)) return true
        val token = navigationToken
        val captured = url
        tabCoroutineScope.launch {
            // Off the main thread. A lookup there fails open.
            val resolved = withContext(Dispatchers.IO) {
                blockedHost(Uri.parse(captured), consultResolver = true)
            }
            if (resolved != null) {
                view.post {
                    if (token == navigationToken && view.url == captured) {
                        showBlockedPage(view, resolved)
                    }
                }
            }
        }
        return false
    }

    private fun showBlockedPage(view: WebView, host: String) {
        val key = host.lowercase()
        val now = android.os.SystemClock.uptimeMillis()
        if (lastBlockHost == key && now - lastBlockAt < BLOCK_DEBOUNCE_MS) return
        lastBlockHost = key
        lastBlockAt = now
        logger.log(TAG, "Blocked navigation to $host")
        pauseRequestFlow.value = null
        val listed = runBlocking { siteBlocker.await().listedDomain(host) } ?: key
        stats.recordBlocked(listed)
        suppressedHost = key
        view.stopLoading()
        val pageUrl = "https://$key/"
        view.loadDataWithBaseURL(
            pageUrl,
            blockedDocument(host, listed),
            "text/html",
            "utf-8",
            pageUrl
        )
    }

    private fun blockedDocument(host: String, listed: String): String =
        BlockedPage.document(
            host = host,
            darkTheme = darkTheme,
            attempts = stats.blockedToday(listed),
            site = listed,
            note = if (Feature.BLOCKED_PAGE_NOTE.isUnlocked) focusCoordinator.blockedNote else "",
            showReadLater = Feature.READ_LATER.isUnlocked,
        )

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        navigationToken++
        val uri = Uri.parse(url)
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()?.trimEnd('.')
        if (host != null && host == suppressedHost) {
            suppressedHost = null
        } else if (scheme == "http" || scheme == "https") {
            suppressedHost = null
            val listed = blockedHost(uri, consultResolver = false)
            if (listed == null && !pauseIfNeeded(view, uri)) {
                val captured = url
                val token = navigationToken
                tabCoroutineScope.launch {
                    val resolved = withContext(Dispatchers.IO) {
                        blockedHost(Uri.parse(captured), consultResolver = true)
                    }
                    if (resolved != null) {
                        view.post {
                            if (token == navigationToken && (view.url == null || view.url == captured)) {
                                showBlockedPage(view, resolved)
                            }
                        }
                    }
                }
            }
        }
        super.onPageStarted(view, url, favicon)
        searchQuery = if (!url.isSpecialUrl()) {
            url
        } else {
            ""
        }
        searchQuerySelection = Pair(0, searchQuery.length)
        currentUrl = url
        tabCoroutineScope.launch {
            startedSharedFlow.emit(Unit)
            urlSharedFlow.emit(url)
            if (urlWithSslError != url) {
                urlWithSslError = null
                val sslState = if (URLUtil.isHttpsUrl(url)) {
                    SslState.Valid
                } else {
                    SslState.None
                }
                sslStateFlow.emit(sslState)
            }
        }
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        // A redirect from a search result often shows up here first.
        if (replaceCommitted(view, url)) return
        super.onPageCommitVisible(view, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (replaceCommitted(view, url)) return
        super.onPageFinished(view, url)
        tabCoroutineScope.launch {
            urlSharedFlow.emit(url)
            goBackSharedFlow.emit(view.canGoBack())
            goForwardSharedFlow.emit(view.canGoForward())
        }
        view.postVisualStateCallback(1, object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                tabCoroutineScope.launch {
                    finishedSharedFlow.emit(Unit)
                }
            }
        })
    }


    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        if (view.isShown && tabSettings.textReflowEnabled) {
            if (isReflowRunning)
                return
            val changeInPercent = abs(100 - 100 / zoomScale * newScale)
            if (changeInPercent > 2.5f && !isReflowRunning) {
                isReflowRunning = view.postDelayed({
                    zoomScale = newScale
                    view.evaluateJavascript(textReflow.provideJs()) { isReflowRunning = false }
                }, 100)
            }

        }
    }

    override fun onReceivedHttpAuthRequest(
        view: WebView,
        handler: HttpAuthHandler,
        host: String,
        realm: String
    ) {
        val context = view.context
        AlertDialog.Builder(context).apply {
            val dialogView = DialogAuthRequestBinding.inflate(LayoutInflater.from(context))

            val realmLabel = dialogView.authRequestRealmTextview
            val name = dialogView.authRequestUsernameEdittext
            val password = dialogView.authRequestPasswordEdittext

            realmLabel.text = context.getString(R.string.label_realm, realm)

            setView(dialogView.root)
            setTitle(R.string.title_sign_in)
            setCancelable(true)
            setPositiveButton(R.string.title_sign_in) { _, _ ->
                val user = name.text.toString()
                val pass = password.text.toString()
                handler.proceed(user.trim(), pass.trim())
                logger.log(TAG, "Attempting HTTP Authentication")
            }
            setNegativeButton(R.string.action_cancel) { _, _ ->
                handler.cancel()
            }
        }.resizeAndShow()
    }

    override fun onFormResubmission(view: WebView, dontResend: Message, resend: Message) {
        val context = view.context
        AlertDialog.Builder(context).apply {
            setTitle(context.getString(R.string.title_form_resubmission))
            setMessage(context.getString(R.string.message_form_resubmission))
            setCancelable(true)
            setPositiveButton(context.getString(R.string.action_yes)) { _, _ ->
                resend.sendToTarget()
            }
            setNegativeButton(context.getString(R.string.action_no)) { _, _ ->
                dontResend.sendToTarget()
            }
        }.resizeAndShow()
    }

    @SuppressLint("WebViewClientOnReceivedSslError")
    override fun onReceivedSslError(webView: WebView, handler: SslErrorHandler, error: SslError) {
        val context = webView.context
        urlWithSslError = webView.url

        tabCoroutineScope.launch {
            val sslState = SslState.Invalid(error)
            sslStateFlow.emit(sslState)
        }

        when (sslWarningPreferences.recallBehaviorForDomain(webView.url)) {
            SslWarningPreferences.Behavior.PROCEED -> return handler.proceed()
            SslWarningPreferences.Behavior.CANCEL -> return handler.cancel()
            null -> Unit
        }

        val errorCodeMessageCodes = error.getAllSslErrorMessageCodes()

        val stringBuilder = StringBuilder()
        for (messageCode in errorCodeMessageCodes) {
            stringBuilder.append(" - ").append(context.getString(messageCode)).append('\n')
        }
        val alertMessage =
            context.getString(R.string.message_insecure_connection, stringBuilder.toString())

        AlertDialog.Builder(context).apply {
            val view = DialogSslWarningBinding.inflate(LayoutInflater.from(context))
            val dontAskAgain = view.checkBoxDontAskAgain
            setTitle(context.getString(R.string.title_warning))
            setMessage(alertMessage)
            setCancelable(true)
            setView(view.root)
            setOnCancelListener { handler.cancel() }
            setPositiveButton(context.getString(R.string.action_yes)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    sslWarningPreferences.rememberBehaviorForDomain(
                        webView.url.orEmpty(),
                        SslWarningPreferences.Behavior.PROCEED
                    )
                }
                handler.proceed()
            }
            setNegativeButton(context.getString(R.string.action_no)) { _, _ ->
                if (dontAskAgain.isChecked) {
                    sslWarningPreferences.rememberBehaviorForDomain(
                        webView.url.orEmpty(),
                        SslWarningPreferences.Behavior.CANCEL
                    )
                }
                handler.cancel()
            }
        }.resizeAndShow()
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (handleBlockedAction(view, request.url)) return true
        val blocked = blockedHost(request.url, consultResolver = false)
        if (blocked != null) {
            showBlockedPage(view, blocked)
            return true
        }
        if (request.isForMainFrame && pauseIfNeeded(view, request.url)) return true
        return urlHandler.shouldOverrideLoading(
            tabSettings.openAvailableAppsEnabled,
            view,
            request.url.toString(),
            headers
        ) || super.shouldOverrideUrlLoading(view, request)
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        val blockedHost = if (request.isForMainFrame) {
            blockedHost(request.url, consultResolver = true)
        } else if (isSiteBlocked(request.url)) {
            request.url.host
        } else {
            null
        }
        if (blockedHost != null) {
            return if (request.isForMainFrame) {
                logger.log(TAG, "Blocked page load for $blockedHost")
                val listed = runBlocking { siteBlocker.await().listedDomain(blockedHost) } ?: blockedHost
                val key = blockedHost.lowercase()
                val now = android.os.SystemClock.uptimeMillis()
                if (lastBlockHost != key || now - lastBlockAt >= BLOCK_DEBOUNCE_MS) {
                    lastBlockHost = key
                    lastBlockAt = now
                    stats.recordBlocked(listed)
                }
                BlockedPage.forHost(
                    host = blockedHost,
                    darkTheme = darkTheme,
                    attempts = stats.blockedToday(listed),
                    site = listed,
                    note = if (Feature.BLOCKED_PAGE_NOTE.isUnlocked) focusCoordinator.blockedNote else "",
                    showReadLater = Feature.READ_LATER.isUnlocked,
                )
            } else {
                BlockedPage.emptyResource()
            }
        }
        if (request.isForMainFrame && Feature.MINDFUL_PAUSE.isUnlocked) {
            val site = pauseList.site(request.url.host)
            if (site != null && !PauseRules.inGrace(pauseGrace[site], clock.now())) {
                return BlockedPage.emptyResource()
            }
        }
        if (shouldBlockRequest(currentUrl, request.url)) {
            val empty = ByteArrayInputStream(emptyResponseByteArray)
            return WebResourceResponse(BLOCKED_RESPONSE_MIME_TYPE, BLOCKED_RESPONSE_ENCODING, empty)
        }
        return if (request.url.path?.startsWith(files.path) == true) {
            filesStoragePathHandler.handle(request.url.path!!.substring(files.path.length))
        } else if (request.url.path?.startsWith(cache.path) == true) {
            cacheStoragePathHandler.handle(request.url.path!!.substring(cache.path.length))
        } else {
            super.shouldInterceptRequest(view, request)
        }
    }

    private fun SslError.getAllSslErrorMessageCodes(): List<Int> {
        val errorCodeMessageCodes = ArrayList<Int>(1)

        if (hasError(SslError.SSL_DATE_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_date_invalid)
        }
        if (hasError(SslError.SSL_EXPIRED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_expired)
        }
        if (hasError(SslError.SSL_IDMISMATCH)) {
            errorCodeMessageCodes.add(R.string.message_certificate_domain_mismatch)
        }
        if (hasError(SslError.SSL_NOTYETVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_not_yet_valid)
        }
        if (hasError(SslError.SSL_UNTRUSTED)) {
            errorCodeMessageCodes.add(R.string.message_certificate_untrusted)
        }
        if (hasError(SslError.SSL_INVALID)) {
            errorCodeMessageCodes.add(R.string.message_certificate_invalid)
        }

        return errorCodeMessageCodes
    }

    companion object {
        private const val TAG = "TabWebViewClient"
        private const val BLOCK_DEBOUNCE_MS = 2_000L

        private val emptyResponseByteArray: ByteArray = byteArrayOf()

        private const val BLOCKED_RESPONSE_MIME_TYPE = "text/plain"
        private const val BLOCKED_RESPONSE_ENCODING = "utf-8"
    }
}
