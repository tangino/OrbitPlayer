package com.orbit.music.car

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ecarx.eas.sdk.mediacenter.MusicPlaybackInfo
import com.ecarx.eas.sdk.mediacenter.SourceType
import com.orbit.music.R
import com.orbit.music.audio.PlaybackState
import com.orbit.music.ui.MainActivity

/**
 * 车机媒体中心实时播放状态信息封装 (适配吉利/领克/银河/极氪 ECARX 架构)
 */
class OrbitCarMusicPlaybackInfo(
    private val context: Context,
    private val state: PlaybackState,
    private val lyricText: String? = null,
    private val currentSentence: String? = null
) : MusicPlaybackInfo() {

    private val currentSong = state.currentSong

    override fun getAppName(): String = "Orbit Player"

    override fun getPackageName(): String = context.packageName

    override fun getAppIcon(): String {
        return "android.resource://${context.packageName}/${R.mipmap.ic_launcher}"
    }

    override fun getTitle(): String {
        return currentSong?.title ?: "Orbit Player"
    }

    override fun getArtist(): String {
        return currentSong?.artist ?: ""
    }

    override fun getAlbum(): String {
        return currentSong?.album ?: ""
    }

    override fun getDuration(): Long {
        return currentSong?.durationMs ?: state.durationMs
    }

    override fun getArtwork(): Uri? {
        val song = currentSong ?: return null
        return song.albumArtUri?.let { Uri.parse(it) }
            ?: Uri.parse("content://com.orbit.music.cover/${song.id}")
    }

    override fun getPlaybackStatus(): Int {
        // 1: 正在播放, 0: 暂停/停止
        return if (state.isPlaying) 1 else 0
    }

    override fun getSourceType(): Int {
        val path = currentSong?.path ?: ""
        return if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("online://")) {
            SourceType.SOURCE_TYPE_ONLINE
        } else {
            SourceType.SOURCE_TYPE_LOCAL
        }
    }

    override fun getLyricContent(): String? = lyricText

    override fun getCurrentLyricSentence(): String? = currentSentence

    override fun getUuid(): String {
        return "${getTitle()}|${getArtist()}"
    }

    override fun getPlayingItemPositionInQueue(): Int {
        return state.currentIndex.coerceAtLeast(0)
    }

    override fun getLaunchIntent(): PendingIntent? {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun getPlayerIntent(): PendingIntent? {
        return getLaunchIntent()
    }

    override fun isCollected(): Boolean {
        return currentSong?.isFavorite == true
    }

    override fun isSupportCollect(): Boolean = true

    override fun isSupportLoopModeSwitch(): Boolean = true
}
