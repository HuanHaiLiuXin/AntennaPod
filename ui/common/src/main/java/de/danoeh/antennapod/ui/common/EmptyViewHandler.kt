package de.danoeh.antennapod.ui.common

import android.content.Context
import android.database.DataSetObserver
import android.view.Gravity
import android.widget.AbsListView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ListAdapter
import androidx.annotation.DrawableRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView

class EmptyViewHandler(context: Context) {
    private var layoutAdded = false
    private var listAdapter: ListAdapter? = null
    private var recyclerAdapter: RecyclerView.Adapter<*>? = null
    private var listView: View? = null

    private val emptyView: View
    private val tvTitle: TextView
    private val tvMessage: TextView
    private val ivIcon: ImageView
    private val button: Button

    init {
        emptyView = View.inflate(context, R.layout.empty_view_layout, null)
        tvTitle = emptyView.findViewById(R.id.emptyViewTitle)
        tvMessage = emptyView.findViewById(R.id.emptyViewMessage)
        ivIcon = emptyView.findViewById(R.id.emptyViewIcon)
        button = emptyView.findViewById(R.id.button)
    }

    fun setTitle(title: Int) {
        tvTitle.setText(title)
    }

    fun setTitle(title: String) {
        tvTitle.setText(title)
    }

    fun setMessage(message: Int) {
        tvMessage.setText(message)
    }

    fun setMessage(message: String) {
        tvMessage.setText(message)
    }

    fun setButtonText(message: Int) {
        button.setText(message)
    }

    fun setButtonVisibility(visibility: Int) {
        button.setVisibility(visibility)
    }

    fun setButtonOnClickListener(onClickListener: View.OnClickListener) {
        button.setOnClickListener(onClickListener)
    }

    fun setIcon(@DrawableRes icon: Int) {
        ivIcon.setImageResource(icon)
        ivIcon.setVisibility(View.VISIBLE)
    }

    fun hide() {
        emptyView.setVisibility(View.GONE)
    }

    fun attachToListView(listView: AbsListView) {
        if (layoutAdded) {
            throw IllegalStateException("Can not attach EmptyView multiple times")
        }
        addToParentView(listView)
        layoutAdded = true
        this.listView = listView
        listView.setEmptyView(emptyView)
        updateAdapter(listView.getAdapter())
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        if (layoutAdded) {
            throw IllegalStateException("Can not attach EmptyView multiple times")
        }
        addToParentView(recyclerView)
        layoutAdded = true
        this.listView = recyclerView
        updateAdapter(recyclerView.getAdapter())
    }

    private fun addToParentView(view: View) {
        var parent = view.getParent() as ViewGroup?
        while (parent != null) {
            if (parent is RelativeLayout) {
                parent.addView(emptyView)
                val layoutParams =
                        emptyView.getLayoutParams() as RelativeLayout.LayoutParams
                layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE)
                emptyView.setLayoutParams(layoutParams)
                break
            } else if (parent is FrameLayout) {
                parent.addView(emptyView)
                val layoutParams =
                        emptyView.getLayoutParams() as FrameLayout.LayoutParams
                layoutParams.gravity = Gravity.CENTER
                emptyView.setLayoutParams(layoutParams)
                break
            } else if (parent is CoordinatorLayout) {
                parent.addView(emptyView)
                val layoutParams =
                        emptyView.getLayoutParams() as CoordinatorLayout.LayoutParams
                layoutParams.gravity = Gravity.CENTER
                emptyView.setLayoutParams(layoutParams)
                break
            }
            parent = parent.getParent() as ViewGroup?
        }
    }

    fun updateAdapter(adapter: RecyclerView.Adapter<*>?) {
        if (this.recyclerAdapter != null) {
            this.recyclerAdapter!!.unregisterAdapterDataObserver(adapterObserver)
        }
        this.recyclerAdapter = adapter
        if (adapter != null) {
            adapter.registerAdapterDataObserver(adapterObserver)
        }
        updateVisibility()
    }

    private fun updateAdapter(adapter: ListAdapter?) {
        if (this.listAdapter != null) {
            this.listAdapter!!.unregisterDataSetObserver(listAdapterObserver)
        }
        this.listAdapter = adapter
        if (adapter != null) {
            adapter.registerDataSetObserver(listAdapterObserver)
        }
        updateVisibility()
    }

    private val adapterObserver = object : SimpleAdapterDataObserver() {
        override fun anythingChanged() {
            updateVisibility()
        }
    }

    private val listAdapterObserver = object : DataSetObserver() {
        override fun onChanged() {
            updateVisibility()
        }
    }

    fun updateVisibility() {
        val empty: Boolean
        if (recyclerAdapter != null) {
            empty = recyclerAdapter!!.getItemCount() == 0
        } else if (listAdapter != null) {
            empty = listAdapter!!.isEmpty()
        } else {
            empty = true
        }
        emptyView.setVisibility(if (empty) View.VISIBLE else View.GONE)
        listView!!.setVisibility(if (empty) View.INVISIBLE else View.VISIBLE)
    }
}
