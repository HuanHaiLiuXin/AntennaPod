package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.echo.R

import java.util.Locale

abstract class EchoScreen(protected val context: Context) {

    protected fun getEchoLanguage(): Locale {
        val hasTranslation = context.getString(R.string.echo_listened_after_title) !=
                getLocalizedResources(Locale.US).getString(R.string.echo_listened_after_title)
        if (hasTranslation) {
            return Locale.getDefault()
        } else {
            return Locale.US
        }
    }

    protected fun getLocalizedResources(desiredLocale: Locale): Resources {
        var conf = context.getResources().getConfiguration()
        conf = Configuration(conf)
        conf.setLocale(desiredLocale)
        val localizedContext = context.createConfigurationContext(conf)
        return localizedContext.getResources()
    }

    open fun postInvalidate() {
        // Do nothing by default
    }

    abstract fun getView(): View

    open fun startLoading(statisticsResult: DBReader.StatisticsResult) {
        // Do nothing by default
    }
}
