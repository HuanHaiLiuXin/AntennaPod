package de.danoeh.antennapod.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import com.google.android.material.elevation.SurfaceColors
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.FloatingSelectMenuBinding
import de.danoeh.antennapod.databinding.FloatingSelectMenuItemBinding

class FloatingSelectMenu @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        FrameLayout(context, attrs, defStyleAttr) {
    private var viewBinding: FloatingSelectMenuBinding? = null
    private var menu: Menu? = null
    private var menuItemClickListener: MenuItem.OnMenuItemClickListener? = null

    init {
        setup()
    }

    private fun setup() {
        viewBinding = FloatingSelectMenuBinding.bind(
                View.inflate(getContext(), R.layout.floating_select_menu, null))
        viewBinding!!.card.setCardBackgroundColor(
                SurfaceColors.getColorForElevation(getContext(), 8 * getResources().getDisplayMetrics().density))
        addView(viewBinding!!.getRoot())
        setVisibility(View.GONE)
    }

    fun inflate(@MenuRes menuRes: Int) {
        val popupMenu = PopupMenu(getContext(), View(getContext()))
        popupMenu.inflate(menuRes)
        menu = popupMenu.getMenu()
        updateItemVisibility()
    }

    fun updateItemVisibility() {
        viewBinding!!.selectContainer.removeAllViews()
        if (menu == null) {
            return
        }
        for (i in 0 until menu!!.size()) {
            val item = menu!!.getItem(i)
            if (!item.isVisible()) {
                continue
            }
            val itemBinding = FloatingSelectMenuItemBinding.bind(
                    View.inflate(getContext(), R.layout.floating_select_menu_item, null))
            itemBinding.titleLabel.setText(item.getTitle())
            itemBinding.icon.setImageDrawable(item.getIcon())
            itemBinding.getRoot().setOnClickListener { menuItemClickListener!!.onMenuItemClick(item) }
            viewBinding!!.selectContainer.addView(itemBinding.getRoot())
        }
    }

    fun getMenu(): Menu? {
        return menu
    }

    fun setOnMenuItemClickListener(listener: MenuItem.OnMenuItemClickListener) {
        this.menuItemClickListener = listener
    }

    override fun setVisibility(visibility: Int) {
        if (visibility == View.VISIBLE && getVisibility() != View.VISIBLE) {
            announceForAccessibility(getContext().getString(R.string.multi_select_started_talkback))
            viewBinding!!.scrollView.scrollTo(0, 0)
            updateItemVisibility()
            setAlpha(0f)
            super.setVisibility(View.VISIBLE)
            animate().alpha(1f).setDuration(100).start()
        } else if (visibility != View.VISIBLE && getVisibility() == View.VISIBLE && isAttachedToWindow()) {
            animate().alpha(0f).setDuration(100).withEndAction { super.setVisibility(visibility) }.start()
        } else {
            super.setVisibility(visibility)
            viewBinding!!.scrollView.scrollTo(0, 0)
            updateItemVisibility()
        }
    }
}
