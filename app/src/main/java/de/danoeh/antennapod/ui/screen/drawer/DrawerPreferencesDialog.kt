package de.danoeh.antennapod.ui.screen.drawer

import android.content.Context
import de.danoeh.antennapod.R
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.preferences.screen.ReorderDialog
import de.danoeh.antennapod.ui.preferences.screen.ReorderDialogItem

import java.util.ArrayList
import java.util.Collections

class DrawerPreferencesDialog(context: Context, private val onSettingsChanged: Runnable?) :
        ReorderDialog(context) {
    companion object {
        private const val TAG_HIDDEN = "hidden"
        private const val TAG_SHOWN = "shown"
    }

    override fun getTitle(): Int {
        return R.string.drawer_preferences
    }

    override fun getInitialItems(): List<ReorderDialogItem> {
        val settingsDialogItems = ArrayList<ReorderDialogItem>()
        settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Header,
                TAG_SHOWN, context.getString(R.string.section_shown)))

        val drawerItemOrder = UserPreferences.getVisibleDrawerItemOrder()
        for (tag in drawerItemOrder) {
            if (UserPreferences.isBottomNavigationEnabled() && tag == NavListAdapter.SUBSCRIPTION_LIST_TAG) {
                continue
            }
            settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Section,
                    tag!!, context.getString(NavigationNames.getLabel(tag))))
        }

        settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Header,
                TAG_HIDDEN, context.getString(R.string.section_hidden)))

        val hiddenDrawerItems = UserPreferences.getHiddenDrawerItems()
        for (sectionTag in hiddenDrawerItems) {
            if (UserPreferences.isBottomNavigationEnabled()
                    && sectionTag == NavListAdapter.SUBSCRIPTION_LIST_TAG) {
                continue
            }
            settingsDialogItems.add(ReorderDialogItem(ReorderDialogItem.ViewType.Section,
                    sectionTag!!, context.getString(NavigationNames.getLabel(sectionTag))))
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
        UserPreferences.setDrawerItemOrder(Collections.emptyList(), Collections.emptyList())
        if (onSettingsChanged != null) {
            onSettingsChanged!!.run()
        }
    }

    override fun onConfirmed() {
        val hiddenDrawerItems = getTagsAfterHeader(TAG_HIDDEN)
        UserPreferences.setDrawerItemOrder(hiddenDrawerItems, getTagsWithoutHeaders())

        if (hiddenDrawerItems.contains(UserPreferences.getDefaultPage())) {
            for (tag in context.getResources().getStringArray(R.array.nav_drawer_section_tags)) {
                if (!hiddenDrawerItems.contains(tag)) {
                    UserPreferences.setDefaultPage(tag)
                    break
                }
            }
        }

        if (onSettingsChanged != null) {
            onSettingsChanged!!.run()
        }
    }
}
