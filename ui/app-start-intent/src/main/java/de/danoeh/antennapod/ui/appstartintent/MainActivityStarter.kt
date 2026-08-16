package de.danoeh.antennapod.ui.appstartintent

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Launches the main activity of the app with specific arguments.
 * Does not require a dependency on the actual implementation of the activity.
 */
class MainActivityStarter(context: Context) {
    companion object {
        const val INTENT = "de.danoeh.antennapod.intents.MAIN_ACTIVITY"
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_FEED_ID = "fragment_feed_id"
        const val EXTRA_EPISODE_ID = "episode_id"
        const val EXTRA_CLEAR_BACK_STACK = "clear_back_stack"
        const val EXTRA_FRAGMENT_TAG = "fragment_tag"
        const val EXTRA_OPEN_DRAWER = "open_drawer"
        const val EXTRA_OPEN_DOWNLOAD_LOGS = "open_download_logs"
        const val EXTRA_FRAGMENT_ARGS = "fragment_args"
    }

    private val intent: Intent
    private val context: Context
    private var fragmentArgs: Bundle? = null

    init {
        this.context = context
        intent = Intent(INTENT)
        intent.setPackage(context.getPackageName())
        intent.putExtra(EXTRA_CLEAR_BACK_STACK, false)
    }

    fun getIntent(): Intent {
        if (fragmentArgs != null) {
            intent.putExtra(EXTRA_FRAGMENT_ARGS, fragmentArgs)
        }
        return intent
    }

    fun getPendingIntent(): PendingIntent {
        return PendingIntent.getActivity(context, R.id.pending_intent_player_activity, getIntent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun start() {
        context.startActivity(getIntent())
    }

    fun withOpenPlayer(): MainActivityStarter {
        intent.putExtra(EXTRA_OPEN_PLAYER, true)
        return this
    }

    fun withOpenFeed(feedId: Long): MainActivityStarter {
        intent.putExtra(EXTRA_FEED_ID, feedId)
        return this
    }

    fun withOpenEpisode(episodeId: Long): MainActivityStarter {
        intent.putExtra(EXTRA_EPISODE_ID, episodeId)
        return this
    }

    fun withClearBackStack(): MainActivityStarter {
        intent.putExtra(EXTRA_CLEAR_BACK_STACK, true)
        return this
    }

    fun withFragmentLoaded(fragmentName: String): MainActivityStarter {
        intent.putExtra(EXTRA_FRAGMENT_TAG, fragmentName)
        return this
    }

    fun withDrawerOpen(): MainActivityStarter {
        intent.putExtra(EXTRA_OPEN_DRAWER, true)
        return this
    }

    fun withDownloadLogsOpen(): MainActivityStarter {
        intent.putExtra(EXTRA_OPEN_DOWNLOAD_LOGS, true)
        return this
    }

    fun withClearTop(): MainActivityStarter {
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return this
    }

    fun withFragmentArgs(name: String, value: Boolean): MainActivityStarter {
        if (fragmentArgs == null) {
            fragmentArgs = Bundle()
        }
        fragmentArgs!!.putBoolean(name, value)
        return this
    }
}
