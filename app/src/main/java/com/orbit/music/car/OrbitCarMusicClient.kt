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
 *
 * 核心功能：
 * 1. 响应车机中控屏音源下拉菜单切换 (onSourceSelected)
 * 2. 声明应用所支持的车载音源类型 (在线音乐 SOURCE_TYPE_ONLINE, 本地音乐 SOURCE_TYPE_LOCAL)
 * 3. 响应方向盘按键与车载多媒体卡片控制 (播放/暂停/上一曲/下一曲/拖动进度条/收藏等)
 * 4. 为车机系统提供当前曲目信息与播放列表
 */
class OrbitCarMusicClient(
    private val context: Context
) : MusicClient() {

    private val playerManager: MusicPlayerManager by lazy {
        MusicPlayerManager.getInstance(context)
    }

    /**
     * 声明本播放器支持的车载播放源类型列表
     * 车机系统的“播放源下拉菜单”会读取此数组，并在下拉栏中创建 Orbit Player 的音源选项！
     */
    override fun getMediaSourceTypeList(): IntArray {
        Log.i(TAG, "getMediaSourceTypeList requested by car system")
        return intArrayOf(SourceType.SOURCE_TYPE_ONLINE, SourceType.SOURCE_TYPE_LOCAL)
    }

    /**
     * 获取当前生效的播放源类型
     */
    override fun getCurrentSourceType(): Int {
        val currentSong = playerManager.playbackState.value.currentSong
        val path = currentSong?.path ?: ""
        return if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("online://")) {
            SourceType.SOURCE_TYPE_ONLINE
        } else {
            SourceType.SOURCE_TYPE_LOCAL
        }
    }

    /**
     * 车主在车机屏幕顶栏或媒体卡片的【播放源下拉菜单】中点击切换至 Orbit Player 时回调！
     *
     * @param source 选中的音源类型 (如 6 为在线音乐, 0 为本地音乐)
     */
    override fun onSourceSelected(source: Int): Boolean {
        Log.i(TAG, "★ 车机播放源下拉菜单切换选中 Orbit Player! source=$source")
        try {
            // 1. 请求车机底层音频播放焦点
            EcarxMediaBridge.takeFocus()
            // 2. 如果当前未在播放，则恢复/启动播放
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
        Log.d(TAG, "onPlay triggered from car")
        playerManager.play()
        return true
    }

    override fun onPause(): Boolean {
        Log.d(TAG, "onPause triggered from car")
        playerManager.pause()
        return true
    }

    override fun onNext(): Boolean {
        Log.d(TAG, "onNext triggered from car")
        playerManager.playNext()
        return true
    }

    override fun onPrevious(): Boolean {
        Log.d(TAG, "onPrevious triggered from car")
        playerManager.playPrevious()
        return true
    }

    override fun onForward(): Boolean {
        Log.d(TAG, "onForward triggered from car")
        playerManager.playNext()
        return true
    }

    override fun onRewind(): Boolean {
        Log.d(TAG, "onRewind triggered from car")
        playerManager.playPrevious()
        return true
    }

    override fun onReplay(): Boolean {
        Log.d(TAG, "onReplay triggered from car")
        playerManager.seekTo(0L)
        playerManager.play()
        return true
    }

    override fun onSeek(position: Long) {
        Log.d(TAG, "onSeek triggered from car: position=$position")
        playerManager.seekTo(position)
    }

    override fun onLoopModeChange(mode: Int): Boolean {
        Log.d(TAG, "onLoopModeChange triggered from car: mode=$mode")
        playerManager.toggleRepeatMode()
        return true
    }

    override fun getCurrentProgress(): Long {
        return playerManager.playbackState.value.currentPositionMs
    }

    override fun getMusicPlaybackInfo(): MusicPlaybackInfo {
        return EcarxMediaBridge.getCurrentPlaybackInfo()
    }

    override fun getPlaylist(source: Int): List<MediaInfo> {
        val playlist = playerManager.playbackState.value.currentPlaylist
        return playlist.mapIndexed { index, song ->
            OrbitCarMediaInfo(song, index)
        }
    }

    override fun onMediaSelected(source: Int, id: String?): Boolean {
        Log.d(TAG, "onMediaSelected: source=$source, id=$id")
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
        return onMediaSelected(mediaInfo?.sourceType ?: 0, mediaInfo?.mediaId)
    }

    override fun onCollect(type: Int, collected: Boolean): Boolean {
        val currentSong = playerManager.playbackState.value.currentSong ?: return false
        playerManager.updateSongFavorite(currentSong.path, collected)
        return true
    }

    companion object {
        private const val TAG = "OrbitCarClient"
    }
}
