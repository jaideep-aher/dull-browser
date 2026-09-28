package com.github.jaideepaher.slatebrowser.search.engine

import com.github.jaideepaher.slatebrowser.R

/**
 * The Kagi search engine.
 * TODO: Warn that requires login.
 *
 * See TODO: for the icon.
 */
class KagiSearch : BaseSearchEngine(
    "file:///android_asset/kagi.png",
    "https://kagi.com/search?&q=",
    R.string.search_engine_kagi
)
