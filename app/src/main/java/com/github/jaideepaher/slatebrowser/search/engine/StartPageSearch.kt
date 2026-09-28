package com.github.jaideepaher.slatebrowser.search.engine

import com.github.jaideepaher.slatebrowser.R

/**
 * The StartPage search engine.
 */
class StartPageSearch : BaseSearchEngine(
    "file:///android_asset/startpage.png",
    "https://startpage.com/do/search?language=english&query=",
    R.string.search_engine_startpage
)
