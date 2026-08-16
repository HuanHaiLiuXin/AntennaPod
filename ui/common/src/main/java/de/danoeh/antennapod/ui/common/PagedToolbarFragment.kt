package de.danoeh.antennapod.ui.common

import com.google.android.material.appbar.MaterialToolbar
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2

/**
 * Fragment with a ViewPager where the displayed items influence the top toolbar's menu.
 * All items share the same general menu items and are just allowed to show/hide them.
 */
abstract class PagedToolbarFragment : Fragment() {

    protected fun setupPagedToolbar(toolbar: MaterialToolbar, viewPager: ViewPager2) {

        toolbar.setOnMenuItemClickListener { item ->
            if (this.onOptionsItemSelected(item)) {
                return@setOnMenuItemClickListener true
            }
            val child = getChildFragmentManager().findFragmentByTag("f" + viewPager.getCurrentItem())
            if (child != null) {
                return@setOnMenuItemClickListener child.onOptionsItemSelected(item)
            }
            return@setOnMenuItemClickListener false
        }
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            private var selectedPosition = 0

            override fun onPageSelected(position: Int) {
                selectedPosition = position
            }

            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                if (state == ViewPager2.SCROLL_STATE_IDLE) {
                    val child = getChildFragmentManager().findFragmentByTag("f" + selectedPosition)
                    if (child != null) {
                        child.onPrepareOptionsMenu(toolbar.getMenu())
                    }
                }
            }
        })
    }
}
