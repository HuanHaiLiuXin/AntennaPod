package de.danoeh.antennapod.ui.common

import android.animation.ValueAnimator
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Workaround for app:liftOnScroll flickering when in SwipeRefreshLayout
 */
class LiftOnScrollListener(appBar: View) : RecyclerView.OnScrollListener(),
        NestedScrollView.OnScrollChangeListener {
    private val animator: ValueAnimator
    private var animatingToScrolled = false

    init {
        val colorLifted = ThemeUtils.getColorFromAttr(appBar.getContext(), R.attr.colorSurfaceContainer)
        animator = ValueAnimator.ofArgb(colorLifted and 0x00ffffff, colorLifted)
        animator.addUpdateListener { animation -> appBar.setBackgroundColor(animation.getAnimatedValue() as Int) }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        elevate(isScrolled(recyclerView))
    }

    private fun isScrolled(recyclerView: RecyclerView): Boolean {
        val firstItem = (recyclerView.getLayoutManager() as LinearLayoutManager).findFirstVisibleItemPosition()
        if (firstItem < 0) {
            return false
        } else if (firstItem > 0) {
            return true
        }
        val firstItemView = recyclerView.getLayoutManager()!!.findViewByPosition(firstItem)
        if (firstItemView == null) {
            return false
        } else {
            return firstItemView.getTop() < 0
        }
    }

    override fun onScrollChange(v: NestedScrollView, scrollX: Int, scrollY: Int, oldScrollX: Int, oldScrollY: Int) {
        elevate(scrollY != 0)
    }

    private fun elevate(isScrolled: Boolean) {
        if (isScrolled == animatingToScrolled) {
            return
        }
        animatingToScrolled = isScrolled
        if (isScrolled) {
            animator.start()
        } else {
            animator.reverse()
        }
    }
}
