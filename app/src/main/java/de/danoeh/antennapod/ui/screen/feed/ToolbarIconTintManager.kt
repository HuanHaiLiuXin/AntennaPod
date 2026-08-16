package de.danoeh.antennapod.ui.screen.feed

import android.graphics.PorterDuff.Mode
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.view.Menu

import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar

import de.danoeh.antennapod.ui.common.OnCollapseChangeListener

/**
 * A manager that automatically finds all icons in a collapsable toolbar and tints them according to the collapse state
 * of the toolbar.
 */
class ToolbarIconTintManager(private val toolbar: MaterialToolbar, collapsingToolbar: CollapsingToolbarLayout) :
        OnCollapseChangeListener(collapsingToolbar) {

    init {
        this.onCollapseChanged(false)
    }

    override fun onCollapseChanged(isCollapsed: Boolean) {
        val filter = if (isCollapsed) null else PorterDuffColorFilter(0xffffffff.toInt(), Mode.SRC_ATOP)

        safeSetColorFilter(toolbar.getNavigationIcon(), filter)
        safeSetColorFilter(toolbar.getOverflowIcon(), filter)
        safeSetColorFilter(toolbar.getCollapseIcon(), filter)

        val menu = toolbar.getMenu()
        for (i in 0 until menu.size()) {
            val icon = menu.getItem(i).getIcon()
            safeSetColorFilter(icon, filter)
            menu.getItem(i).setIcon(icon)
        }
    }

    private fun safeSetColorFilter(icon: Drawable?, filter: PorterDuffColorFilter?) {
        if (icon != null) {
            icon.setColorFilter(filter)
        }
    }
}
