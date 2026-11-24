package com.novalisten.app.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Notification du lecteur aux couleurs de l'univers actif.
 *
 * Android dessine lui-même la carte (surtout depuis Android 13) : on ne peut pas y imposer un fond
 * libre. Ce qui est réellement sous notre contrôle est appliqué ici : la couleur d'accent, l'icône
 * et le canal nommés par NovaListen ; la pochette est fournie en grand par la MediaSession.
 */
@OptIn(UnstableApi::class)
class UniverseNotification(context: Context, private val accent: () -> Int) : DefaultMediaNotificationProvider(context) {

    init {
        setSmallIcon(com.novalisten.app.R.drawable.ic_nova)
        setColor(accent())
    }

    /** État du thème relu à chaque construction de la notification, jamais figé. */
    fun refreshColor() = setColor(accent())
}
