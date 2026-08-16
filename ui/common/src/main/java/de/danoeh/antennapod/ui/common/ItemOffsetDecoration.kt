package de.danoeh.antennapod.ui.common

import android.content.Context
import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class ItemOffsetDecoration(context: Context, itemOffsetHorizontalDp: Int, itemOffsetVerticalDp: Int) : RecyclerView.ItemDecoration() {
    private val itemOffsetHorizontal: Int
    private val itemOffsetVertical: Int

    constructor(context: Context, itemOffsetDp: Int) : this(context, itemOffsetDp, itemOffsetDp)

    init {
        itemOffsetHorizontal = (itemOffsetHorizontalDp * context.getResources().getDisplayMetrics().density).toInt()
        itemOffsetVertical = (itemOffsetVerticalDp * context.getResources().getDisplayMetrics().density).toInt()
    }

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.set(itemOffsetHorizontal, itemOffsetVertical, itemOffsetHorizontal, itemOffsetVertical)
    }
}
