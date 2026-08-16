package de.danoeh.antennapod.ui.episodeslist

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import de.danoeh.antennapod.R

/**
 * Utility methods for the more_content_list_footer layout.
 */
class MoreContentListFooterUtil(private val root: View) {

    private var loading = false

    private var listener: Listener? = null

    init {
        root.setOnClickListener {
            if (listener != null && !loading) {
                listener!!.onClick()
            }
        }
    }

    fun setLoadingState(newState: Boolean) {
        val imageView = root.findViewById<ImageView>(R.id.imgExpand)
        val progressBar = root.findViewById<ProgressBar>(R.id.progBar)
        if (newState) {
            imageView.setVisibility(View.GONE)
            progressBar.setVisibility(View.VISIBLE)
        } else {
            imageView.setVisibility(View.VISIBLE)
            progressBar.setVisibility(View.GONE)
        }
        loading = newState
    }

    fun setClickListener(l: Listener) {
        listener = l
    }

    interface Listener {
        fun onClick()
    }

    fun getRoot(): View {
        return root
    }
}
