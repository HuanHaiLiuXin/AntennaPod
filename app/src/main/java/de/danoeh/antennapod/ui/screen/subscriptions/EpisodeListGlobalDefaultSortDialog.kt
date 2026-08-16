package de.danoeh.antennapod.ui.screen.subscriptions

import android.os.Bundle

import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.screen.feed.ItemSortDialog

class EpisodeListGlobalDefaultSortDialog : ItemSortDialog() {
    companion object {
        fun newInstance(): EpisodeListGlobalDefaultSortDialog {
            val bundle = Bundle()
            val dialog = EpisodeListGlobalDefaultSortDialog()
            dialog.setArguments(bundle)
            return dialog
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sortOrder = SortOrder.fromCodeString(java.lang.String.valueOf(UserPreferences.getPrefGlobalSortedOrder().code))
    }

    override fun onAddItem(title: Int, ascending: SortOrder, descending: SortOrder, ascendingIsDefault: Boolean) {
        if (ascending == SortOrder.DATE_OLD_NEW || ascending == SortOrder.DURATION_SHORT_LONG
                || ascending == SortOrder.EPISODE_TITLE_A_Z) {
            super.onAddItem(title, ascending, descending, ascendingIsDefault)
        }
    }

    override fun onSelectionChanged() {
        super.onSelectionChanged()
        UserPreferences.setPrefGlobalSortedOrder(sortOrder!!)
    }
}
