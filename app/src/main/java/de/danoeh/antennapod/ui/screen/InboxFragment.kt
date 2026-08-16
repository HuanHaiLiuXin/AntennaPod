package de.danoeh.antennapod.ui.screen

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.util.Pair

import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.ui.screen.feed.ItemSortDialog
import de.danoeh.antennapod.event.FeedListUpdateEvent
import de.danoeh.antennapod.model.feed.FeedItem
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.episodeslist.EpisodesListFragment
import org.greenrobot.eventbus.EventBus

/**
 * Like 'EpisodesFragment' except that it only shows new episodes and
 * supports swiping to mark as read.
 */
class InboxFragment : EpisodesListFragment() {
    companion object {
        const val TAG = "NewEpisodesFragment"
        private const val PREF_NAME = "PrefNewEpisodesFragment"
        private const val PREF_DO_NOT_PROMPT_REMOVE_ALL_FROM_INBOX = "prefDoNotPromptRemovalAllFromInbox"
        private var scrollPosition: Pair<Int, Int>? = null
    }

    private lateinit var prefs: SharedPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        toolbar!!.inflateMenu(R.menu.inbox)
        toolbar!!.setTitle(R.string.inbox_label)
        prefs = getActivity()!!.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        updateToolbar()
        emptyView.setIcon(R.drawable.ic_inbox)
        emptyView.setTitle(R.string.no_inbox_head_label)
        emptyView.setMessage(R.string.home_new_empty_text)
        return root
    }

    override fun getFilter(): FeedItemFilter {
        return FeedItemFilter(FeedItemFilter.NEW)
    }

    override fun getFragmentTag(): String {
        return TAG
    }

    override fun onPause() {
        super.onPause()
        scrollPosition = recyclerView.getScrollPosition()
    }

    override fun onItemsFirstLoaded() {
        recyclerView.restoreScrollPosition(scrollPosition)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (super.onMenuItemClick(item)) {
            return true
        }
        if (item.getItemId() == R.id.remove_all_inbox_item) {
            if (prefs.getBoolean(PREF_DO_NOT_PROMPT_REMOVE_ALL_FROM_INBOX, false)) {
                removeAllFromInbox()
            } else {
                showRemoveAllDialog()
            }
            return true
        } else if (item.getItemId() == R.id.inbox_sort) {
            InboxSortDialog().show(getChildFragmentManager(), "SortDialog")
            return true
        }
        return false
    }

    override fun loadData(): List<FeedItem> {
        return DBReader.getEpisodes(0, page * EPISODES_PER_PAGE,
                FeedItemFilter(FeedItemFilter.NEW), UserPreferences.getInboxSortedOrder())
    }

    override fun loadMoreData(page: Int): List<FeedItem> {
        return DBReader.getEpisodes((page - 1) * EPISODES_PER_PAGE, EPISODES_PER_PAGE,
                FeedItemFilter(FeedItemFilter.NEW), UserPreferences.getInboxSortedOrder())
    }

    override fun loadTotalItemCount(): Int {
        return DBReader.getTotalEpisodeCount(FeedItemFilter(FeedItemFilter.NEW))
    }

    private fun removeAllFromInbox() {
        DBWriter.removeAllNewFlags()
        EventBus.getDefault().post(MessageEvent(getString(R.string.removed_all_inbox_msg)))
    }

    private fun showRemoveAllDialog() {
        val builder = MaterialAlertDialogBuilder(getContext()!!)
        builder.setTitle(R.string.remove_all_inbox_label)
        builder.setMessage(R.string.remove_all_inbox_confirmation_msg)

        val view = View.inflate(getContext()!!, R.layout.checkbox_do_not_show_again, null)
        val checkNeverAskAgain = view.findViewById<CheckBox>(R.id.checkbox_do_not_show_again)
        builder.setView(view)

        builder.setPositiveButton(R.string.confirm_label) { dialog, which ->
            dialog.dismiss()
            removeAllFromInbox()
            prefs.edit().putBoolean(PREF_DO_NOT_PROMPT_REMOVE_ALL_FROM_INBOX, checkNeverAskAgain.isChecked).apply()
        }
        builder.setNegativeButton(R.string.cancel_label, null)
        builder.show()
    }

    class InboxSortDialog : ItemSortDialog() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            sortOrder = UserPreferences.getInboxSortedOrder()
        }

        override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
            if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG) {
                super.onAddItem(title, ascending, descending, ascendingIsDefault)
            }
        }

        override fun onSelectionChanged() {
            super.onSelectionChanged()
            UserPreferences.setInboxSortedOrder(sortOrder)
            EventBus.getDefault().post(FeedListUpdateEvent(0))
        }
    }
}
