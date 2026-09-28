/*
 * Copyright 2014 A.C.R. Development
 */
package com.github.jaideepaher.slatebrowser.settings.activity

import com.github.jaideepaher.slatebrowser.ThemableActivity
import com.github.jaideepaher.slatebrowser.compose.BrowserTheme
import com.github.jaideepaher.slatebrowser.compose.slideInFrom
import com.github.jaideepaher.slatebrowser.di.injector
import com.github.jaideepaher.slatebrowser.settings.SettingsScreenStateProvider
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkPresenter
import com.github.jaideepaher.slatebrowser.settings.framework.SettingsFrameworkScreen
import com.github.jaideepaher.slatebrowser.settings.licenses.LicensesScreen
import com.github.jaideepaher.slatebrowser.settings.licenses.LicensesScreenPresenter
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigation
import com.github.jaideepaher.slatebrowser.settings.navigation.SettingsNavigator
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import javax.inject.Inject

class SettingsActivity : ThemableActivity() {

    @Inject internal lateinit var settingsScreenStateProvider: SettingsScreenStateProvider
    @Inject internal lateinit var licensesScreenPresenterFactory: LicensesScreenPresenter.Factory
    @Inject internal lateinit var settingsNavigator: SettingsNavigator

    override fun onCreate(savedInstanceState: Bundle?) {
        injector.settingsComponentBuilder()
            .activity(this)
            .build()
            .inject(this)

        super.onCreate(savedInstanceState)

        setContent {
            BrowserTheme(appThemeStateFlow) {
                val navigationState by settingsNavigator.events.collectAsState(SettingsNavigation.ROOT)
                AnimatedContent(navigationState, transitionSpec = {
                    when {
                        targetState == initialState.parent -> slideInFrom { -it / 2 }
                        else -> slideInFrom { it / 2 }
                    }
                }) { state ->
                    when (state) {
                        SettingsNavigation.LICENSES -> LicensesScreen(
                            useBlackStatusBarStateFlow,
                            viewModel(
                                key = "licenses",
                                factory = licensesScreenPresenterFactory
                            ),
                            onClickUrl = {
                                startActivity(Intent(Intent.ACTION_VIEW, it.toUri()))
                            }
                        ) {
                            settingsNavigator.navigateTo(SettingsNavigation.ABOUT)
                        }

                        else -> {
                            val frameworkState = settingsScreenStateProvider.provideState(state)
                            SettingsFrameworkScreen(
                                useBlackStatusBarStateFlow,
                                viewModel(
                                    key = state.name,
                                    factory = SettingsFrameworkPresenter.Factory(
                                        settingsFrameworkState = { frameworkState },
                                        settingsNavigator = settingsNavigator,
                                    )
                                )
                            ) {
                                if (state == SettingsNavigation.ROOT) {
                                    finish()
                                } else {
                                    settingsNavigator.navigateTo(SettingsNavigation.ROOT)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
