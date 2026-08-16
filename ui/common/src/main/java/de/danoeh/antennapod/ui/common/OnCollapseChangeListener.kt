package de.danoeh.antennapod.ui.common

import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout

abstract class OnCollapseChangeListener(private val collapsingToolbar: CollapsingToolbarLayout) :
        AppBarLayout.OnOffsetChangedListener {
    private var wasCollapsed = false

    override fun onOffsetChanged(appBarLayout: AppBarLayout, offset: Int) {
        val isCollapsed = collapsingToolbar.getHeight() + offset <
                collapsingToolbar.getScrimVisibleHeightTrigger()
        if (wasCollapsed != isCollapsed) {
            wasCollapsed = isCollapsed
            onCollapseChanged(isCollapsed)
        }
    }

    abstract fun onCollapseChanged(isCollapsed: Boolean)
}
