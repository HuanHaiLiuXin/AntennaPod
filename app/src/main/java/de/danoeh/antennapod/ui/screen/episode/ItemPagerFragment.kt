package de.danoeh.antennapod.ui.screen.episode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.MaterialToolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2

import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.FeedItemEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.util.Collections

/**
 * Displays information about a list of FeedItems.
 */
class ItemPagerFragment : Fragment(), Toolbar.OnMenuItemClickListener {
    companion object {
        private const val ARG_FEEDITEMS = "feeditems"
        private const val ARG_FEEDITEM_POS = "feeditem_pos"
        private const val KEY_PAGER_ID = "pager_id"

        @JvmStatic
        fun newInstance(itemId: Long): ItemPagerFragment {
            val fragment = ItemPagerFragment()
            val args = Bundle()
            args.putLongArray(ARG_FEEDITEMS, longArrayOf(itemId))
            args.putInt(ARG_FEEDITEM_POS, 0)
            fragment.setArguments(args)
            return fragment
        }

        /**
         * Creates a new instance of an ItemPagerFragment.
         *
         * @return The ItemFragment instance
         */
        @JvmStatic
        fun newInstance(allItems: List<FeedItem>, currentItem: FeedItem): ItemPagerFragment {
            var position = 0
            val ids = LongArray(allItems.size)
            for (i in allItems.indices) {
                ids[i] = allItems[i].getId()
                if (ids[i] == currentItem.getId()) {
                    position = i
                }
            }
            val fragment = ItemPagerFragment()
            val args = Bundle()
            args.putLongArray(ARG_FEEDITEMS, ids)
            args.putInt(ARG_FEEDITEM_POS, position)
            fragment.setArguments(args)
            return fragment
        }
    }

    private lateinit var feedItems: LongArray
    private var item: FeedItem? = null
    private var disposable: Disposable? = null
    private lateinit var toolbar: MaterialToolbar
    private var pager: ViewPager2? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        val layout = inflater.inflate(R.layout.feeditem_pager_fragment, container, false)
        toolbar = layout.findViewById(R.id.toolbar)
        toolbar.setTitle("")
        toolbar.inflateMenu(R.menu.feeditem_options)
        toolbar.setNavigationOnClickListener { getParentFragmentManager().popBackStack() }
        toolbar.setOnMenuItemClickListener(this)

        feedItems = requireArguments().getLongArray(ARG_FEEDITEMS)!!
        val feedItemPos = Math.max(0, requireArguments().getInt(ARG_FEEDITEM_POS))

        pager = layout.findViewById(R.id.pager)
        // FragmentStatePagerAdapter documentation:
        // > When using FragmentStatePagerAdapter the host ViewPager must have a valid ID set.
        // When opening multiple ItemPagerFragments by clicking "item" -> "visit podcast" -> "item" -> etc,
        // the ID is no longer unique and FragmentStatePagerAdapter does not display any pages.
        var newId = View.generateViewId()
        if (savedInstanceState != null && savedInstanceState.getInt(KEY_PAGER_ID, 0) != 0) {
            // Restore state by using the same ID as before. ID collisions are prevented in MainActivity.
            newId = savedInstanceState.getInt(KEY_PAGER_ID, 0)
        }
        pager!!.setId(newId)
        pager!!.setAdapter(ItemPagerAdapter(this))
        pager!!.setCurrentItem(feedItemPos, false)
        pager!!.setOffscreenPageLimit(1)
        loadItem(feedItems[feedItemPos])
        pager!!.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                loadItem(feedItems[position])
            }
        })

        EventBus.getDefault().register(this)
        return layout
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (pager != null) {
            outState.putInt(KEY_PAGER_ID, pager!!.getId())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        EventBus.getDefault().unregister(this)
        if (disposable != null) {
            disposable!!.dispose()
        }
    }

    private fun loadItem(itemId: Long) {
        if (disposable != null) {
            disposable!!.dispose()
        }

        disposable = Observable.fromCallable<FeedItem> { DBReader.getFeedItem(itemId)!! }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ result ->
                    item = result
                    refreshToolbarState()
                }, { error -> error.printStackTrace() })
    }

    fun refreshToolbarState() {
        if (item == null) {
            return
        }
        if (item!!.hasMedia()) {
            FeedItemMenuHandler.onPrepareMenu(toolbar.getMenu(), Collections.singletonList(item))
        } else {
            // these are already available via button1 and button2
            FeedItemMenuHandler.onPrepareMenu(toolbar.getMenu(), Collections.singletonList(item),
                    R.id.mark_read_item, R.id.visit_website_item)
        }
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        if (menuItem.getItemId() == R.id.open_podcast) {
            openPodcast()
            return true
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, menuItem.getItemId(), item!!)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: FeedItemEvent) {
        if (event.unreadStatusChanged && event.items.isEmpty()) {
            refreshToolbarState()
            return
        }
        for (item in event.items) {
            if (this.item != null && this.item!!.getId() == item.getId()) {
                this.item = item
                refreshToolbarState()
                return
            }
        }
    }

    private fun openPodcast() {
        if (item == null) {
            return
        }
        if (item!!.getFeed()!!.getState() == Feed.STATE_NOT_SUBSCRIBED) {
            startActivity(OnlineFeedviewActivityStarter(requireContext(),
                    item!!.getFeed()!!.getDownloadUrl()!!).getIntent())
        } else {
            MainActivityStarter(requireContext()).withOpenFeed(item!!.getFeedId()).withClearTop().start()
        }
    }

    private inner class ItemPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun createFragment(position: Int): Fragment {
            return ItemFragment.newInstance(feedItems[position])
        }

        override fun getItemCount(): Int {
            return feedItems.size
        }
    }
}
