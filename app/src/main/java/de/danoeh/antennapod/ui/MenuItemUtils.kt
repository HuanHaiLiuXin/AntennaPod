package de.danoeh.antennapod.ui

import android.view.Menu
import android.view.MenuItem

/**
 * Utilities for menu items
 */
class MenuItemUtils {

    companion object {
        /**
         * When pressing a context menu item, Android calls onContextItemSelected
         * for ALL fragments in arbitrary order, not just for the fragment that the
         * context menu was created from. This assigns the listener to every menu item,
         * so that the correct fragment is always called first and can consume the click.
         *
         * Note that Android still calls the onContextItemSelected methods of all fragments
         * when the passed listener returns false.
         */
        @JvmStatic
        fun setOnClickListeners(menu: Menu, listener: MenuItem.OnMenuItemClickListener) {
            for (i in 0 until menu.size()) {
                if (menu.getItem(i).getSubMenu() != null) {
                    setOnClickListeners(menu.getItem(i).getSubMenu()!!, listener)
                }
                menu.getItem(i).setOnMenuItemClickListener(listener)
            }
        }
    }
}
