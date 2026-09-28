package com.github.jaideepaher.slatebrowser.adblock.siteblock

import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * The page shown in place of a blocked site.
 *
 * Intentionally a dead end: no "continue anyway" link, no settings shortcut, nothing to tap.
 */
object BlockedPage {

    private const val MIME_TYPE = "text/html"
    private const val ENCODING = "utf-8"

    private val EMPTY_RESPONSE_BYTES = ByteArray(0)

    /**
     * A blank response, used for blocked sub-resources where a visible page would make no sense.
     */
    fun emptyResource(): WebResourceResponse = WebResourceResponse(
        MIME_TYPE,
        ENCODING,
        ByteArrayInputStream(EMPTY_RESPONSE_BYTES)
    ).asSuccess()

    /**
     * The full page shown when a blocked site is opened in the address bar.
     */
    fun forHost(host: String, darkTheme: Boolean = false): WebResourceResponse = WebResourceResponse(
        MIME_TYPE,
        ENCODING,
        ByteArrayInputStream(document(host, darkTheme).toByteArray())
    ).asSuccess()

    /**
     * WebView drops a main-frame interception whose status code was left unset and then loads the
     * real site. 200 makes the replacement stick.
     */
    private fun WebResourceResponse.asSuccess(): WebResourceResponse = apply {
        setStatusCodeAndReasonPhrase(200, "OK")
        if (responseHeaders == null) {
            responseHeaders = emptyMap()
        }
    }

    /**
     * Same mark as the launcher icon, one sentence, and the domain, in the app's light or dark
     * palette.
     */
    fun document(host: String, darkTheme: Boolean = false): String {
        val background = if (darkTheme) "#202124" else "#FFFFFF"
        val foreground = if (darkTheme) "#E8EAED" else "#202124"
        val muted = if (darkTheme) "#9AA0A6" else "#5F6368"
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta name="color-scheme" content="${if (darkTheme) "dark" else "light"}">
          <title>Dull Browser</title>
          <style>
            html, body {
              margin: 0;
              height: 100%;
              background: $background;
              color: $foreground;
              font-family: sans-serif;
            }
            main {
              height: 100%;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
              padding: 0 32px;
              text-align: center;
              box-sizing: border-box;
            }
            svg { margin-bottom: 28px; }
            p { font-size: 17px; line-height: 1.45; margin: 0; }
            .host {
              margin-top: 12px;
              font-size: 14px;
              color: $muted;
              word-break: break-all;
            }
          </style>
        </head>
        <body>
          <main>
            <svg viewBox="0 0 64 64" width="72" height="72" aria-hidden="true">
              <circle cx="32" cy="32" r="22" fill="none" stroke="$foreground" stroke-width="2.5"/>
              <line x1="16" y1="32" x2="48" y2="32" stroke="$foreground" stroke-width="2.5"/>
            </svg>
            <p>This site stays closed in Dull Browser.</p>
            <p class="host">${host.escapeHtml()}</p>
          </main>
        </body>
        </html>
        """.trimIndent()
    }

    private fun String.escapeHtml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
