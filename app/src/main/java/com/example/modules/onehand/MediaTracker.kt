package com.example.modules.onehand

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Dernière application audio : repérée par sa notification de lecture (vue par le service
 * d’accessibilité), dont on récupère la session média. Les boutons Lecture / Suivant /
 * Précédent pilotent alors CE flux ; sans session connue, on envoie une touche média
 * qu’Android remet à la dernière appli qui a joué du son.
 */
object MediaTracker {
    private const val PREFS = "media_tracker"
    private const val KEY_PACKAGE = "last_package"

    @Volatile private var controller: MediaController? = null

    fun onNotification(context: Context, packageName: String, notification: Notification) {
        val token = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                notification.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION)
            }
        }.getOrNull() ?: return
        controller = runCatching { MediaController(context.applicationContext, token) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PACKAGE, packageName).apply()
    }

    fun hasListenerAccess(context: Context): Boolean =
        androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun openListenerSettings(context: Context) {
        val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /**
     * Relit les sessions média actives (accès aux notifications requis) : la première qui joue,
     * sinon la plus récente, est celle qui reçoit les commandes, comme pour les touches média.
     */
    fun refresh(context: Context) {
        if (!hasListenerAccess(context)) return
        val manager = context.getSystemService(android.media.session.MediaSessionManager::class.java) ?: return
        val sessions = runCatching {
            manager.getActiveSessions(android.content.ComponentName(context, MediaListenerService::class.java))
        }.getOrNull() ?: return
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
            ?: return
        if (pick.sessionToken != controller?.sessionToken) controller = pick
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_PACKAGE, pick.packageName).apply()
    }

    /** Session pilotée actuellement (pour suivre les changements de titre). */
    fun activeController(): MediaController? = controller

    fun currentArtist(): String? =
        controller?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }

    fun lastPackage(context: Context): String? =
        controller?.packageName ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PACKAGE, null)

    fun appIcon(context: Context): Drawable? {
        val pkg = lastPackage(context) ?: return null
        return runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    fun appLabel(context: Context): String? {
        val pkg = lastPackage(context) ?: return null
        return runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()
    }

    /** Titre en cours, s’il est connu. */
    fun currentTitle(): String? =
        controller?.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }

    fun isPlaying(): Boolean = controller?.playbackState?.state == PlaybackState.STATE_PLAYING

    fun playPause(context: Context): Boolean {
        refresh(context)
        val c = controller
        if (c != null && runCatching {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
                else c.transportControls.play()
            }.isSuccess
        ) return true
        return sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    }

    fun next(context: Context): Boolean {
        refresh(context)
        val c = controller
        if (c != null && runCatching { c.transportControls.skipToNext() }.isSuccess) return true
        return sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(context: Context): Boolean {
        refresh(context)
        val c = controller
        if (c != null && runCatching { c.transportControls.skipToPrevious() }.isSuccess) return true
        return sendMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun sendMediaKey(context: Context, keyCode: Int): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val now = SystemClock.uptimeMillis()
        return runCatching {
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
            true
        }.getOrDefault(false)
    }
}
