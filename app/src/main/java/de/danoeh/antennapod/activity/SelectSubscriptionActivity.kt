package de.danoeh.antennapod.activity

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView

import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target

import java.util.ArrayList

import de.danoeh.antennapod.R
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.common.ThemeSwitcher
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.databinding.SubscriptionSelectionActivityBinding
import de.danoeh.antennapod.model.feed.Feed
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

class SelectSubscriptionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SelectSubscription"
    }

    private var disposable: Disposable? = null
    @Volatile
    private var listItems: List<Feed>? = null

    private var viewBinding: SubscriptionSelectionActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this))
        super.onCreate(savedInstanceState)

        viewBinding = SubscriptionSelectionActivityBinding.inflate(getLayoutInflater())
        setContentView(viewBinding!!.getRoot())
        setSupportActionBar(viewBinding!!.toolbar)
        setTitle(R.string.shortcut_select_subscription)

        viewBinding!!.transparentBackground.setOnClickListener { finish() }
        viewBinding!!.card.setOnClickListener(null)

        loadSubscriptions()

        val checkedPosition = arrayOfNulls<Int>(1)
        viewBinding!!.list.setChoiceMode(ListView.CHOICE_MODE_SINGLE)
        viewBinding!!.list.setOnItemClickListener { listView, view1, position, rowId ->
            checkedPosition[0] = position
        }
        viewBinding!!.shortcutBtn.setOnClickListener {
            if (checkedPosition[0] != null && Intent.ACTION_CREATE_SHORTCUT ==
                    getIntent().getAction()) {
                getBitmapFromUrl(listItems!!.get(checkedPosition[0]!!))
            }
        }

    }

    private fun addShortcut(feed: Feed, bitmap: Bitmap?) {
        val intent = Intent(this, MainActivity::class.java)
        intent.setAction(Intent.ACTION_MAIN)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        intent.putExtra(MainActivityStarter.EXTRA_FEED_ID, feed.getId())
        val id = "subscription-" + feed.getId()
        val icon: IconCompat

        if (bitmap != null) {
            icon = IconCompat.createWithAdaptiveBitmap(bitmap)
        } else {
            icon = IconCompat.createWithResource(this, R.drawable.ic_shortcut_subscriptions)
        }

        val shortcut = ShortcutInfoCompat.Builder(this, id)
                .setShortLabel(feed.getTitle()!!)
                .setLongLabel(feed.getFeedTitle()!!)
                .setIntent(intent)
                .setIcon(icon)
                .build()

        setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this, shortcut))
        finish()
    }

    private fun getBitmapFromUrl(feed: Feed) {
        val iconSize = (128 * getResources().getDisplayMetrics().density).toInt()
        Glide.with(this)
                .asBitmap()
                .load(feed.getImageUrl())
                .apply(RequestOptions.overrideOf(iconSize, iconSize))
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?,
                                              target: Target<Bitmap>, isFirstResource: Boolean): Boolean {
                        addShortcut(feed, null)
                        return true
                    }

                    override fun onResourceReady(resource: Bitmap, model: Any,
                                                 target: Target<Bitmap>, dataSource: DataSource, isFirstResource: Boolean): Boolean {
                        addShortcut(feed, resource)
                        return true
                    }
                }).submit()
    }

    private fun loadSubscriptions() {
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<List<Feed>> { DBReader.getFeedList() }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { result ->
                            listItems = result
                            val titles = ArrayList<String>()
                            for (feed in result) {
                                titles.add(feed.getTitle()!!)
                            }
                            val adapter = ArrayAdapter(this,
                                    R.layout.simple_list_item_multiple_choice_on_start, titles)
                            viewBinding!!.list.setAdapter(adapter)
                        }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
