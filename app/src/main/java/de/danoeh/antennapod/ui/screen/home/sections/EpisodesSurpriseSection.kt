package de.danoeh.antennapod.ui.screen.home.sections

import android.os.Bundle
import android.util.Log
import android.view.ContextMenu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.event.EpisodeDownloadEvent
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.event.PlayerStatusEvent
import de.danoeh.antennapod.event.playback.PlaybackPositionEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemListAdapter
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemViewHolder
import de.danoeh.antennapod.ui.screen.AllEpisodesFragment
import de.danoeh.antennapod.ui.screen.home.HomeSection
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.ArrayList
import java.util.Random

class EpisodesSurpriseSection : HomeSection() {
    private var listAdapter: HorizontalItemListAdapter? = null
    private var disposable: Disposable? = null
    private var episodes: List<FeedItem> = ArrayList()

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        viewBinding!!.shuffleButton.setVisibility(View.VISIBLE)
        viewBinding!!.shuffleButton.setOnClickListener {
            seed = Random().nextInt()
            viewBinding!!.recyclerView.scrollToPosition(0)
            loadItems()
        }
        listAdapter = object : HorizontalItemListAdapter(getActivity() as MainActivity) {
            override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
                super.onCreateContextMenu(menu, v, menuInfo)
                MenuItemUtils.setOnClickListeners(menu, this@EpisodesSurpriseSection::onContextItemSelected)
            }
        }
        listAdapter!!.setDummyViews(NUM_EPISODES)
        viewBinding!!.recyclerView.setLayoutManager(
                LinearLayoutManager(getContext(), RecyclerView.HORIZONTAL, false))
        viewBinding!!.recyclerView.setAdapter(listAdapter)
        val paddingHorizontal = (12 * getResources().getDisplayMetrics().density).toInt()
        viewBinding!!.recyclerView.setPadding(paddingHorizontal, 0, paddingHorizontal, 0)
        if (seed == 0) {
            seed = Random().nextInt()
        }
        viewBinding!!.emptyLabel.setText(R.string.home_no_recent_unplayed_episodes_text)
        return view
    }

    override fun onStart() {
        super.onStart()
        loadItems()
    }

    override fun onStop() {
        super.onStop()
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    override fun handleMoreClick() {
        (requireActivity() as MainActivity).loadChildFragment(AllEpisodesFragment())
    }

    override fun getSectionTitle(): String {
        return getString(R.string.home_surprise_title)
    }

    override fun getMoreLinkTitle(): String {
        return getString(R.string.episodes_label)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onPlayerStatusChanged(event: PlayerStatusEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]")
        for (i in 0 until event.items.size) {
            val item = event.items.get(i)
            val pos = FeedItemEvent.indexOfItemWithId(episodes, item.getId())
            if (pos >= 0) {
                (episodes as ArrayList<FeedItem>).removeAt(pos)
                (episodes as ArrayList<FeedItem>).add(pos, item)
                listAdapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: EpisodeDownloadEvent) {
        for (downloadUrl in event.getUrls()) {
            val pos = EpisodeDownloadEvent.indexOfItemWithDownloadUrl(episodes, downloadUrl)
            if (pos >= 0) {
                listAdapter!!.notifyItemChangedCompat(pos)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedListUpdateEvent) {
        loadItems()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: PlaybackPositionEvent) {
        if (listAdapter == null) {
            return
        }
        for (i in 0 until listAdapter!!.getItemCount()) {
            val holder = viewBinding!!.recyclerView.findViewHolderForAdapterPosition(i) as HorizontalItemViewHolder?
            if (holder != null && holder.isCurrentlyPlayingItem()) {
                holder.notifyPlaybackPositionUpdated(event)
                break
            }
        }
    }

    private fun loadItems() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable { DBReader.getRandomEpisodes(NUM_EPISODES, seed) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ episodes ->
                    this.episodes = episodes
                    listAdapter!!.setDummyViews(0)
                    listAdapter!!.updateData(episodes)

                    val isShuffleable = !episodes.isEmpty()
                    viewBinding!!.shuffleButton.setVisibility(if (isShuffleable) View.VISIBLE else View.GONE)
                    viewBinding!!.recyclerView.setVisibility(if (isShuffleable) View.VISIBLE else View.GONE)
                    viewBinding!!.emptyLabel.setVisibility(if (!isShuffleable) View.VISIBLE else View.GONE)
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    companion object {
        const val TAG = "EpisodesSurpriseSection"
        private const val NUM_EPISODES = 8
        private var seed = 0
    }
}
