package com.github.jaideepaher.slatebrowser.settings.screens

import com.github.jaideepaher.slatebrowser.R
import com.github.jaideepaher.slatebrowser.preference.DeveloperPreferenceStore
import com.github.jaideepaher.slatebrowser.resources.ResourceProvider
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkState
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsSnackBarState
import com.github.jaideepaher.slatebrowser.settings.framework.ToggleState
import javax.inject.Inject

class DebugSettingsScreen @Inject constructor(
    private val resourceProvider: ResourceProvider,
    private val developerPreferenceStore: DeveloperPreferenceStore
) {
    fun createSettingsFrameworkState(): SettingsFrameworkState = SettingsFrameworkState(
        title = resourceProvider.stringResource(R.string.debug_title),
        content = listOf(
            ToggleState(
                title = resourceProvider.stringResource(R.string.debug_leak_canary),
                isChecked = { developerPreferenceStore.useLeakCanary.get() },
                onToggle = {
                    developerPreferenceStore.useLeakCanary.set(it)
                    SettingsSnackBarState(
                        resourceProvider.stringResource(R.string.app_restart)
                    )
                }
            )
        )
    )
}
