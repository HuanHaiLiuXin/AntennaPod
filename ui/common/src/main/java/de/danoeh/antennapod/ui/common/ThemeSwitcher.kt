package de.danoeh.antennapod.ui.common

import android.content.Context
import android.content.res.Configuration
import de.danoeh.antennapod.storage.preferences.UserPreferences

abstract class ThemeSwitcher {
    companion object {
        @JvmStatic
        fun getNoTitleTheme(context: Context): Int {
            val dynamic = UserPreferences.getIsThemeColorTinted()
            return when (readThemeValue(context)) {
                UserPreferences.ThemePreference.DARK ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_Dark_NoTitle else R.style.Theme_AntennaPod_Dark_NoTitle
                UserPreferences.ThemePreference.BLACK ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_TrueBlack_NoTitle
                    else R.style.Theme_AntennaPod_TrueBlack_NoTitle
                UserPreferences.ThemePreference.LIGHT ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_NoTitle
                    else R.style.Theme_AntennaPod_Light_NoTitle
                else ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_NoTitle
                    else R.style.Theme_AntennaPod_Light_NoTitle
            }
        }

        @JvmStatic
        fun getTranslucentTheme(context: Context): Int {
            val dynamic = UserPreferences.getIsThemeColorTinted()
            return when (readThemeValue(context)) {
                UserPreferences.ThemePreference.DARK ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_Dark_Translucent
                    else R.style.Theme_AntennaPod_Dark_Translucent
                UserPreferences.ThemePreference.BLACK ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_TrueBlack_Translucent
                    else R.style.Theme_AntennaPod_TrueBlack_Translucent
                UserPreferences.ThemePreference.LIGHT ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_Translucent
                    else R.style.Theme_AntennaPod_Light_Translucent
                else ->
                    if (dynamic) R.style.Theme_AntennaPod_Dynamic_Light_Translucent
                    else R.style.Theme_AntennaPod_Light_Translucent
            }
        }

        private fun readThemeValue(context: Context): UserPreferences.ThemePreference {
            var theme = UserPreferences.getTheme()
            if (theme == UserPreferences.ThemePreference.SYSTEM) {
                val nightMode = context.getResources().getConfiguration().uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightMode == Configuration.UI_MODE_NIGHT_YES) {
                    theme = UserPreferences.ThemePreference.DARK
                } else {
                    theme = UserPreferences.ThemePreference.LIGHT

                }
            }
            if (theme == UserPreferences.ThemePreference.DARK && UserPreferences.getIsBlackTheme()) {
                theme = UserPreferences.ThemePreference.BLACK
            }
            return theme
        }
    }
}
