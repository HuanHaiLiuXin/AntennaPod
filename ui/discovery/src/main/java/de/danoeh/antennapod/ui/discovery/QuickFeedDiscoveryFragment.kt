package de.danoeh.antennapod.ui.discovery

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.TextUtils
import android.util.DisplayMetrics

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import de.danoeh.antennapod.net.discovery.BuildConfig
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.event.DiscoveryDefaultUpdateEvent
import de.danoeh.antennapod.net.discovery.ItunesTopListLoader
import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.discovery.databinding.QuickFeedDiscoveryBinding
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import java.util.ArrayList
import java.util.Locale

class QuickFeedDiscoveryFragment : Fragment(), AdapterView.OnItemClickListener {
    companion object {
        private const val TAG = "FeedDiscoveryFragment"
        private const val NUM_SUGGESTIONS = 12
    }

    private var disposable: Disposable? = null
    private var adapter: FeedDiscoverAdapter? = null
    private var viewBinding: QuickFeedDiscoveryBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        viewBinding = QuickFeedDiscoveryBinding.inflate(inflater)
        viewBinding!!.discoverMore.setOnClickListener {
            startActivity(MainActivityStarter(requireContext())
                    .withFragmentLoaded(DiscoveryFragment.TAG)
                    .getIntent())
        }

        adapter = FeedDiscoverAdapter(requireActivity())
        viewBinding!!.discoverGrid.setAdapter(adapter)
        viewBinding!!.discoverGrid.setOnItemClickListener(this)

        val displayMetrics: DisplayMetrics = requireContext().getResources().getDisplayMetrics()
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        if (screenWidthDp > 600) {
            viewBinding!!.discoverGrid.setNumColumns(6)
        } else {
            viewBinding!!.discoverGrid.setNumColumns(4)
        }

        // Fill with dummy elements to have a fixed height and
        // prevent the UI elements below from jumping on slow connections
        val dummies: MutableList<PodcastSearchResult> = ArrayList()
        for (i in 0 until NUM_SUGGESTIONS) {
            dummies.add(PodcastSearchResult.dummy())
        }

        adapter!!.updateData(dummies)
        loadToplist()

        EventBus.getDefault().register(this)
        return viewBinding!!.getRoot()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
        viewBinding = null
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDiscoveryDefaultUpdateEvent(event: DiscoveryDefaultUpdateEvent) {
        loadToplist()
    }

    private fun loadToplist() {
        viewBinding!!.errorContainer.setVisibility(View.GONE)
        viewBinding!!.errorRetryButton.setVisibility(View.INVISIBLE)
        viewBinding!!.errorRetryButton.setText(R.string.retry_label)
        viewBinding!!.poweredByLabel.setVisibility(View.VISIBLE)

        val loader = ItunesTopListLoader(requireContext())
        val prefs: SharedPreferences = requireActivity().getSharedPreferences(ItunesTopListLoader.PREFS, Context.MODE_PRIVATE)
        val countryCode = prefs.getString(ItunesTopListLoader.PREF_KEY_COUNTRY_CODE,
                Locale.getDefault().getCountry())!!
        if (prefs.getBoolean(ItunesTopListLoader.PREF_KEY_HIDDEN_DISCOVERY_COUNTRY, false)) {
            viewBinding!!.errorLabel.setText(R.string.discover_is_hidden)
            viewBinding!!.errorContainer.setVisibility(View.VISIBLE)
            viewBinding!!.discoverGrid.setVisibility(View.GONE)
            viewBinding!!.errorRetryButton.setVisibility(View.GONE)
            viewBinding!!.poweredByLabel.setVisibility(View.GONE)
            return
        }
        if (BuildConfig.FLAVOR == "free" && prefs.getBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, true)) {
            viewBinding!!.errorLabel.setText("")
            viewBinding!!.errorContainer.setVisibility(View.VISIBLE)
            viewBinding!!.discoverGrid.setVisibility(View.VISIBLE)
            viewBinding!!.errorRetryButton.setVisibility(View.VISIBLE)
            viewBinding!!.errorRetryButton.setText(R.string.discover_confirm)
            viewBinding!!.poweredByLabel.setVisibility(View.VISIBLE)
            viewBinding!!.errorRetryButton.setOnClickListener {
                prefs.edit().putBoolean(ItunesTopListLoader.PREF_KEY_NEEDS_CONFIRM, false).apply()
                loadToplist()
            }
            return
        }

        disposable = Observable.fromCallable<List<PodcastSearchResult>> {
                    loader.loadToplist(countryCode, NUM_SUGGESTIONS, DBReader.getFeedList()) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { podcasts ->
                        viewBinding!!.errorContainer.setVisibility(View.GONE)
                        if (podcasts.isEmpty()) {
                            viewBinding!!.errorLabel.setText(getResources().getText(R.string.search_status_no_results))
                            viewBinding!!.errorContainer.setVisibility(View.VISIBLE)
                            viewBinding!!.discoverGrid.setVisibility(View.INVISIBLE)
                        } else {
                            viewBinding!!.discoverGrid.setVisibility(View.VISIBLE)
                            adapter!!.updateData(podcasts)
                        }
                    }, { error ->
                        Log.e(TAG, Log.getStackTraceString(error))
                        viewBinding!!.errorLabel.setText(error.getLocalizedMessage())
                        viewBinding!!.errorContainer.setVisibility(View.VISIBLE)
                        viewBinding!!.discoverGrid.setVisibility(View.INVISIBLE)
                        viewBinding!!.errorRetryButton.setVisibility(View.VISIBLE)
                        viewBinding!!.errorRetryButton.setOnClickListener { loadToplist() }
                    })
    }

    override fun onItemClick(parent: AdapterView<*>, view: View, position: Int, id: Long) {
        val podcast = adapter!!.getItem(position)
        if (TextUtils.isEmpty(podcast.feedUrl)) {
            return
        }
        startActivity(OnlineFeedviewActivityStarter(requireContext(), podcast.feedUrl!!).getIntent())
    }
}
