package de.danoeh.antennapod.ui.screen.feed.preferences

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.google.android.material.appbar.MaterialToolbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.storage.database.DBReader
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Container fragment for feed settings fragment.
 * @see FeedSettingsPreferenceFragment for the actual preferences.
 */
class FeedSettingsFragment : Fragment() {
    companion object {
        private const val TAG = "FeedSettingsFragment"
        private const val EXTRA_FEED_ID = "de.danoeh.antennapod.extra.feedId"

        @JvmStatic
        fun newInstance(feed: Feed): FeedSettingsFragment {
            val fragment = FeedSettingsFragment()
            val arguments = Bundle()
            arguments.putLong(EXTRA_FEED_ID, feed.getId())
            fragment.setArguments(arguments)
            return fragment
        }
    }

    private var disposable: Disposable? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.feedsettings, container, false)
        val feedId = requireArguments().getLong(EXTRA_FEED_ID)

        val toolbar = root.findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }

        getParentFragmentManager().beginTransaction()
                .replace(R.id.settings_fragment_container,
                        FeedSettingsPreferenceFragment.newInstance(feedId), "settings_fragment")
                .commitAllowingStateLoss()

        disposable = Maybe.create<Feed> { emitter ->
            val feed = DBReader.getFeed(feedId, false, 0, 0)
            if (feed != null) {
                emitter.onSuccess(feed)
            } else {
                emitter.onComplete()
            }
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result -> toolbar.setSubtitle(result.getTitle()) },
                        { error -> Log.d(TAG, Log.getStackTraceString(error)) },
                        { })


        return root
    }

    override fun onDestroy() {
        super.onDestroy()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }
}
