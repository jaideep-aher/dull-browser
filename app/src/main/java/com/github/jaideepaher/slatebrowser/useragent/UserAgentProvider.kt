package com.github.jaideepaher.slatebrowser.useragent

import com.github.jaideepaher.slatebrowser.constant.DESKTOP_USER_AGENT
import com.github.jaideepaher.slatebrowser.constant.MOBILE_USER_AGENT
import com.github.jaideepaher.slatebrowser.preference.UserPreferencesDataStore
import android.app.Application
import android.webkit.WebSettings
import javax.inject.Inject

/**
 * Provides the chosen user-agent if different from the default.
 */
class DefaultUserAgentProvider @Inject constructor(
    private val userPreferencesDataStore: UserPreferencesDataStore,
    private val application: Application,
) : UserAgentProvider {
    override suspend fun getUserAgent(): String =
        when (userPreferencesDataStore.userAgentChoice.get()) {
            UserAgentChoice.DEFAULT -> WebSettings.getDefaultUserAgent(application)
            UserAgentChoice.DESKTOP -> DESKTOP_USER_AGENT
            UserAgentChoice.MOBILE -> MOBILE_USER_AGENT
            UserAgentChoice.CUSTOM -> userPreferencesDataStore.userAgentString.get()
                .takeIf(String::isNotEmpty).orEmpty()
        }
}

/**
 * Provides the preference backed user-agent.
 */
interface UserAgentProvider {

    /**
     * Get the current user-agent string.
     */
    suspend fun getUserAgent(): String
}
