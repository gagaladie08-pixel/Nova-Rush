package com.novalisten.app.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.toArgb
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import com.google.common.collect.ImmutableList
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import com.novalisten.app.NovaApplication

/**
 * Notification du lecteur aux couleurs de l'univers actif.
 *
 * Android dessine lui-même la carte (surtout depuis Android 13) : on ne peut pas y imposer un fond
 * libre. Ce qui est réellement sous notre contrôle, et qui est appliqué ici : la couleur d'accent,
 * la pochette fournie en grand par la session, et les commandes utiles (file d'attente comprise).
 */
@OptIn(UnstableApi::class)
class UniverseNotification(context: Context) : DefaultMediaNotificationProvider(context) {

    init {
        setSmallIcon(com.novalisten.app.R.drawable.ic_nova)
        setNotificationId(NOTIFICATION_ID)
        setChannelId(CHANNEL_ID)
        setChannelName(com.novalisten.app.R.string.notification_channel_playback)
    }

    override fun getMediaButtons(session: MediaSession, playerCommands: Player.Commands,
                                 showPauseButton: Boolean): ImmutableList<CommandButton> {
        val universe = (context.applicationContext as? NovaApplication)?.themes?.state?.value?.universe
        val accent = universe?.accent ?: return super.getMediaButtons(session, playerCommands, showPauseButton)
        setColor(accent.toArgb())
        return super.getMediaButtons(session, playerCommands, showPauseButton)
    }

    companion object {
        const val NOTIFICATION_ID = 1102
        const val CHANNEL_ID = "novalisten_playback"
    }
}
