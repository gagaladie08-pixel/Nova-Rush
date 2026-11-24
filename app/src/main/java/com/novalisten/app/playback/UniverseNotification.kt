package com.novalisten.app.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider

/**
 * Notification du lecteur aux couleurs de NovaListen.
 *
 * Android dessine lui-même la carte (surtout depuis Android 13) : on ne peut pas y imposer un fond
 * libre. Ce qui est réellement sous notre contrôle est appliqué ici : l'icône et le canal
 * nommés par l'application ; la pochette est fournie en grand par la MediaSession et reste
 * visible sur l'écran verrouillé comme dans le tiroir de notifications.
 */
@OptIn(UnstableApi::class)
class UniverseNotification(context: Context) : DefaultMediaNotificationProvider(context) {
    init {
        setSmallIcon(com.novalisten.app.R.drawable.ic_nova)
    }
}
