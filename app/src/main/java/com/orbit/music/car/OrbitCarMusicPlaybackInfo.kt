package com.orbit.music.car

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ecarx.eas.sdk.mediacenter.MusicPlaybackInfo
import com.ecarx.eas.sdk.mediacenter.SourceType
import com.orbit.music.R
import com.orbit.music.audio.PlaybackState
import com.orbit.music.audio.RepeatMode
import com.orbit.music.ui.MainActivity

/**
 * 车机媒体中心实时播放状态信息封装 (适配吉利/领克/银河/极氪 ECARX 架构)
 * 参考 Flyme Auto 版 QQ音乐 FaPlaybackInfo 设计
 */
class OrbitCarMusicPlaybackInfo(
    private val context: Context,
    private val state: PlaybackState,
    private val lyricText: String? = null,
    private val currentSentence: String? = null
) : MusicPlaybackInfo() {

    private val currentSong = state.currentSong

    override fun getAppName(): String = "恒星律动"

    override fun getPackageName(): String = context.packageName

    override fun getAppIcon(): String {
        return "android.resource://${context.packageName}/${R.mipmap.ic_launcher}"
    }

    override fun getTitle(): String {
        return currentSong?.title ?: "恒星律动"
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

    override fun getArtwork(): Uri {
        val uriStr = currentSong?.albumArtUri
        return if (!uriStr.isNullOrEmpty()) {
            Uri.parse(uriStr)
        } else if (currentSong != null) {
            Uri.parse("content://com.orbit.music.cover/${currentSong.id}")
        } else {
            Uri.EMPTY
        }
    }

    /**
     * 车机底层播放状态映射 (与 ECARX 系统 / QQ音乐 FaPlaybackInfo 完全一致):
     * 3: 正在播放 (PLAYING)
     * 1: 暂停 (PAUSED)
     * 0: 停止/空闲 (STOPPED)
     */
    override fun getPlaybackStatus(): Int {
        return if (state.isPlaying) 3 else 1
    }

    /**
     * 循环模式映射:
     * 0: 顺序循环 / 列表循环
     * 1: 单曲循环
     * 2: 随机播放
     */
    override fun getLoopMode(): Int {
        return if (state.isShuffleEnabled) {
            2
        } else {
            when (state.repeatMode) {
                RepeatMode.ONE -> 1
                RepeatMode.ALL -> 0
                RepeatMode.OFF -> 0
            }
        }
    }

    override fun getSourceType(): Int = SourceType.SOURCE_TYPE_ONLINE

    override fun getLyricContent(): String? = lyricText

    override fun getCurrentLyricSentence(): String? = currentSentence

    override fun getUuid(): String {
        return currentSong?.id?.toString() ?: "${getTitle()}|${getArtist()}"
    }

    override fun getPlayingItemPositionInQueue(): Int {
        return state.currentIndex.coerceAtLeast(0)
    }

    override fun getPlayingMediaListId(): String {
        return "orbit_current_playlist"
    }

    override fun getLaunchIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            1000,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    override fun getPlayerIntent(): PendingIntent {
        return getLaunchIntent()
    }

    override fun isCollected(): Boolean {
        return currentSong?.isFavorite == true
    }

    override fun isSupportCollect(): Boolean = true

    override fun isSupportLoopModeSwitch(): Boolean = true
}
