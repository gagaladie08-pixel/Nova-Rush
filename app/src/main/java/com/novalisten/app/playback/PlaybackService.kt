package com.novalisten.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.novalisten.app.MainActivity
import com.novalisten.app.NovaApplication
import com.novalisten.app.data.*
import com.novalisten.app.enrichment.ArtistPlayback
import com.novalisten.app.enrichment.artistRequestsForTrack
import com.novalisten.app.enrichment.LyricPlayback
import com.novalisten.app.enrichment.MusicQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

@UnstableApi
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val counter = PassageCounter()
    private var passageId: String? = null
    private var lastPlaying = false
    private val lyricPlayback = MutableStateFlow<LyricPlayback?>(null)
    private val app get() = application as NovaApplication
    private val repository get() = (application as NovaApplication).repository
    private val ticker = object : Runnable {
        override fun run() {
            sample()
            handler.postDelayed(this, 200)
        }
    }
    private fun sample(position: Long = player.currentPosition, playing: Boolean = player.isPlaying) {
        val id = player.currentMediaItem?.mediaId ?: return
        if (id != passageId) {
            passageId = id
            counter.newPassage(position, SystemClock.elapsedRealtime(), playing, player.playbackParameters.speed)
        } else if (counter.sample(position, SystemClock.elapsedRealtime(), playing, player.playbackParameters.speed)) {
            repository.recordListen(id)
        }
        lastPlaying = playing
    }
    private fun publishLyricsRequest() {
        val item = player.currentMediaItem
        lyricPlayback.value = item?.let {
            val metadata = it.mediaMetadata
            val duration = metadata.extras?.getLong("duration_ms")?.takeIf { d -> d > 0 } ?: player.duration.coerceAtLeast(0)
            LyricPlayback(it.mediaId, MusicQuery(metadata.title?.toString().orEmpty(), metadata.artist?.toString().orEmpty(), metadata.albumTitle?.toString().orEmpty(), duration), player.isPlaying)
        }
    }
    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) { publishLyricsRequest() }
                override fun onIsPlayingChanged(isPlaying: Boolean) { sample(playing = isPlaying) }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) sample(playing = false)
                }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                    val now = SystemClock.elapsedRealtime()
                    val oldId = oldPosition.mediaItem?.mediaId
                    // Credit the tail before a natural transition/seek; never credit the jump.
                    if (oldId != null && oldId == passageId && counter.sample(oldPosition.positionMs, now, lastPlaying, player.playbackParameters.speed)) repository.recordListen(oldId)
                    if (reason == Player.DISCONTINUITY_REASON_SEEK && oldId == newPosition.mediaItem?.mediaId) {
                        counter.rebase(newPosition.positionMs, now, player.isPlaying, player.playbackParameters.speed)
                    } else {
                        passageId = newPosition.mediaItem?.mediaId
                        counter.newPassage(newPosition.positionMs, now, player.isPlaying, player.playbackParameters.speed)
                    }
                    lastPlaying = player.isPlaying
                }
            })
        }
        val intent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        session = MediaSession.Builder(this, player).setSessionActivity(intent).setCallback(object : MediaSession.Callback {
            override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                return if (controller.uid == android.os.Process.myUid() || controller.isTrusted) super.onConnect(session, controller)
                else MediaSession.ConnectionResult.reject()
            }
        }).build()
        handler.post(ticker)
        scope.launch {
            combine(lyricPlayback, repository.data, repository.localDataReady, app.lyricPreferences.automatic) { playback, local, ready, enabled ->
                app.automaticLyrics.onPlayback(playback?.takeUnless { it.trackId in local.hidden }, enabled, playback?.let { local.lyrics[it.trackId] }, ready,
                    stored = playback?.let { local.automaticLyrics[it.trackId] })
            }.collect { }
        }
        scope.launch {
            combine(lyricPlayback, repository.library, repository.data, repository.localDataReady, app.artistPreferences.state) { playback, library, local, ready, settings ->
                val active = playback?.takeIf { it.playing && it.trackId !in local.hidden }
                val artists = if (active == null) emptyList() else artistGroups(playableTracks(library.tracks, local.hidden), local.catalog).filter { group -> group.tracks.any { it.id == active.trackId } }
                val track = active?.let { playing -> library.tracks.firstOrNull { it.id == playing.trackId } }
                val request = track?.let { ArtistPlayback(it.id, artistRequestsForTrack(it, local, artists), true) }
                app.automaticArtists.onPlayback(request, settings, artists.associate { it.id to artistNote(it, local) }, ready)
            }.collect { }
        }
        scope.launch {
            repository.data.collect { local ->
                if (player.currentMediaItem?.mediaId in local.hidden) {
                    val next = (player.currentMediaItemIndex + 1 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId !in local.hidden }
                    if (next == null) { player.stop(); player.clearMediaItems() }
                    else player.seekTo(next, 0)
                }
                for (i in player.mediaItemCount - 1 downTo 0) {
                    if (player.getMediaItemAt(i).mediaId in local.hidden) player.removeMediaItem(i)
                }
            }
        }
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        app.automaticLyrics.stop()
        app.automaticArtists.stop()
        session?.release()
        player.release()
        super.onDestroy()
    }
}
