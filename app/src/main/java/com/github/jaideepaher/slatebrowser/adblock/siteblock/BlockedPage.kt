package com.github.jaideepaher.slatebrowser.adblock.siteblock

import com.github.jaideepaher.slatebrowser.focus.SiteName
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * The page shown in place of a blocked site.
 *
 * Intentionally a dead end: no "continue anyway" link. The actions only go somewhere else,
 * through the `dull://blocked/` scheme handled by the tab client.
 */
object BlockedPage {

    const val SCHEME = "dull"
    const val BACK = "dull://blocked/back"
    const val LATER = "dull://blocked/later"
    const val HOME = "dull://blocked/home"
    const val DISMISS_MILESTONE = "dull://focus/dismiss-milestone"

    private const val MIME_TYPE = "text/html"
    private const val ENCODING = "utf-8"

    private val EMPTY_RESPONSE_BYTES = ByteArray(0)

    fun emptyResource(): WebResourceResponse = WebResourceResponse(
        MIME_TYPE,
        ENCODING,
        ByteArrayInputStream(EMPTY_RESPONSE_BYTES)
    ).asSuccess()

    fun forHost(
        host: String,
        darkTheme: Boolean = false,
        attempts: Int = 0,
        site: String? = null,
        note: String = "",
        showReadLater: Boolean = true,
    ): WebResourceResponse = WebResourceResponse(
        MIME_TYPE,
        ENCODING,
        ByteArrayInputStream(document(host, darkTheme, attempts, site, note, showReadLater).toByteArray())
    ).asSuccess()

    private fun WebResourceResponse.asSuccess(): WebResourceResponse = apply {
        setStatusCodeAndReasonPhrase(200, "OK")
        if (responseHeaders == null) {
            responseHeaders = emptyMap()
        }
    }

    fun document(
        host: String,
        darkTheme: Boolean = false,
        attempts: Int = 0,
        site: String? = null,
        note: String = "",
        showReadLater: Boolean = true,
    ): String {
        val background = if (darkTheme) "#202124" else "#FFFFFF"
        val foreground = if (darkTheme) "#E8EAED" else "#202124"
        val muted = if (darkTheme) "#9AA0A6" else "#5F6368"
        val attemptsLine = when {
            site == null || attempts <= 0 -> ""
            attempts == 1 -> "<p class=\"attempts\">You tried ${SiteName.display(site).escapeHtml()} once today.</p>"
            else -> "<p class=\"attempts\">You tried ${SiteName.display(site).escapeHtml()} $attempts times today.</p>"
        }
        val trimmed = note.trim()
        val noteLine = if (trimmed.isEmpty()) {
            ""
        } else {
            "<p class=\"note\">${trimmed.escapeHtml()}</p>"
        }
        val later = if (showReadLater) {
            """<a href="$LATER">Read later</a>"""
        } else {
            ""
        }
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
            .attempts {
              margin-top: 20px;
              font-size: 15px;
              color: $muted;
            }
            .note {
              margin-top: 24px;
              font-size: 16px;
              font-style: italic;
              font-family: serif;
            }
            nav {
              margin-top: 36px;
              display: flex;
              gap: 10px;
              flex-wrap: wrap;
              justify-content: center;
            }
            a {
              font-size: 15px;
              color: $foreground;
              text-decoration: none;
              padding: 0 14px;
              height: 38px;
              line-height: 38px;
              border: 1px solid ${if (darkTheme) "#5F6368" else "#2021244D"};
              border-radius: 19px;
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
            $attemptsLine
            $noteLine
            <nav>
              <a href="$BACK">Go back</a>
              $later
              <a href="$HOME">Start page</a>
            </nav>
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
