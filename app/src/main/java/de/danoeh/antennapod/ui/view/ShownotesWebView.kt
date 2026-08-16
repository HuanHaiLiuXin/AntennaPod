package de.danoeh.antennapod.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.ContextMenu
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer

import com.google.android.material.snackbar.Snackbar

import de.danoeh.antennapod.R
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.ui.MenuItemUtils
import de.danoeh.antennapod.ui.common.Converter
import de.danoeh.antennapod.ui.common.IntentUtils
import de.danoeh.antennapod.net.common.NetworkUtils
import de.danoeh.antennapod.ui.share.ShareUtils
import de.danoeh.antennapod.ui.cleaner.ShownotesCleaner
import org.greenrobot.eventbus.EventBus

import kotlin.math.max

class ShownotesWebView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        WebView(context, attrs, defStyleAttr), View.OnLongClickListener {
    companion object {
        private const val TAG = "ShownotesWebView"
    }

    /**
     * URL that was selected via long-press.
     */
    private var selectedUrl: String? = null
    private var timecodeSelectedListener: Consumer<Int>? = null
    private var pageFinishedListener: Runnable? = null

    init {
        setup()
    }

    private fun setup() {
        setBackgroundColor(Color.TRANSPARENT)
        if (!NetworkUtils.networkAvailable()) {
            getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK)
            // Use cached resources, even if they have expired
        }
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW)
        getSettings().setUseWideViewPort(false)
        getSettings().setLoadWithOverviewMode(true)
        setOnLongClickListener(this)

        setWebViewClient(object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (ShownotesCleaner.isTimecodeLink(url) && timecodeSelectedListener != null) {
                    timecodeSelectedListener!!.accept(ShownotesCleaner.getTimecodeLinkTime(url))
                } else {
                    IntentUtils.openInBrowser(getContext(), url)
                }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished")
                if (pageFinishedListener != null) {
                    pageFinishedListener!!.run()
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val parent = view.getParent() as ViewGroup?
                if (parent == null) {
                    return true
                }
                val params = parent.getLayoutParams()
                val errorText = TextView(getContext())
                val position = parent.indexOfChild(view)
                parent.removeView(view)
                parent.addView(errorText, position, params)
                val padding = (40 * getContext().getResources().getDisplayMetrics().density).toInt()
                errorText.setPadding(padding, padding, padding, padding)
                errorText.setGravity(Gravity.CENTER)
                errorText.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER)
                errorText.setText("Your Android System WebView crashed. Try restarting the phone. If this happens "
                        + "repeatedly, contact the phone manufacturer or the creator of your custom ROM.")
                return true
            }
        })
    }

    override fun onLongClick(v: View): Boolean {
        val r = getHitTestResult()
        if (r != null && r.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            Log.d(TAG, "Link of webview was long-pressed. Extra: " + r.getExtra())
            selectedUrl = r.getExtra()
            showContextMenu()
            return true
        } else if (r != null && r.getType() == WebView.HitTestResult.EMAIL_TYPE) {
            Log.d(TAG, "E-Mail of webview was long-pressed. Extra: " + r.getExtra())
            val clipboardManager = ContextCompat.getSystemService(this.getContext(),
                    ClipboardManager::class.java)
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("AntennaPod", r.getExtra()))
            }
            if (Build.VERSION.SDK_INT <= 32) {
                EventBus.getDefault().post(MessageEvent(
                        getContext().getResources().getString(R.string.copied_to_clipboard)))
            }
            return true
        }
        selectedUrl = null
        return false
    }

    fun onContextItemSelected(item: MenuItem): Boolean {
        if (selectedUrl == null) {
            return false
        }

        val itemId = item.getItemId()
        if (itemId == R.id.open_in_browser_item) {
            IntentUtils.openInBrowser(getContext(), selectedUrl!!)
        } else if (itemId == R.id.share_url_item) {
            ShareUtils.shareLink(getContext(), selectedUrl!!)
        } else if (itemId == R.id.copy_url_item) {
            val clipData = ClipData.newPlainText(selectedUrl, selectedUrl)
            val cm = getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clipData)
            if (Build.VERSION.SDK_INT < 32) {
                val s = Snackbar.make(this, R.string.copied_to_clipboard, Snackbar.LENGTH_LONG)
                s.getView().setElevation(100f)
                s.show()
            }
        } else if (itemId == R.id.go_to_position_item) {
            if (ShownotesCleaner.isTimecodeLink(selectedUrl!!) && timecodeSelectedListener != null) {
                timecodeSelectedListener!!.accept(ShownotesCleaner.getTimecodeLinkTime(selectedUrl!!))
            } else {
                Log.e(TAG, "Selected go_to_position_item, but URL was no timecode link: " + selectedUrl)
            }
        } else {
            selectedUrl = null
            return false
        }
        selectedUrl = null
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu) {
        super.onCreateContextMenu(menu)
        if (selectedUrl == null) {
            return
        }

        if (ShownotesCleaner.isTimecodeLink(selectedUrl!!)) {
            menu.add(Menu.NONE, R.id.go_to_position_item, Menu.NONE, R.string.go_to_position_label)
            menu.setHeaderTitle(Converter.getDurationStringLong(ShownotesCleaner.getTimecodeLinkTime(selectedUrl!!)))
        } else {
            val uri = Uri.parse(selectedUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri)
            if (IntentUtils.isCallable(getContext(), intent)) {
                menu.add(Menu.NONE, R.id.open_in_browser_item, Menu.NONE, R.string.open_in_browser_label)
            }
            menu.add(Menu.NONE, R.id.copy_url_item, Menu.NONE, R.string.copy_url_label)
            menu.add(Menu.NONE, R.id.share_url_item, Menu.NONE, R.string.share_url_label)
            menu.setHeaderTitle(selectedUrl)
        }
        MenuItemUtils.setOnClickListeners(menu, { onContextItemSelected(it) })
    }

    fun setTimecodeSelectedListener(timecodeSelectedListener: Consumer<Int>) {
        this.timecodeSelectedListener = timecodeSelectedListener
    }

    fun setPageFinishedListener(pageFinishedListener: Runnable) {
        this.pageFinishedListener = pageFinishedListener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(max(getMeasuredWidth(), getMinimumWidth()),
                max(getMeasuredHeight(), getMinimumHeight()))
    }
}
