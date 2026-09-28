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
    fun forHost(host: String): WebResourceResponse = WebResourceResponse(
        MIME_TYPE,
        ENCODING,
        ByteArrayInputStream(document(host).toByteArray())
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
     * Same mark as the launcher icon, one sentence, and the domain.
     */
    fun document(host: String) = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>Dull Browser</title>
          <style>
            html, body {
              margin: 0;
              height: 100%;
              background: #F3EFE6;
              color: #1C1917;
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
              color: #6F6A64;
              word-break: break-all;
            }
          </style>
        </head>
        <body>
          <main>
            <svg viewBox="0 0 64 64" width="72" height="72" aria-hidden="true">
              <circle cx="32" cy="32" r="22" fill="none" stroke="#1C1917" stroke-width="2.5"/>
              <line x1="16" y1="32" x2="48" y2="32" stroke="#1C1917" stroke-width="2.5"/>
            </svg>
            <p>This site stays closed in Dull Browser.</p>
            <p class="host">${host.escapeHtml()}</p>
          </main>
        </body>
        </html>
    """.trimIndent()

    private fun String.escapeHtml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
