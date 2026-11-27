package com.example.modules.automate

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings

/**
 * Son joué par l’action « Jouer un son » d’une routine : son de notification du téléphone
 * ou son choisi. Il respecte le mode silencieux, sauf si l’action demande de passer outre
 * (il est alors joué comme une alarme).
 */
object RoutineSound {

    /** Garde le son en cours : un Ringtone libéré trop tôt peut s’arrêter net. */
    private var current: Ringtone? = null

    fun uriFor(raw: String): Uri =
        raw.takeIf { it.isNotBlank() }?.let { Uri.parse(it) } ?: Settings.System.DEFAULT_NOTIFICATION_URI

    fun title(context: Context, raw: String): String {
        if (raw.isBlank()) return "Son de notification du téléphone"
        return runCatching { RingtoneManager.getRingtone(context, uriFor(raw))?.getTitle(context) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: "Son choisi"
    }

    /** @return le libellé à inscrire au journal. */
    fun play(context: Context, raw: String, evenWhenSilent: Boolean): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (!evenWhenSilent && am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            return "Son non joué (téléphone en silencieux)"
        }
        val ringtone = runCatching { RingtoneManager.getRingtone(context, uriFor(raw)) }.getOrNull()
            ?: runCatching { RingtoneManager.getRingtone(context, Settings.System.DEFAULT_NOTIFICATION_URI) }.getOrNull()
            ?: return "Son indisponible"
        return runCatching {
            ringtone.audioAttributes = AudioAttributes.Builder()
                .setUsage(if (evenWhenSilent) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            current?.stop()
            current = ringtone
            ringtone.play()
            "Son joué"
        }.getOrDefault("Son indisponible")
    }

    fun stop() {
        runCatching { current?.stop() }
        current = null
    }
}
