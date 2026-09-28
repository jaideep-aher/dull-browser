package com.github.jaideepaher.slatebrowser.di

import com.github.jaideepaher.slatebrowser.settings.activity.SettingsActivity
import android.app.Activity
import dagger.BindsInstance
import dagger.Subcomponent

@SettingsScope
@Subcomponent
interface SettingsComponent {

    @Subcomponent.Builder
    interface Builder {

        @BindsInstance
        fun activity(activity: Activity): Builder

        fun build(): SettingsComponent
    }

    fun inject(activity: SettingsActivity)

}
