package de.danoeh.antennapod.ui

import android.os.Bundle
import de.danoeh.antennapod.model.feed.FeedItemFilter
import de.danoeh.antennapod.ui.screen.feed.ItemFilterDialog
import org.greenrobot.eventbus.EventBus

class AllEpisodesFilterDialog : ItemFilterDialog() {

    override fun onFilterChanged(newFilterValues: Set<String>) {
        EventBus.getDefault().post(AllEpisodesFilterChangedEvent(newFilterValues))
    }

    class AllEpisodesFilterChangedEvent(@JvmField val filterValues: Set<String>)

    companion object {
        @JvmStatic
        fun newInstance(filter: FeedItemFilter): AllEpisodesFilterDialog {
            val dialog = AllEpisodesFilterDialog()
            val arguments = Bundle()
            arguments.putSerializable(ARGUMENT_FILTER, filter)
            dialog.setArguments(arguments)
            return dialog
        }
    }
}
