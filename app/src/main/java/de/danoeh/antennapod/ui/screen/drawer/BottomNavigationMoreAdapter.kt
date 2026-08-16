package de.danoeh.antennapod.ui.screen.drawer

import android.content.Context
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import de.danoeh.antennapod.R

class BottomNavigationMoreAdapter(private val context: Context, private val listItems: List<MenuItem>) :
        ArrayAdapter<MenuItem>(context, R.layout.bottom_navigation_more_listitem, listItems) {

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        var view = view
        if (view == null) {
            view = View.inflate(context, R.layout.bottom_navigation_more_listitem, null)
        }

        val item = listItems[position]
        (view!!.findViewById<ImageView>(R.id.coverImage)).setImageDrawable(item.getIcon())
        (view.findViewById<TextView>(R.id.titleLabel)).setText(item.getTitle())
        return view
    }
}
