package de.danoeh.antennapod.ui.screen.home.settingsdialog

import android.content.Context
import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.preferences.screen.ReorderDialog
import de.danoeh.antennapod.ui.preferences.screen.ReorderDialogItem
import java.util.ArrayList
import java.util.Collections

class HomeSectionsSettingsDialog(context: Context, private val onSettingsChanged: Runnable) : ReorderDialog(context) {
    override fun getTitle(): Int {
        return R.string.configure_home
    }

    override fun getInitialItems(): List<ReorderDialogItem> {
        val sectionTags = HomePreferences.getSortedSectionTags(context)
        val hiddenSectionTags = HomePreferences.getHiddenSectionTags(context)
        val settingsDialogItems: ArrayList<ReorderDialogItem> = ArrayList()
        settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Header,
                TAG_SHOWN, context.getString(R.string.section_shown)))
        for (sectionTag in sectionTags) {
            settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Section, sectionTag,
                    HomePreferences.getNameFromTag(context, sectionTag)))
        }
        settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Header,
                TAG_HIDDEN, context.getString(R.string.section_hidden)))
        for (sectionTag in hiddenSectionTags) {
            settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Section, sectionTag,
                    HomePreferences.getNameFromTag(context, sectionTag)))
        }
        return settingsDialogItems
    }

    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        if (toPosition == 0 || fromPosition == 0) {
            return false
        }
        return super.onItemMove(fromPosition, toPosition)
    }

    override fun onReset() {
        HomePreferences.saveChanges(context, Collections.emptyList(), Collections.emptyList())
        onSettingsChanged.run()
    }

    override fun onConfirmed() {
        val sectionOrder = getTagsWithoutHeaders()
        val hiddenSections = getTagsAfterHeader(TAG_HIDDEN)
        HomePreferences.saveChanges(context, hiddenSections, sectionOrder)
        onSettingsChanged.run()
    }

    companion object {
        private const val TAG_HIDDEN = "hidden"
        private const val TAG_SHOWN = "shown"
    }
}
