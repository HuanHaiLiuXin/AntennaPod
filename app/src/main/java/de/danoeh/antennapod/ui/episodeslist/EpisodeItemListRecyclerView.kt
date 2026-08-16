package de.danoeh.antennapod.ui.episodeslist

import android.content.Context
import android.content.res.Configuration
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.util.Pair
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.danoeh.antennapod.R

class EpisodeItemListRecyclerView : RecyclerView {
    private lateinit var layoutManager: LinearLayoutManager

    constructor(context: Context) : super(ContextThemeWrapper(context, R.style.FastScrollRecyclerView)) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(ContextThemeWrapper(context, R.style.FastScrollRecyclerView), attrs) {
        setup()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(ContextThemeWrapper(context, R.style.FastScrollRecyclerView), attrs, defStyleAttr) {
        setup()
    }

    private fun setup() {
        layoutManager = LinearLayoutManager(getContext())
        setLayoutManager(layoutManager)
        setHasFixedSize(true)
        setClipToPadding(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val horizontalSpacing = getResources().getDimension(R.dimen.additional_horizontal_spacing).toInt()
        setPadding(horizontalSpacing, getPaddingTop(), horizontalSpacing, getPaddingBottom())
    }

    fun getScrollPosition(): Pair<Int, Int> {
        val firstItem = layoutManager.findFirstVisibleItemPosition()
        val firstItemView: View? = layoutManager.findViewByPosition(firstItem)
        val topOffset = if (firstItemView == null) 0 else firstItemView.getTop()
        return Pair(firstItem, topOffset)
    }

    fun restoreScrollPosition(scrollPosition: Pair<Int, Int>?) {
        if (scrollPosition == null || (scrollPosition.first == 0 && scrollPosition.second == 0)) {
            return
        }
        layoutManager.scrollToPositionWithOffset(scrollPosition.first, scrollPosition.second)
    }

    fun isScrolledToBottom(): Boolean {
        val visibleEpisodeCount = getChildCount()
        val totalEpisodeCount = layoutManager.getItemCount()
        val firstVisibleEpisode = layoutManager.findFirstVisibleItemPosition()
        return (totalEpisodeCount - visibleEpisodeCount) <= (firstVisibleEpisode + 3)
    }
}
