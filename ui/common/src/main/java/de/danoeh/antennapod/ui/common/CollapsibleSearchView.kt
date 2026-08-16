package de.danoeh.antennapod.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.CollapsibleActionView
import android.view.ViewGroup

import androidx.appcompat.widget.SearchView

/**
 * A SearchView implementation that can be used as an expanded action view.
 * It overrides the default behavior to take the full width of the screen.
 */
class CollapsibleSearchView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        SearchView(context, attrs, defStyleAttr), CollapsibleActionView {

    override fun onActionViewExpanded() {
        super.onActionViewExpanded()
        val params = getLayoutParams()
        if (params != null) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
            setLayoutParams(params)
        }
        setIconifiedByDefault(false)
    }
}
