package com.github.jaideepaher.slatebrowser.di

import com.github.jaideepaher.slatebrowser.BrowserApp
import android.content.Context

/**
 * The [AppComponent] attached to the application [Context].
 */
val Context.injector: AppComponent
    get() = (applicationContext as BrowserApp).applicationComponent

