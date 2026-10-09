package com.orbit.music.car

import android.content.Context
import android.util.Log
import com.ecarx.eas.sdk.mediacenter.MediaInfo
import com.ecarx.eas.sdk.mediacenter.MusicClient
import com.ecarx.eas.sdk.mediacenter.MusicPlaybackInfo
import com.ecarx.eas.sdk.mediacenter.SourceType
import com.orbit.music.audio.MusicPlayerManager

/**
 * 亿咖通 (ECARX) / 吉利银河 OS / 领克 Flyme Auto 车载音乐客户端
 * 参考 Flyme Auto 版 QQ音乐 FaMusicClient 重新实现
 *
 * 核心功能：
 * 1. 响应车机中控屏音源下拉菜单切换 (onSourceSelected)
 * 2. 声明应用所支持的车载音源类型 (在线音乐 SOURCE_TYPE_ONLINE = 6)
 * 3. 响应方向盘按键与车载多媒体卡片控制 (播放/暂停/上一曲/下一曲/拖动进度条/收藏等)
 * 4. 向车机系统供给当前曲目信息与播放列表
 */
class OrbitCarMusicClient(
    private val context: Context
) : MusicClient() {

    private val playerManager: MusicPlayerManager by lazy {
        MusicPlayerManager.getInstance(context)
    }

    @Volatile
    var currentPlaybackInfo: MusicPlaybackInfo? = null

    /**
     * 声明本播放器支持的车载播放源类型列表
     * 车机中控系统的“播放源下拉菜单”会读取此数组，并在下拉栏中创建对应音源入口！
     */
    override fun getMediaSourceTypeList(): IntArray {
        Log.i(TAG, "车机请求 getMediaSourceTypeList -> [ONLINE(6), LOCAL(0)]")
        return intArrayOf(SourceType.SOURCE_TYPE_ONLINE, SourceType.SOURCE_TYPE_LOCAL)
    }

    /**
     * 获取当前生效的播放源类型
     */
    override fun getCurrentSourceType(): Int {
        return currentPlaybackInfo?.sourceType ?: SourceType.SOURCE_TYPE_ONLINE
    }

    /**
     * 车主在车机屏幕顶栏或媒体卡片的【播放源下拉菜单】中点击切换至本播放器时回调！
     *
     * @param source 选中的音源类型 (如 6 为在线音乐, 0 为本地音乐)
     */
    override fun onSourceSelected(source: Int): Boolean {
        Log.i(TAG, "★ 车机播放源下拉菜单切换选中当前应用! source=$source")
        try {
            // 1. 立即请求车机底层音频播放焦点
            EcarxMediaBridge.takeFocus()
            // 2. 如果当前未在播放，则启动播放续播
            if (!playerManager.playbackState.value.isPlaying) {
                playerManager.play()
            }
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "onSourceSelected error: ${e.message}", e)
            return false
        }
    }

    override fun onSourceChanged(source: Int, reason: String?): Boolean {
        Log.i(TAG, "onSourceChanged: source=$source, reason=$reason")
        return true
    }

    override fun onPlay(): Boolean {
        Log.i(TAG, "★ 车机触发 onPlay")
        try {
            EcarxMediaBridge.takeFocus()
            playerManager.play()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "onPlay failed: ${e.message}", e)
            return false
        }
    }

    override fun onPause(): Boolean {
        Log.i(TAG, "★ 车机触发 onPause")
        try {
            playerManager.pause()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "onPause failed: ${e.message}", e)
            return false
        }
    }

    override fun onNext(): Boolean {
        Log.i(TAG, "★ 车机触发 onNext (方向盘按键/中控卡片切下一曲)")
        try {
            playerManager.playNext()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "onNext failed: ${e.message}", e)
            return false
        }
    }

    override fun onPrevious(): Boolean {
        Log.i(TAG, "★ 车机触发 onPrevious (方向盘按键/中控卡片切上一曲)")
        try {
            playerManager.playPrevious()
            return true
        } catch (e: Throwable) {
            Log.e(TAG, "onPrevious failed: ${e.message}", e)
            return false
        }
    }

    override fun onForward(): Boolean {
        Log.i(TAG, "onForward triggered from car")
        playerManager.playNext()
        return true
    }

    override fun onRewind(): Boolean {
        Log.i(TAG, "onRewind triggered from car")
        playerManager.playPrevious()
        return true
    }

    override fun onReplay(): Boolean {
        Log.i(TAG, "onReplay triggered from car")
        playerManager.seekTo(0L)
        playerManager.play()
        return true
    }

    override fun onSeek(position: Long) {
        Log.i(TAG, "onSeek triggered from car: position=$position")
        playerManager.seekTo(position)
    }

    override fun onLoopModeChange(mode: Int): Boolean {
        Log.i(TAG, "onLoopModeChange triggered from car: mode=$mode")
        playerManager.toggleRepeatMode()
        return true
    }

    override fun getCurrentProgress(): Long {
        return playerManager.playbackState.value.currentPositionMs
    }

    override fun getMusicPlaybackInfo(): MusicPlaybackInfo? {
        return currentPlaybackInfo ?: EcarxMediaBridge.getCurrentPlaybackInfo()
    }

    /**
     * 向车机提供当前播放列表 (截取最多 300 首，防止跨进程 Binder 传输溢出)
     */
    override fun getPlaylist(source: Int): List<MediaInfo> {
        val playlist = playerManager.playbackState.value.currentPlaylist
        val limitedList = if (playlist.size > 300) playlist.take(300) else playlist
        return limitedList.mapIndexed { index, song ->
            OrbitCarMediaInfo(song, index)
        }
    }

    override fun onMediaSelected(source: Int, id: String?): Boolean {
        Log.i(TAG, "onMediaSelected by id: source=$source, id=$id")
        if (id == null) return false
        val playlist = playerManager.playbackState.value.currentPlaylist
        val index = playlist.indexOfFirst { it.id.toString() == id }
        if (index >= 0) {
            playerManager.playSongList(playlist, index)
            return true
        }
        return false
    }

    override fun onMediaSelected(mediaInfo: MediaInfo?): Boolean {
        Log.i(TAG, "onMediaSelected by mediaInfo: ${mediaInfo?.title}")
        return onMediaSelected(mediaInfo?.sourceType ?: 0, mediaInfo?.mediaId)
    }

    override fun onCollect(type: Int, collected: Boolean): Boolean {
        val currentSong = playerManager.playbackState.value.currentSong ?: return false
        playerManager.updateSongFavorite(currentSong.path, collected)
        return true
    }

    companion object {
        private const val TAG = "OrbitCarMusicClient"
    }
}
