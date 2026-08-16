package de.danoeh.antennapod.ui.common

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import de.danoeh.antennapod.ui.common.databinding.ToolbarActivityBinding

/**
 * Activity showing a toolbar and ensuring that system insets are properly consumed.
 */
open class ToolbarActivity : AppCompatActivity() {
    private var viewBinding: ToolbarActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeSwitcher.getNoTitleTheme(this))
        super.onCreate(savedInstanceState)
        viewBinding = ToolbarActivityBinding.inflate(getLayoutInflater())
        setSupportActionBar(viewBinding!!.toolbar)
        super.setContentView(viewBinding!!.getRoot())
    }

    override fun setContentView(view: View) {
        viewBinding!!.content.removeAllViews()
        viewBinding!!.content.addView(view)
    }

    override fun setContentView(layoutResID: Int) {
        setContentView(View.inflate(this, layoutResID, null))
    }
}
