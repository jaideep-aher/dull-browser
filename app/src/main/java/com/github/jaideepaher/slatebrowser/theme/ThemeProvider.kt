package com.github.jaideepaher.slatebrowser.theme

import com.github.jaideepaher.slatebrowser.AppTheme
import androidx.compose.material3.ColorScheme
import kotlinx.coroutines.flow.Flow

/**
 * Provides the current app theme.
 */
interface ThemeProvider {

    /**
     * Emit the current [AppTheme] and all changes to it.
     */
    fun appThemeValues(): Flow<AppTheme>

    /**
     * The current app theme.
     */
    suspend fun appTheme(): AppTheme

    /**
     * The current app color scheme.
     */
    suspend fun colorScheme(): ColorScheme

    /**
     * True if the current app theme resolves to a dark palette.
     */
    suspend fun isDarkTheme(): Boolean

}
