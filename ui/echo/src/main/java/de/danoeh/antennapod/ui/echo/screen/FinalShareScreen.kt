package de.danoeh.antennapod.ui.echo.screen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import de.danoeh.antennapod.storage.database.DBReader
import de.danoeh.antennapod.storage.preferences.UserPreferences
import de.danoeh.antennapod.ui.echo.EchoConfig
import de.danoeh.antennapod.ui.echo.R
import de.danoeh.antennapod.ui.echo.background.FinalShareBackground
import de.danoeh.antennapod.ui.echo.databinding.SimpleEchoScreenBinding
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers

import java.io.File
import java.io.FileOutputStream
import java.util.ArrayList
import java.util.concurrent.TimeUnit

class FinalShareScreen(context: Context, layoutInflater: LayoutInflater) : EchoScreen(context) {
    companion object {
        private const val SHARE_SIZE = 1000
        private const val TAG = "FinalShareScreen"
    }

    private val viewBinding: SimpleEchoScreenBinding
    private val favoritePodNames = ArrayList<String>()
    private val favoritePodImages = ArrayList<Drawable>()
    private val background: FinalShareBackground
    private var disposable: Disposable? = null

    init {
        viewBinding = SimpleEchoScreenBinding.inflate(layoutInflater)
        viewBinding.actionButton.setOnClickListener { share() }
        viewBinding.actionButton.setCompoundDrawablesWithIntrinsicBounds(ResourcesCompat.getDrawable(
                context.getResources(), R.drawable.ic_share, context.getTheme()), null, null, null)
        viewBinding.actionButton.setVisibility(View.VISIBLE)
        viewBinding.actionButton.setText(R.string.share_label)
        background = FinalShareBackground(context, favoritePodNames, favoritePodImages)
        viewBinding.backgroundImage.setImageDrawable(background)
    }

    override fun getView(): View {
        return viewBinding.getRoot()
    }

    override fun postInvalidate() {
        viewBinding.backgroundImage.postInvalidate()
    }

    private fun share() {
        try {
            val bitmap = Bitmap.createBitmap(SHARE_SIZE, SHARE_SIZE, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            background.setBounds(0, 0, canvas.getWidth(), canvas.getHeight())
            background.draw(canvas)
            viewBinding.backgroundImage.setImageDrawable(null)
            viewBinding.backgroundImage.setImageDrawable(background)
            val file = File(UserPreferences.getDataFolder(null), "AntennaPodEcho.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
            stream.close()

            val fileUri = FileProvider.getUriForFile(context, context.getString(R.string.provider_authority), file)
            ShareCompat.IntentBuilder(context)
                    .setType("image/png")
                    .addStream(fileUri)
                    .setText(context.getString(R.string.echo_share, EchoConfig.RELEASE_YEAR))
                    .setChooserTitle(R.string.share_file_label)
                    .startChooser()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun startLoading(statisticsData: DBReader.StatisticsResult) {
        favoritePodNames.clear()
        var i = 0
        while (i < 5 && i < statisticsData.feedTime.size) {
            favoritePodNames.add(statisticsData.feedTime.get(i).feed.getTitle()!!)
            i++
        }
        if (disposable != null) {
            disposable!!.dispose()
        }
        disposable = Observable.fromCallable<DBReader.StatisticsResult>(
                {
                    favoritePodImages.clear()
                    var j = 0
                    while (j < 5 && j < statisticsData.feedTime.size) {
                        var cover = BitmapDrawable(context.getResources(), null as Bitmap?)
                        try {
                            val size = SHARE_SIZE / 3
                            val radius = if (j == 0) size / 16 else size / 8
                            cover = BitmapDrawable(context.getResources(), Glide.with(context)
                                    .asBitmap()
                                    .load(statisticsData.feedTime.get(j).feed.getImageUrl())
                                    .apply(RequestOptions()
                                            .fitCenter()
                                            .transform(RoundedCorners(radius)))
                                    .submit(size, size)
                                    .get(5, TimeUnit.SECONDS))
                        } catch (e: Exception) {
                            Log.d(TAG, "Loading cover: " + e.message)
                        }
                        favoritePodImages.add(cover)
                        j++
                    }
                    statisticsData
                })
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ }, { error -> Log.e(TAG, Log.getStackTraceString(error)) })
    }
}
