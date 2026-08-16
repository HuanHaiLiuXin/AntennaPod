package de.danoeh.antennapod.ui.appstartintent

import android.content.Context
import android.content.Intent

class OnlineFeedviewActivityStarter(context: Context, feedUrl: String) {
    companion object {
        const val INTENT = "de.danoeh.antennapod.intents.ONLINE_FEEDVIEW"
        const val ARG_FEEDURL = "arg.feedurl"
        const val ARG_WAS_MANUAL_URL = "manual_url"
    }

    private val intent: Intent

    init {
        intent = Intent(INTENT)
        intent.setPackage(context.getPackageName())
        intent.putExtra(ARG_FEEDURL, feedUrl)
    }

    fun withManualUrl(): OnlineFeedviewActivityStarter {
        intent.putExtra(ARG_WAS_MANUAL_URL, true)
        return this
    }

    fun getIntent(): Intent {
        return intent
    }
}
