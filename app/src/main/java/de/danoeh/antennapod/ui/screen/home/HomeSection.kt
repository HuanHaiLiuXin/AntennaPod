package de.danoeh.antennapod.ui.screen.home

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DefaultItemAnimator
import de.danoeh.antennapod.ui.episodeslist.EpisodeItemListAdapter
import de.danoeh.antennapod.ui.screen.subscriptions.HorizontalFeedListAdapter
import de.danoeh.antennapod.ui.episodeslist.HorizontalItemListAdapter
import de.danoeh.antennapod.databinding.HomeSectionBinding
import de.danoeh.antennapod.ui.episodeslist.FeedItemMenuHandler
import de.danoeh.antennapod.ui.screen.subscriptions.FeedMenuHandler
import de.danoeh.antennapod.model.feed.FeedItem
import org.greenrobot.eventbus.EventBus

/**
 * Section on the HomeFragment
 */
abstract class HomeSection : Fragment(), View.OnCreateContextMenuListener {
    companion object {
        const val TAG = "HomeSection"
    }

    protected var viewBinding: HomeSectionBinding? = null

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?, savedInstanceState: Bundle?): View? {
        viewBinding = HomeSectionBinding.inflate(inflater)
        viewBinding!!.titleLabel.setText(getSectionTitle())
        viewBinding!!.moreButton.setText(getMoreLinkTitle())
        viewBinding!!.moreButton.setOnClickListener { handleMoreClick() }
        if (TextUtils.isEmpty(getMoreLinkTitle())) {
            viewBinding!!.moreButton.setVisibility(View.INVISIBLE)
        }
        // Dummies are necessary to ensure height, but do not animate them
        viewBinding!!.recyclerView.setItemAnimator(null)
        viewBinding!!.recyclerView.postDelayed({
            if (viewBinding != null) {
                viewBinding!!.recyclerView.setItemAnimator(DefaultItemAnimator())
            }
        }, 500L)
        return viewBinding!!.getRoot()
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (!getUserVisibleHint() || !isVisible() || !isMenuVisible()) {
            // The method is called on all fragments in a ViewPager, so this needs to be ignored in invisible ones.
            // Apparently, none of the visibility check method works reliably on its own, so we just use all.
            return false
        }
        if (viewBinding!!.recyclerView.getAdapter() is HorizontalFeedListAdapter) {
            val adapter = viewBinding!!.recyclerView.getAdapter() as HorizontalFeedListAdapter
            val selectedFeed = adapter.getLongPressedItem()
            return selectedFeed != null
                    && FeedMenuHandler.onMenuItemClicked(this, item.getItemId(), selectedFeed)
        }
        val longPressedItem: FeedItem?
        if (viewBinding!!.recyclerView.getAdapter() is EpisodeItemListAdapter) {
            val adapter = viewBinding!!.recyclerView.getAdapter() as EpisodeItemListAdapter
            longPressedItem = adapter.getLongPressedItem()
        } else if (viewBinding!!.recyclerView.getAdapter() is HorizontalItemListAdapter) {
            val adapter = viewBinding!!.recyclerView.getAdapter() as HorizontalItemListAdapter
            longPressedItem = adapter.getLongPressedItem()
        } else {
            return false
        }

        if (longPressedItem == null) {
            Log.i(TAG, "Selected item or listAdapter was null, ignoring selection")
            return super.onContextItemSelected(item)
        }
        return FeedItemMenuHandler.onMenuItemClicked(this, item.getItemId(), longPressedItem)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
        registerForContextMenu(viewBinding!!.recyclerView)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
        unregisterForContextMenu(viewBinding!!.recyclerView)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewBinding = null
    }

    protected abstract fun getSectionTitle(): String

    protected abstract fun getMoreLinkTitle(): String

    protected abstract fun handleMoreClick()
}
