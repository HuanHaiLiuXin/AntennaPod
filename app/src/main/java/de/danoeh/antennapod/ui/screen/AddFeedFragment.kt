package de.danoeh.antennapod.ui.screen

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

import androidx.core.widget.NestedScrollView
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment

import de.danoeh.antennapod.R
import de.danoeh.antennapod.activity.MainActivity
import de.danoeh.antennapod.activity.OpmlImportActivity
import de.danoeh.antennapod.event.MessageEvent
import de.danoeh.antennapod.model.feed.Feed
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter
import de.danoeh.antennapod.model.feed.SortOrder
import de.danoeh.antennapod.databinding.AddfeedBinding
import de.danoeh.antennapod.ui.common.databinding.EditTextDialogBinding
import de.danoeh.antennapod.net.discovery.CombinedSearcher
import de.danoeh.antennapod.net.discovery.FyydPodcastSearcher
import de.danoeh.antennapod.net.discovery.ItunesPodcastSearcher
import de.danoeh.antennapod.net.discovery.PodcastIndexPodcastSearcher
import de.danoeh.antennapod.ui.appstartintent.OnlineFeedviewActivityStarter
import de.danoeh.antennapod.ui.common.Keyboard
import de.danoeh.antennapod.ui.discovery.OnlineSearchFragment
import de.danoeh.antennapod.ui.screen.feed.FeedItemlistFragment
import de.danoeh.antennapod.ui.common.LiftOnScrollListener
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.greenrobot.eventbus.EventBus

import java.util.Collections

/**
 * Provides actions for adding new podcast subscriptions.
 */
class AddFeedFragment : Fragment() {

    companion object {
        const val TAG = "AddFeedFragment"
        private const val KEY_UP_ARROW = "up_arrow"
    }

    private var viewBinding: AddfeedBinding? = null
    private var disposable: Disposable? = null
    private var activity: MainActivity? = null
    private var displayUpArrow = false

    private val chooseOpmlImportPathLauncher: ActivityResultLauncher<String> =
            registerForActivityResult(ActivityResultContracts.GetContent()) { chooseOpmlImportPathResult(it) }
    private val addLocalFolderLauncher: ActivityResultLauncher<Uri?> =
            registerForActivityResult(AddLocalFolder()) { addLocalFolderResult(it) }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        viewBinding = AddfeedBinding.inflate(inflater)
        activity = getActivity() as MainActivity

        displayUpArrow = getParentFragmentManager().getBackStackEntryCount() != 0
        if (savedInstanceState != null) {
            displayUpArrow = savedInstanceState.getBoolean(KEY_UP_ARROW)
        }
        (getActivity() as MainActivity).setupToolbarToggle(viewBinding!!.toolbar, displayUpArrow)

        val scrollView = viewBinding!!.getRoot().findViewById<NestedScrollView>(R.id.scrollView)
        scrollView.setOnScrollChangeListener(LiftOnScrollListener(viewBinding!!.appbar))

        viewBinding!!.searchItunesButton.setOnClickListener {
            activity!!.loadChildFragment(OnlineSearchFragment.newInstance(ItunesPodcastSearcher::class.java))
        }
        viewBinding!!.searchFyydButton.setOnClickListener {
            activity!!.loadChildFragment(OnlineSearchFragment.newInstance(FyydPodcastSearcher::class.java))
        }
        viewBinding!!.searchPodcastIndexButton.setOnClickListener {
            activity!!.loadChildFragment(OnlineSearchFragment.newInstance(PodcastIndexPodcastSearcher::class.java))
        }

        viewBinding!!.combinedFeedSearchEditText.setOnEditorActionListener { v, actionId, event ->
            performSearch()
            true
        }

        viewBinding!!.addViaUrlButton.setOnClickListener { showAddViaUrlDialog() }

        viewBinding!!.opmlImportButton.setOnClickListener {
            try {
                chooseOpmlImportPathLauncher.launch("*/*")
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                EventBus.getDefault().post(MessageEvent(getString(R.string.unable_to_start_system_file_manager)))
            }
        }

        viewBinding!!.addLocalFolderButton.setOnClickListener {
            try {
                addLocalFolderLauncher.launch(null)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
                EventBus.getDefault().post(MessageEvent(getString(R.string.unable_to_start_system_file_manager)))
            }
        }
        viewBinding!!.searchButton.setOnClickListener { performSearch() }

        return viewBinding!!.getRoot()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_UP_ARROW, displayUpArrow)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (disposable != null) {
            disposable!!.dispose()
        }
        viewBinding = null
    }

    private fun showAddViaUrlDialog() {
        val builder = MaterialAlertDialogBuilder(getContext()!!)
        builder.setTitle(R.string.add_podcast_by_url)
        val dialogBinding = EditTextDialogBinding.inflate(getLayoutInflater())
        dialogBinding.textInput.setHint(R.string.rss_address)
        dialogBinding.textInput.setInputType(InputType.TYPE_CLASS_TEXT
                or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_URI)

        val clipboard = getContext()!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData: ClipData? = clipboard.getPrimaryClip()
        if (clipData != null && clipData.getItemCount() > 0 && clipData.getItemAt(0).getText() != null) {
            val clipboardContent = clipData.getItemAt(0).getText().toString()
            if (clipboardContent.trim().startsWith("http")) {
                dialogBinding.textInput.setText(clipboardContent.trim())
            }
        }
        builder.setView(dialogBinding.getRoot())
        builder.setPositiveButton(R.string.confirm_label, null)
        builder.setNegativeButton(R.string.cancel_label, null)
        val alertDialog = builder.create()
        alertDialog.show()

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val inputText: Editable = dialogBinding.textInput.getText()!!
            if (!inputText.toString().matches(Regex(Patterns.WEB_URL.pattern()))) {
                dialogBinding.textInputLayout.setError(getText(R.string.rss_address_invalid))
                return@setOnClickListener
            }
            addUrl(inputText.toString())
            alertDialog.dismiss()
        }
    }

    private fun addUrl(url: String) {
        startActivity(OnlineFeedviewActivityStarter(getContext()!!, url).withManualUrl().getIntent())
    }

    private fun performSearch() {
        Keyboard.hide(getActivity()!!)
        viewBinding!!.combinedFeedSearchEditText.clearFocus()
        val query = viewBinding!!.combinedFeedSearchEditText.getText().toString()
        if (query.matches(Regex("http[s]?://.*"))) {
            addUrl(query)
            return
        }
        activity!!.loadChildFragment(OnlineSearchFragment.newInstance(CombinedSearcher::class.java, query))
        viewBinding!!.combinedFeedSearchEditText.post { viewBinding!!.combinedFeedSearchEditText.setText("") }
    }

    private fun chooseOpmlImportPathResult(uri: Uri?) {
        if (uri == null) {
            return
        }
        val intent = Intent(getContext(), OpmlImportActivity::class.java)
        intent.setData(uri)
        startActivity(intent)
    }

    private fun addLocalFolderResult(uri: Uri?) {
        if (uri == null) {
            return
        }
        disposable = Observable.fromCallable<Feed> { addLocalFolder(uri) }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { feed ->
                            val fragment = FeedItemlistFragment.newInstance(feed.getId())
                            (getActivity() as MainActivity).loadChildFragment(fragment)
                        }, { error ->
                            Log.e(TAG, Log.getStackTraceString(error))
                            EventBus.getDefault().post(MessageEvent(error.localizedMessage))
                        })
    }

    private fun addLocalFolder(uri: Uri): Feed {
        getActivity()!!.getContentResolver().takePersistableUriPermission(uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val documentFile = DocumentFile.fromTreeUri(getContext()!!, uri)
        if (documentFile == null) {
            throw IllegalArgumentException("Unable to retrieve document tree")
        }
        var title = documentFile.getName()
        if (title == null) {
            title = getString(R.string.local_folder)
        }
        val dirFeed = Feed(Feed.PREFIX_LOCAL_FOLDER + uri.toString(), null, title)
        dirFeed.setItems(Collections.emptyList())
        dirFeed.setSortOrder(SortOrder.EPISODE_TITLE_A_Z)
        val fromDatabase = FeedDatabaseWriter.updateFeed(getContext()!!, dirFeed, false)
        FeedUpdateManager.getInstance()!!.runOnce(requireContext(), fromDatabase)
        return fromDatabase
    }

    private class AddLocalFolder : ActivityResultContracts.OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            return super.createIntent(context, input)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
}
