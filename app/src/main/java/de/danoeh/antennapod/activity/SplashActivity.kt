package de.danoeh.antennapod.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast

import de.danoeh.antennapod.system.CrashReportWriter
import de.danoeh.antennapod.storage.database.PodDBAdapter
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Shows the AntennaPod logo while waiting for the main activity to start.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val content = findViewById<View>(android.R.id.content)
        content.getViewTreeObserver().addOnPreDrawListener { false } // Keep splash screen active

        Completable.create { subscriber ->
            // Trigger schema updates
            PodDBAdapter.getInstance().open()
            PodDBAdapter.getInstance().close()
            subscriber.onComplete()
        }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {
                        val intent = Intent(this@SplashActivity, MainActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(0, 0)
                        finish()
                    }, { error ->
                        error.printStackTrace()
                        CrashReportWriter.write(error)
                        Toast.makeText(this, error.getLocalizedMessage(), Toast.LENGTH_LONG).show()
                        finish()
                    })
    }
}
