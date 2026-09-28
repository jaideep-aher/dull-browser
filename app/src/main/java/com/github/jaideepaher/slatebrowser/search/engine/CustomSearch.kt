package com.github.jaideepaher.slatebrowser.search.engine

import com.github.jaideepaher.slatebrowser.R

/**
 * A custom search engine.
 */
class CustomSearch(queryUrl: String) : BaseSearchEngine(
    "file:///android_asset/lightning.png",
    queryUrl,
    R.string.search_engine_custom
)
