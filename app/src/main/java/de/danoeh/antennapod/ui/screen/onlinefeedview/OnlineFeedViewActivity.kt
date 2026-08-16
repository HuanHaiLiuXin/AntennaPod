package de.danoeh.antennapod.ui.screen.onlinefeedview

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.UiThread
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import de.danoeh.antennapod.R
import de.danoeh.antennapod.databinding.OnlinefeedviewActivityBinding
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.download.DownloadError
import de.danoeh.antennapod.model.download.DownloadRequest
import de.danoeh.antennapod.model.download.DownloadResult
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.common.UrlChecker
import de.danoeh.antennapod.net.discovery.CombinedSearcher
import de.danoeh.antennapod.net.discovery.FeedUrlNotFoundException
import de.danoeh.antennapod.net.discovery.PodcastSearchResult
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader
import de.danoeh.antennapod.net.download.service.feed.remote.HttpDownloader
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestCreator
import de.danoeh.antennapod.parser.feed.FeedHandler
import de.danoeh.antennapod.parser.feed.FeedHandlerResult
import de.danoeh.antennapod.parser.feed.UnsupportedFeedtypeException
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.database.DBWriter
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.appstartintent.MainActivityStarter
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter.Companion.ARG_FEEDURL
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter.Companion.ARG_WAS_MANUAL_URL
import de.danoeh.antennapod.ui.common.ThemeSwitcher
import de.danoeh.antennapod.ui.common.ThemeUtils
import de.danoeh.antennapod.ui.common.databinding.EditTextDialogBinding
import de.danoeh.antennapod.ui.preferences.screen.ParentalControlDialog
import de.danoeh.antennapod.ui.preferences.screen.synchronization.AuthenticationDialog
import de.danoeh.antennapod.ui.screen.download.DownloadErrorLabel
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.File
import java.io.IOException
import java.util.ArrayList

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 *
 *
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
class OnlineFeedViewActivity : AppCompatActivity() {
    private var selectedDownloadUrl: String? = null
    private var downloader: Downloader? = null
    private var username: String? = null
    private var password: String? = null
    private var isPaused = false
    private var isFeedFoundBySearch = false
    private var dialog: Dialog? = null
    private var download: Disposable? = null
    private var parser: Disposable? = null
    private var viewBinding: OnlinefeedviewActivityBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeSwitcher.getTranslucentTheme(this))
        super.onCreate(savedInstanceState)

        viewBinding = OnlinefeedviewActivityBinding.inflate(getLayoutInflater())
        setContentView(viewBinding!!.getRoot())
        viewBinding!!.transparentBackground.setOnClickListener { finish() }
        viewBinding!!.card.setOnClickListener(null)
        viewBinding!!.card.setCardBackgroundColor(ThemeUtils.getColorFromAttr(this, R.attr.colorSurface))

        var feedUrl: String? = null
        if (getIntent().hasExtra(ARG_FEEDURL)) {
            feedUrl = getIntent().getStringExtra(ARG_FEEDURL)
        } else if (TextUtils.equals(getIntent().getAction(), Intent.ACTION_SEND)) {
            feedUrl = getIntent().getStringExtra(Intent.EXTRA_TEXT)
        } else if (TextUtils.equals(getIntent().getAction(), Intent.ACTION_VIEW)) {
            feedUrl = getIntent().getDataString()
        }

        if (feedUrl == null || UrlChecker.isDeeplinkWithoutUrl(feedUrl)) {
            Log.e(TAG, "feedUrl is null.")
            showNoPodcastFoundError()
        } else {
            Log.d(TAG, "Activity was started with url " + feedUrl)
            if (savedInstanceState != null) {
                username = savedInstanceState.getString("username")
                password = savedInstanceState.getString("password")
            }
            val preparedUrl = UrlChecker.prepareUrl(feedUrl)
            if (UserPreferences.isParentalControlPasswordSet()
                    && UserPreferences.isParentalControlRequireSubscribeSet()) {
                ParentalControlDialog.show(this, { lookupUrlAndDownload(preparedUrl) }, this::finish)
            } else {
                lookupUrlAndDownload(preparedUrl)
            }
        }
    }

    private fun showNoPodcastFoundError() {
        runOnUiThread {
            MaterialAlertDialogBuilder(this@OnlineFeedViewActivity)
                    .setNeutralButton(android.R.string.ok) { dialog, which -> finish() }
                    .setTitle(R.string.error_label)
                    .setMessage(R.string.null_value_podcast_error)
                    .setOnDismissListener { dialog1 -> finish() }
                    .show()
        }
    }

    override fun onStart() {
        super.onStart()
        isPaused = false
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        isPaused = true
        if (downloader != null && !downloader!!.isFinished()) {
            downloader!!.cancel()
        }
        if (dialog != null && dialog!!.isShowing()) {
            dialog!!.dismiss()
        }
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (download != null) {
            download!!.dispose()
        }
        if (parser != null) {
            parser!!.dispose()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("username", username)
        outState.putString("password", password)
    }

    private fun resetIntent(url: String) {
        val intent = Intent()
        intent.putExtra(ARG_FEEDURL, url)
        setIntent(intent)
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun lookupUrlAndDownload(url: String) {
        download = PodcastSearcherRegistry.lookupUrl(url)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(this::downloadIfNotAlreadySubscribed,
                        { error ->
                            if (error is FeedUrlNotFoundException) {
                                tryToRetrieveFeedUrlBySearch(error)
                            } else {
                                showNoPodcastFoundError()
                                Log.e(TAG, Log.getStackTraceString(error))
                            }
                        })
    }

    private fun tryToRetrieveFeedUrlBySearch(error: FeedUrlNotFoundException) {
        Log.d(TAG, "Unable to retrieve feed url, trying to retrieve feed url from search")
        val url = searchFeedUrlByTrackName(error.getTrackName(), error.getArtistName())
        if (url != null) {
            Log.d(TAG, "Successfully retrieve feed url")
            isFeedFoundBySearch = true
            downloadIfNotAlreadySubscribed(url)
        } else {
            showNoPodcastFoundError()
            Log.d(TAG, "Failed to retrieve feed url")
        }
    }

    private fun searchFeedUrlByTrackName(trackName: String?, artistName: String?): String? {
        val searcher = CombinedSearcher()
        val query = trackName + " " + artistName
        val results: List<PodcastSearchResult> = searcher.search(query).blockingGet()
        for (result in results) {
            if (result.feedUrl != null && result.author != null
                    && result.author!!.equals(artistName, ignoreCase = true)
                    && result.title.equals(trackName, ignoreCase = true)) {
                return result.feedUrl
            }
        }
        return null
    }

    private fun downloadIfNotAlreadySubscribed(url: String): Feed? {
        download = Maybe.fromCallable<Feed> {
            val feeds = DBReader.getFeedList()
            for (f in feeds) {
                if (f.getDownloadUrl() == url) {
                    return@fromCallable f
                }
            }
            null
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ subscribedFeed ->
                    if (subscribedFeed.getState() == Feed.STATE_NOT_SUBSCRIBED) {
                        showFeedFragment(subscribedFeed.getId())
                    } else {
                        openFeed(subscribedFeed.getId())
                    }
                }, { error -> Log.e(TAG, Log.getStackTraceString(error)) }, { startFeedDownload(url) })
        return null
    }

    private fun startFeedDownload(url: String) {
        Log.d(TAG, "Starting feed download")
        selectedDownloadUrl = UrlChecker.prepareUrl(url)
        val request = DownloadRequestCreator.create(Feed(selectedDownloadUrl, null))
                .withAuthentication(username, password)
                .withInitiatedByUser(true)
                .build()

        download = Observable.fromCallable {
            downloader = HttpDownloader(request)
            downloader!!.call()
            downloader!!.result
        }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ status -> checkDownloadResult(status, request.getDestination()) },
                        { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }

    private fun checkDownloadResult(status: DownloadResult, destination: String) {
        if (status.isSuccessful()) {
            parseFeed(destination)
        } else if (status.getReason() == DownloadError.ERROR_UNAUTHORIZED) {
            if (!isFinishing() && !isPaused) {
                if (username != null && password != null) {
                    Toast.makeText(this, R.string.download_error_unauthorized, Toast.LENGTH_LONG).show()
                }
                dialog = FeedViewAuthenticationDialog(this@OnlineFeedViewActivity,
                        R.string.authentication_notification_title,
                        downloader!!.getDownloadRequest().getSource()).create()
                dialog!!.show()
            }
        } else {
            showErrorDialog(getString(DownloadErrorLabel.from(status.getReason()!!)), status.getReasonDetailed())
        }
    }

    private fun parseFeed(destination: String) {
        Log.d(TAG, "Parsing feed")
        parser = Maybe.create<Long>({ emitter ->
            val handlerResult = doParseFeed(destination)
            if (handlerResult == null) { // Started another attempt with another url
                emitter.onComplete()
                return@create
            }
            val feed = handlerResult.feed
            feed.setState(Feed.STATE_NOT_SUBSCRIBED)
            feed.setLastRefreshAttempt(System.currentTimeMillis())
            FeedDatabaseWriter.updateFeed(this, feed, false)
            val feedFromDb = DBReader.getFeed(feed.getId(), false, 0, Int.MAX_VALUE)
            feedFromDb!!.getPreferences()!!.setKeepUpdated(false)
            if (username != null && password != null) {
                feedFromDb.getPreferences()!!.setUsername(username)
                feedFromDb.getPreferences()!!.setPassword(password)
            }
            DBWriter.setFeedPreferences(feedFromDb.getPreferences()!!)
            emitter.onSuccess(feed.getId())
        })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::showFeedFragment, { error ->
                    error.printStackTrace()
                    if (error is UnsupportedFeedtypeException
                            && "html".equals(error.getRootElement(), ignoreCase = true)) {
                        if (getIntent().getBooleanExtra(ARG_WAS_MANUAL_URL, false)) {
                            showErrorDialog(getString(R.string.download_error_unsupported_type_html_manual),
                                    error.message)
                        } else {
                            showErrorDialog(getString(R.string.download_error_unsupported_type_html), error.message)
                        }
                        return@subscribe
                    }
                    showErrorDialog(getString(R.string.download_error_parser_exception), error.message)
                })
    }

    /**
     * Try to parse the feed.
     * @return  The FeedHandlerResult if successful.
     * Null if unsuccessful but we started another attempt.
     * @throws Exception If unsuccessful but we do not know a resolution.
     */
    private fun doParseFeed(destination: String): FeedHandlerResult? {
        val handler = FeedHandler()
        val feed = Feed(selectedDownloadUrl, null)
        feed.setLocalFileUrl(destination)
        val destinationFile = File(destination)
        try {
            return handler.parseFeed(feed)
        } catch (e: UnsupportedFeedtypeException) {
            Log.d(TAG, "Unsupported feed type detected")
            if ("html".equals(e.getRootElement(), ignoreCase = true)) {
                val dialogShown = showFeedDiscoveryDialog(destinationFile, selectedDownloadUrl!!)
                if (dialogShown) {
                    return null // We handled the problem
                }
            }
            throw e
        } catch (e: Exception) {
            Log.e(TAG, Log.getStackTraceString(e))
            throw e
        } finally {
            val rc = destinationFile.delete()
            Log.d(TAG, "Deleted feed source file. Result: " + rc)
        }
    }

    private fun showFeedFragment(id: Long) {
        if (isFeedFoundBySearch) {
            Toast.makeText(this, R.string.no_feed_url_podcast_found_by_search, Toast.LENGTH_LONG).show()
        }

        viewBinding!!.progressBar.setVisibility(View.GONE)
        val fragment = FeedItemlistFragment.newInstance(id)
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment, FeedItemlistFragment.TAG)
                .commitAllowingStateLoss()
    }

    private fun openFeed(feedId: Long) {
        // feed.getId() is always 0, we have to retrieve the id from the feed list from the database
        val mainActivityStarter = MainActivityStarter(this)
        mainActivityStarter.withOpenFeed(feedId)
        finish()
        startActivity(mainActivityStarter.getIntent())
    }

    @UiThread
    private fun showErrorDialog(errorMsg: String?, details: String?) {
        if (!isFinishing() && !isPaused) {
            val builder = MaterialAlertDialogBuilder(this)
            builder.setTitle(R.string.error_label)
            if (errorMsg != null) {
                val errorMessage = SpannableString(getString(
                        R.string.download_log_details_message, errorMsg, details, selectedDownloadUrl))
                errorMessage.setSpan(ForegroundColorSpan(0x88888888.toInt()),
                        errorMsg.length, errorMessage.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setMessage(errorMessage)
            } else {
                builder.setMessage(R.string.download_error_error_unknown)
            }
            builder.setPositiveButton(android.R.string.ok) { dialog, which -> dialog.cancel() }
            if (getIntent().getBooleanExtra(ARG_WAS_MANUAL_URL, false)) {
                builder.setNeutralButton(R.string.edit_url_menu) { dialog, which -> editUrl() }
            }
            builder.setOnCancelListener { finish() }
            if (dialog != null && dialog!!.isShowing()) {
                dialog!!.dismiss()
            }
            dialog = builder.show()
            (dialog!!.findViewById<View>(android.R.id.message) as TextView).setTextIsSelectable(true)
        }
    }

    private fun editUrl() {
        val builder = MaterialAlertDialogBuilder(this)
        builder.setTitle(R.string.edit_url_menu)
        val dialogBinding = EditTextDialogBinding.inflate(getLayoutInflater())
        if (downloader != null) {
            dialogBinding.textInput.setInputType(InputType.TYPE_CLASS_TEXT
                    or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI)
            dialogBinding.textInput.setText(downloader!!.getDownloadRequest().getSource())
            dialogBinding.textInput.setHint(R.string.rss_address)
        }
        builder.setView(dialogBinding.getRoot())
        builder.setPositiveButton(R.string.confirm_label) { dialog, which ->
            lookupUrlAndDownload(dialogBinding.textInput.getText().toString())
        }
        builder.setNegativeButton(R.string.cancel_label) { dialog1, which -> dialog1.cancel() }
        builder.setOnCancelListener { finish() }
        builder.show()
    }

    /**
     *
     * @return true if a FeedDiscoveryDialog is shown, false otherwise (e.g., due to no feed found).
     */
    private fun showFeedDiscoveryDialog(feedFile: File, baseUrl: String): Boolean {
        val fd = FeedDiscoverer()
        val urlsMap: Map<String, String>
        try {
            urlsMap = fd.findLinks(feedFile, baseUrl)
            if (urlsMap.isEmpty()) {
                return false
            }
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }

        if (isPaused || isFinishing()) {
            return false
        }

        val titles: MutableList<String> = ArrayList()

        val urls = ArrayList(urlsMap.keys)
        for (url in urls) {
            titles.add(urlsMap[url]!!)
        }

        if (urls.size == 1) {
            // Skip dialog and display the item directly
            resetIntent(urls[0])
            downloadIfNotAlreadySubscribed(urls[0])
            return true
        }

        val adapter = ArrayAdapter<String>(this@OnlineFeedViewActivity,
                R.layout.ellipsize_start_listitem, R.id.txtvTitle, titles)
        val onClickListener = DialogInterface.OnClickListener { dialog, which ->
            val selectedUrl = urls[which]
            dialog.dismiss()
            resetIntent(selectedUrl)
            downloadIfNotAlreadySubscribed(selectedUrl)
        }

        val ab = MaterialAlertDialogBuilder(this@OnlineFeedViewActivity)
                .setTitle(R.string.subscriptions_label)
                .setCancelable(true)
                .setOnCancelListener { finish() }
                .setAdapter(adapter, onClickListener)

        runOnUiThread {
            if (dialog != null && dialog!!.isShowing()) {
                dialog!!.dismiss()
            }
            dialog = ab.show()
        }
        return true
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onEventMainThread(event: MessageEvent) {
        Log.d(TAG, "onEvent(" + event + ")")
        val snackbar = Snackbar.make(findViewById(android.R.id.content), event.message, Snackbar.LENGTH_LONG)
        snackbar.show()
        if (event.action != null) {
            snackbar.setAction(event.actionText, View.OnClickListener { v -> event.action!!.accept(this) })
        }
    }

    private inner class FeedViewAuthenticationDialog(context: Context, titleRes: Int, feedUrl: String) :
            AuthenticationDialog(context, titleRes, true, username, password) {

        private val feedUrl: String

        init {
            this.feedUrl = feedUrl
        }

        override fun onCancelled() {
            super.onCancelled()
            finish()
        }

        override fun onConfirmed(username: String, password: String) {
            this@OnlineFeedViewActivity.username = username
            this@OnlineFeedViewActivity.password = password
            downloadIfNotAlreadySubscribed(feedUrl)
        }
    }

    companion object {
        private const val TAG = "OnlineFeedViewActivity"
    }
}
