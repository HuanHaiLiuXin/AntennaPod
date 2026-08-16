package de.danoeh.antennapod.playback.cast

import android.content.Context
import androidx.annotation.NonNull
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

open class CastStateListener : SessionManagerListener<CastSession> {
    private val castContext: CastContext?

    constructor(context: Context) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            castContext = null
            return
        }
        var castCtx: CastContext?
        try {
            castCtx = CastContext.getSharedInstance(context)
            castCtx.getSessionManager().addSessionManagerListener(this, CastSession::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            castCtx = null
        }
        castContext = castCtx
    }

    fun destroy() {
        if (castContext != null) {
            castContext!!.getSessionManager().removeSessionManagerListener(this, CastSession::class.java)
        }
    }

    override fun onSessionStarting(@NonNull castSession: CastSession) {
    }

    override fun onSessionStarted(@NonNull session: CastSession, @NonNull sessionId: String) {
        onSessionStartedOrEnded()
    }

    override fun onSessionStartFailed(@NonNull castSession: CastSession, i: Int) {
    }

    override fun onSessionEnding(@NonNull castSession: CastSession) {
    }

    override fun onSessionResumed(@NonNull session: CastSession, wasSuspended: Boolean) {
    }

    override fun onSessionResumeFailed(@NonNull castSession: CastSession, i: Int) {
    }

    override fun onSessionSuspended(@NonNull castSession: CastSession, i: Int) {
    }

    override fun onSessionEnded(@NonNull session: CastSession, error: Int) {
        onSessionStartedOrEnded()
    }

    override fun onSessionResuming(@NonNull castSession: CastSession, @NonNull s: String) {
    }

    open fun onSessionStartedOrEnded() {
    }
}
