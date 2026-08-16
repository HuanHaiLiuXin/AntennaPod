package de.danoeh.antennapod.ui.screen.playback.audio

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.Consumer
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.BuildConfig
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.model.feed.FeedMedia
import de.danoeh.antennapod.model.playback.Playable
import de.danoeh.antennapod.playback.service.PlaybackController
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.PlaybackPreferences
import de.danoeh.antennapod.ui.cleaner.ShownotesCleaner
import de.danoeh.antennapod.ui.view.ShownotesWebView
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

/**
 * Displays the description of a Playable object in a Webview.
 */
class ItemDescriptionFragment : Fragment() {
    private var webvDescription: ShownotesWebView? = null
    private var webViewLoader: Disposable? = null
    private var loadedData = ""

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.d(TAG, "Creating view")
        val root = inflater.inflate(R.layout.item_description_fragment, container, false)
        webvDescription = root.findViewById(R.id.webview)
        webvDescription!!.setTimecodeSelectedListener(Consumer { time ->
            if (BuildConfig.USE_MEDIA3_PLAYBACK_SERVICE) {
                PlaybackController.bindToMedia3Service(getActivity()!!, Consumer { controller ->
                    controller.seekTo(time.toLong())
                })
            } else {
                PlaybackController.bindToService(getActivity()!!, Consumer { playbackService ->
                    playbackService.seekTo(time)
                })
            }
        })
        webvDescription!!.setPageFinishedListener {
            // Restoring the scroll position might not always work
            webvDescription!!.postDelayed({ restoreFromPreference() }, 50)
        }

        root.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, left: Int, top: Int, right: Int,
                                        bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
                if (root.getMeasuredHeight() != webvDescription!!.getMinimumHeight()) {
                    webvDescription!!.setMinimumHeight(root.getMeasuredHeight())
                }
                root.removeOnLayoutChangeListener(this)
            }
        })
        registerForContextMenu(webvDescription!!)
        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Fragment destroyed")
        if (webvDescription != null) {
            webvDescription!!.removeAllViews()
            webvDescription!!.destroy()
        }
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        return webvDescription!!.onContextItemSelected(item)
    }

    private fun load() {
        Log.d(TAG, "load()")
        if (webViewLoader != null) {
            webViewLoader!!.dispose()
        }
        val context = getContext()
        if (context == null) {
            return
        }
        webViewLoader = Maybe.create<String>({ emitter ->
            val media = DBReader.getFeedMedia(PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
            if (media == null) {
                emitter.onComplete()
                return@create
            }
            if (media is FeedMedia) {
                val feedMedia = media
                if (feedMedia.getItem() == null) {
                    feedMedia.setItem(DBReader.getFeedItem(feedMedia.getItemId()))
                }
                DBReader.loadDescriptionOfFeedItem(feedMedia.getItem()!!)
            }
            val shownotesCleaner = ShownotesCleaner(
                    context, media.getDescription(), media.getDuration())
            emitter.onSuccess(shownotesCleaner.processShownotes())
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ data ->
                    if (TextUtils.equals(loadedData, data)) {
                        return@subscribe
                    }
                    loadedData = data
                    webvDescription!!.loadDataWithBaseURL("https://127.0.0.1", data, "text/html",
                            "utf-8", "about:blank")
                    Log.d(TAG, "Webview loaded")
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    override fun onPause() {
        super.onPause()
        savePreference()
    }

    private fun savePreference() {
        Log.d(TAG, "Saving preferences")
        val prefs = getActivity()!!.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
        val editor = prefs.edit()
        if (webvDescription != null) {
            Log.d(TAG, "Saving scroll position: " + webvDescription!!.getScrollY())
            editor.putInt(PREF_SCROLL_Y, webvDescription!!.getScrollY())
            editor.putString(PREF_PLAYABLE_ID, "" + PlaybackPreferences.getCurrentlyPlayingFeedMediaId())
        } else {
            Log.d(TAG, "savePreferences was called while webview was null")
            editor.putInt(PREF_SCROLL_Y, -1)
            editor.putString(PREF_PLAYABLE_ID, "")
        }
        editor.apply()
    }

    private fun restoreFromPreference() {
        Log.d(TAG, "Restoring from preferences")
        val activity = getActivity()
        if (activity != null) {
            val prefs = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE)
            val id = prefs.getString(PREF_PLAYABLE_ID, "")
            val scrollY = prefs.getInt(PREF_SCROLL_Y, -1)
            if (scrollY != -1
                    && id == "" + PlaybackPreferences.getCurrentlyPlayingFeedMediaId()
                    && webvDescription != null) {
                Log.d(TAG, "Restored scroll Position: " + scrollY)
                webvDescription!!.scrollTo(webvDescription!!.getScrollX(), scrollY)
            }
        }
    }

    fun scrollToTop() {
        webvDescription!!.scrollTo(0, 0)
        savePreference()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        load()
    }

    override fun onStop() {
        super.onStop()

        if (webViewLoader != null) {
            webViewLoader!!.dispose()
        }
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusEvent(event: PlayerStatusEvent) {
        load()
    }

    companion object {
        private const val TAG = "ItemDescriptionFragment"

        private const val PREF = "ItemDescriptionFragmentPrefs"
        private const val PREF_SCROLL_Y = "prefScrollY"
        private const val PREF_PLAYABLE_ID = "prefPlayableId"
    }
}
