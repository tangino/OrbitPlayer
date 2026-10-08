package com.orbit.music.audio

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.engine.OnlineAudioSourceManager
import com.orbit.music.data.online.engine.SourceScriptManager
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlineSongItem
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

enum class RepeatMode {
    OFF, ALL, ONE
}

enum class ShuffleStrategy {
    STANDARD,
    FAVORITE_FIRST,
    LEAST_PLAYED
}

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffleEnabled: Boolean = false,
    val shuffleStrategy: ShuffleStrategy = ShuffleStrategy.STANDARD,
    val currentPlaylist: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val errorMessage: String? = null
)

class MusicPlayerManager private constructor(private val context: Context) {

    private val effectManager = AudioEffectManager.getInstance(context)
    val visualizerManager = AudioVisualizerManager.getInstance(context)
    val onlineSourceManager = OnlineAudioSourceManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var visualizerWatchdogJob: Job? = null
    private var currentOnlineResolveJob: Job? = null
    private var prefetchJob: Job? = null
    private var plannedNextShuffleIndex: Int? = null
    private var playbackSequenceId: Long = 0L
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val savedRepeatOrdinal = prefs.getInt(KEY_REPEAT_MODE, RepeatMode.ALL.ordinal)
    private val initialRepeatMode = RepeatMode.values().getOrElse(savedRepeatOrdinal) { RepeatMode.ALL }
    private val initialShuffle = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
    private val savedStrategyOrdinal = prefs.getInt(KEY_SHUFFLE_STRATEGY, ShuffleStrategy.STANDARD.ordinal)
    private val initialStrategy = ShuffleStrategy.values().getOrElse(savedStrategyOrdinal) { ShuffleStrategy.STANDARD }

    private val playHistory = ArrayDeque<Int>()
    private val MAX_HISTORY_SIZE = 50
    private var hasRecordedPlayForCurrentSong = false
    private var consecutiveResolveFailures = 0

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)
        .setKeepPostFor302Redirects(true)

    private val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true // 自动管理音频焦点 (电话打入/语音打断自动暂停)
        )
        .setHandleAudioBecomingNoisy(true) // 耳机拔出自动暂停
        .build()

    val exoPlayer: ExoPlayer get() = player

    private val _playbackState = MutableStateFlow(
        PlaybackState(
            repeatMode = initialRepeatMode,
            isShuffleEnabled = initialShuffle,
            shuffleStrategy = initialStrategy
        )
    )
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    init {
        // 同步底层 ExoPlayer 的循环与随机模式，防止状态分裂导致死循环
        player.repeatMode = when (initialRepeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        player.shuffleModeEnabled = initialShuffle

        setupPlayerListener()
        restoreLastPlayedSong()
    }

    /**
     * 启动时从持久化配置中恢复上次播放的歌曲与进度
     */
    private fun restoreLastPlayedSong() {
        val lastSongId = prefs.getLong(KEY_SONG_ID, -1L)
        if (lastSongId != -1L) {
            val title = prefs.getString(KEY_SONG_TITLE, "") ?: ""
            val artist = prefs.getString(KEY_SONG_ARTIST, "") ?: ""
            val album = prefs.getString(KEY_SONG_ALBUM, "") ?: ""
            val albumId = prefs.getLong(KEY_ALBUM_ID, 0L)
            val path = prefs.getString(KEY_SONG_PATH, "") ?: ""
            val size = prefs.getLong(KEY_SIZE, 0L)
            val duration = prefs.getLong(KEY_SONG_DURATION, 0L)
            val artUri = prefs.getString(KEY_SONG_ART_URI, null)
            val lastPos = prefs.getLong(KEY_LAST_POS, 0L)

            if (title.isNotBlank() && path.isNotBlank()) {
                val restoredSong = Song(
                    id = lastSongId,
                    title = title,
                    artist = artist,
                    album = album,
                    albumId = albumId,
                    durationMs = duration,
                    path = path,
                    size = size,
                    albumArtUri = artUri
                )

                val initialProgress = (lastPos.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)

                _playbackState.update {
                    it.copy(
                        currentSong = restoredSong,
                        currentPositionMs = lastPos,
                        durationMs = duration,
                        progress = initialProgress,
                        currentPlaylist = listOf(restoredSong),
                        currentIndex = 0
                    )
                }

                // 预置 MediaItem，用户点击播放可立即无缝开始
                val mediaItem = createMediaItem(restoredSong)
                player.setMediaItem(mediaItem, lastPos)
                player.prepare()
                Log.i(TAG, "Restored last played song: $title by $artist at pos: $lastPos ms")
            }
        }
    }

    /**
     * 当曲库就绪时，自动将单曲恢复队列扩展为完整曲库队列，支持随意切歌
     */
    fun attachFullQueueIfRestored(allSongs: List<Song>) {
        if (allSongs.isEmpty()) return
        val current = _playbackState.value.currentSong ?: return
        // 若当前播放队列只有 1 首或为空，自动无缝挂载全曲库（保留当前正在播放的位置和状态）
        if (_playbackState.value.currentPlaylist.size <= 1) {
            val index = allSongs.indexOfFirst { it.id == current.id }
            val startIndex = if (index >= 0) index else 0
            val targetSong = if (index >= 0) allSongs[index] else current
            val lastPos = player.currentPosition.coerceAtLeast(_playbackState.value.currentPositionMs)
            val isPlaying = player.isPlaying

            val mediaItems = allSongs.map { createMediaItem(it) }

            _playbackState.update {
                it.copy(
                    currentPlaylist = allSongs,
                    currentIndex = startIndex,
                    currentSong = targetSong
                )
            }

            player.setMediaItems(mediaItems, startIndex, lastPos)
            player.prepare()
            if (isPlaying) {
                player.play()
                startVisualizerWatchdog()
            }
            Log.i(TAG, "Attached full queue (${allSongs.size} songs) to player. Current index: $startIndex, isPlaying: $isPlaying")
        }
    }

    /**
     * 保存当前播放的歌曲信息与进度到持久化存储
     */
    fun saveLastPlayedSong(song: Song?, positionMs: Long, syncImmediately: Boolean = false) {
        if (song == null) return
        val editor = prefs.edit()
            .putLong(KEY_SONG_ID, song.id)
            .putString(KEY_SONG_TITLE, song.title)
            .putString(KEY_SONG_ARTIST, song.artist)
            .putString(KEY_SONG_ALBUM, song.album)
            .putLong(KEY_ALBUM_ID, song.albumId)
            .putString(KEY_SONG_PATH, song.path)
            .putLong(KEY_SIZE, song.size)
            .putLong(KEY_SONG_DURATION, song.durationMs)
            .putString(KEY_SONG_ART_URI, song.albumArtUri)
            .putLong(KEY_LAST_POS, positionMs)
        if (syncImmediately) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    /**
     * 高效仅更新当前播放进度
     */
    fun saveCurrentPosition(positionMs: Long, syncImmediately: Boolean = false) {
        val editor = prefs.edit().putLong(KEY_LAST_POS, positionMs)
        if (syncImmediately) {
            editor.commit()
        } else {
            editor.apply()
        }
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playbackState.update { 
                    it.copy(
                        isPlaying = isPlaying,
                        isBuffering = if (isPlaying) false else it.isBuffering
                    ) 
                }
                visualizerManager.setPlaying(isPlaying)
                if (isPlaying) {
                    startProgressTracker()
                    startVisualizerWatchdog()
                } else {
                    stopProgressTracker()
                    stopVisualizerWatchdog()
                    saveLastPlayedSong(_playbackState.value.currentSong, player.currentPosition, syncImmediately = true)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val audioSessionId = player.audioSessionId
                    if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                        Log.i(TAG, "ExoPlayer AudioSession ID: $audioSessionId. Attaching to Equalizer DSP Chain...")
                        effectManager.attachSession(audioSessionId)
                        if (player.isPlaying) {
                            visualizerManager.attachSession(audioSessionId)
                        }
                    }
                    _playbackState.update {
                        it.copy(
                            durationMs = if (player.duration > 0) player.duration else it.durationMs,
                            isBuffering = false,
                            errorMessage = null
                        )
                    }
                } else if (playbackState == Player.STATE_BUFFERING) {
                    _playbackState.update { 
                        it.copy(
                            isBuffering = true
                        ) 
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    _playbackState.update { it.copy(isBuffering = false) }
                    handlePlaybackEnded()
                } else if (playbackState == Player.STATE_IDLE) {
                    _playbackState.update { it.copy(isBuffering = false) }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val cause = error.cause
                val errorMsg = when {
                    // HTTP 状态码错误 (403, 404, 500 等)
                    cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException -> {
                        when (cause.responseCode) {
                            403 -> "音频访问受限 (HTTP 403: 防盗链或直链已过期)"
                            404 -> "音频资源不存在 (HTTP 404: 资源已失效)"
                            500, 502, 503 -> "音源服务器无响应 (HTTP ${cause.responseCode})"
                            else -> "网络请求异常 (HTTP ${cause.responseCode})"
                        }
                    }
                    // 网络不可用或超时
                    cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException -> {
                        if (cause.cause is java.net.SocketTimeoutException) {
                            "音频加载超时，请检查网络连接"
                        } else if (cause.cause is java.net.UnknownHostException || cause.cause is java.net.ConnectException) {
                            "网络连接失败，请检查网络设置"
                        } else {
                            "网络音频流读取失败: ${cause.message ?: "网络异常"}"
                        }
                    }
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                        "网络连接超时或断开，请检查网络"
                    }
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
                        "音源服务器返回错误状态码"
                    }
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
                        "音频文件不存在或已被移除"
                    }
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                        "音频解码失败: 格式不支持或数据损坏"
                    }
                    else -> {
                        error.localizedMessage ?: error.message ?: "播放失败 (${error.errorCodeName})"
                    }
                }

                Log.e(TAG, "ExoPlayer error occurred: $errorMsg (code: ${error.errorCode}, name: ${error.errorCodeName})", error)
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = errorMsg
                    )
                }

                val currentSong = _playbackState.value.currentSong
                val playlist = _playbackState.value.currentPlaylist
                if (currentSong?.let { OnlineAudioSourceManager.isOnlineSong(it) } == true &&
                    playlist.size > 1 &&
                    consecutiveResolveFailures < playlist.size &&
                    consecutiveResolveFailures < 5
                ) {
                    consecutiveResolveFailures++
                    com.orbit.music.utils.FastToast.show(
                        context,
                        "【${currentSong.title}】播放异常，正在自动切至下一首...",
                        1800L
                    )
                    scope.launch {
                        delay(600L)
                        playNext()
                    }
                } else {
                    consecutiveResolveFailures = 0
                    com.orbit.music.utils.FastToast.show(context, errorMsg, 2500L)
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    Log.i(TAG, "ExoPlayer onAudioSessionIdChanged: $audioSessionId")
                    effectManager.attachSession(audioSessionId)
                    if (player.isPlaying) {
                        visualizerManager.attachSession(audioSessionId, force = true)
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                val currentList = _playbackState.value.currentPlaylist
                val foundIndex = if (mediaId != null) {
                    currentList.indexOfFirst { it.id.toString() == mediaId }
                } else {
                    -1
                }
                val index = if (foundIndex >= 0) foundIndex else player.currentMediaItemIndex
                if (index in currentList.indices) {
                    val song = currentList[index]
                    val isSameSong = _playbackState.value.currentSong?.id == song.id
                    // 仅当冷启动挂载/扩展播放队列且曲目相同时，保留已恢复的暂停进度；一旦切歌或自然推进，进度彻底清除重置为 0L
                    val targetPos = if (isSameSong && reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                        player.currentPosition.coerceAtLeast(_playbackState.value.currentPositionMs)
                    } else {
                        0L
                    }

                    if (!isSameSong) {
                        visualizerManager.reset()
                    }

                    hasRecordedPlayForCurrentSong = false
                    _playbackState.update {
                        val dur = song.durationMs.coerceAtLeast(1L)
                        it.copy(
                            currentSong = song,
                            currentIndex = index,
                            currentPositionMs = targetPos,
                            durationMs = song.durationMs,
                            progress = (targetPos.toFloat() / dur).coerceIn(0f, 1f)
                        )
                    }
                    saveLastPlayedSong(song, targetPos, syncImmediately = true)
                }
            }
        })
    }

    private fun createMediaItem(song: Song): MediaItem {
        val uri = when {
            song.path.startsWith("content://") || song.path.startsWith("http://") || song.path.startsWith("https://") -> Uri.parse(song.path)
            song.path.startsWith("file://") -> Uri.parse(song.path)
            song.path.startsWith("/") -> Uri.fromFile(java.io.File(song.path))
            else -> Uri.parse(song.path)
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
                    .build()
            )
            .build()
    }

    private fun recordPlayImmediately(song: Song) {
        hasRecordedPlayForCurrentSong = true
        scope.launch {
            com.orbit.music.data.repository.MusicRepository.getInstance(context).recordSongPlay(song)
        }
    }

    /**
     * 异步解析 online:// 协议歌曲的真实音频 URL 与音源元数据
     */
    suspend fun resolveOnlineSongSource(song: Song, explicitQuality: String? = null): OnlineAudioSourceManager.ResolvedAudioSource? {
        if (!song.path.startsWith("online://")) {
            return OnlineAudioSourceManager.ResolvedAudioSource(
                url = song.path,
                platform = song.sourcePlatform ?: song.originalPlatform ?: OnlinePlatform.NETEASE,
                sourceName = song.sourceTag ?: "本地/直链",
                durationMs = song.durationMs
            )
        }
        val uri = Uri.parse(song.path)
        val platformId = uri.host ?: ""
        val songId = uri.lastPathSegment ?: ""
        val platform = song.originalPlatform 
            ?: (OnlinePlatform.values().firstOrNull { it.id == platformId } ?: OnlinePlatform.NETEASE)
        return onlineSourceManager.resolvePlayableSource(
            platform = platform,
            songId = songId,
            title = song.title,
            artist = song.artist,
            album = song.album,
            expectedDurationMs = song.durationMs,
            explicitQuality = explicitQuality
        )
    }

    /**
     * 异步解析 online:// 协议歌曲的真实音频 URL (兼容)
     */
    suspend fun resolveOnlineSongDirectUrl(song: Song, explicitQuality: String? = null): String? {
        return resolveOnlineSongSource(song, explicitQuality)?.url
    }

    /**
     * 实时切换当前在线歌曲的音频清晰度/音质 (保持当前播放进度无缝重载)
     */
    fun switchOnlineAudioQuality(targetQuality: String) {
        val currentSong = _playbackState.value.currentSong ?: return
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val isPlaying = player.isPlaying

        // 更新全局默认偏好音质
        SourceScriptManager.getInstance(context).setPreferredQuality(targetQuality)

        val qualityLabel = when (targetQuality) {
            "flac24bit" -> "Hi-Res 无损 (24bit)"
            "flac" -> "SQ 超品质无损"
            "320k" -> "HQ 高品质 (320k)"
            else -> "标准音质 (128k)"
        }

        if (!currentSong.path.startsWith("online://") && !currentSong.isOnlineSong) {
            com.orbit.music.utils.FastToast.show(context, "已设置网络默认偏好音质为【$qualityLabel】\n当前本地歌曲为原音直出", 2000L)
            return
        }

        com.orbit.music.utils.FastToast.show(context, "正在切换音质为【$qualityLabel】...", 1200L)

        // 重新启动该曲目的解析与播放 (带上 targetQuality)
        val currentIndex = _playbackState.value.currentIndex
        playTrackInternal(
            targetIndex = currentIndex,
            startPositionMs = currentPos,
            autoPlay = isPlaying,
            explicitQuality = targetQuality
        )
    }

    /**
     * 参照 auralis 核心架构：统一单轨播放调度引擎
     * 无论在线歌曲或本地歌曲，均通过全局单调递增事务序号 (playbackSequenceId) 与严格状态机驱动
     */
    private fun playTrackInternal(
        targetIndex: Int,
        startPositionMs: Long = 0L,
        autoPlay: Boolean = true,
        explicitQuality: String? = null
    ) {
        val playlist = _playbackState.value.currentPlaylist
        if (playlist.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(0, playlist.size - 1)
        val targetSong = playlist[safeIndex]
        val updatedSong = targetSong.copy(playCount = targetSong.playCount + 1)
        val updatedPlaylist = playlist.mapIndexed { idx, s ->
            if (idx == safeIndex) updatedSong else s
        }

        // 分配本轮播放事务序号并递增，使所有更早的异步解析或网络请求立即作废
        val txId = ++playbackSequenceId
        currentOnlineResolveJob?.cancel()
        currentOnlineResolveJob = null

        visualizerManager.reset()
        hasRecordedPlayForCurrentSong = false

        val isOnline = targetSong.path.startsWith("online://") || 
                targetSong.path.startsWith("http://") || 
                targetSong.path.startsWith("https://")

        _playbackState.update {
            it.copy(
                currentPlaylist = updatedPlaylist,
                currentIndex = safeIndex,
                currentSong = updatedSong,
                currentPositionMs = startPositionMs,
                durationMs = updatedSong.durationMs,
                progress = if (updatedSong.durationMs > 0) (startPositionMs.toFloat() / updatedSong.durationMs).coerceIn(0f, 1f) else 0f,
                isPlaying = autoPlay,
                isBuffering = isOnline,
                errorMessage = null
            )
        }

        recordPlayImmediately(targetSong)

        if (targetSong.path.startsWith("online://")) {
            // 在线歌曲分支：由应用层按需解析真实音频直链与完整性校验后安全交付 ExoPlayer
            currentOnlineResolveJob = scope.launch {
                var resolvedSource: OnlineAudioSourceManager.ResolvedAudioSource? = null
                var resolveException: Exception? = null
                try {
                    resolvedSource = resolveOnlineSongSource(targetSong, explicitQuality)
                } catch (e: Exception) {
                    resolveException = e
                }

                if (txId != playbackSequenceId || !isActive) {
                    Log.d(TAG, "Online song resolution discarded due to stale txId: $txId vs $playbackSequenceId")
                    return@launch
                }

                if (resolvedSource == null || resolvedSource.url.isBlank() || resolvedSource.url.startsWith("online://")) {
                    val errorMsg = when {
                        resolveException is java.net.UnknownHostException || resolveException is java.net.ConnectException -> "网络连接失败，请检查网络设置"
                        resolveException is java.net.SocketTimeoutException -> "网络请求超时，请稍后重试"
                        !onlineSourceManager.hasCustomScript() -> "未导入音源，请前往「设置 - 音源管理」导入第三方音源"
                        resolveException != null -> "第三方音源解析失败: ${resolveException.localizedMessage ?: "未知错误"}"
                        else -> "全平台均未找到可用歌源 (可能受版权保护或音源不支持)"
                    }

                    Log.e(TAG, "Failed to resolve direct URL for ${targetSong.title}: $errorMsg", resolveException)

                    consecutiveResolveFailures++

                    if (playlist.size > 1 && consecutiveResolveFailures < playlist.size && consecutiveResolveFailures <= 10) {
                        _playbackState.update {
                            it.copy(
                                isPlaying = true,
                                isBuffering = true,
                                errorMessage = "【${targetSong.title}】$errorMsg，正在自动跳至下一首..."
                            )
                        }
                        com.orbit.music.utils.FastToast.show(
                            context,
                            "【${targetSong.title}】未找到可用歌源，正在自动跳至下一首...",
                            1800L
                        )
                        delay(600L)
                        if (txId == playbackSequenceId && isActive) {
                            playNext()
                        }
                    } else {
                        val finalTip = if (playlist.size > 1 && consecutiveResolveFailures >= playlist.size) {
                            "当前列表中所有歌曲均无法解析，播放已暂停"
                        } else {
                            errorMsg
                        }
                        consecutiveResolveFailures = 0
                        _playbackState.update {
                            it.copy(
                                isPlaying = false,
                                isBuffering = false,
                                errorMessage = finalTip
                            )
                        }
                        com.orbit.music.utils.FastToast.show(context, finalTip, 2500L)
                    }
                    return@launch
                }

                // 成功解析到有效音频直链，重置连续失败计数器
                consecutiveResolveFailures = 0

                val directUrl = resolvedSource.url

                val origPlatform = targetSong.originalPlatform ?: run {
                    val host = Uri.parse(targetSong.path).host
                    OnlinePlatform.values().firstOrNull { it.id == host } ?: OnlinePlatform.NETEASE
                }

                val resolvedSong = updatedSong.copy(
                    path = directUrl,
                    durationMs = if (resolvedSource.durationMs > 0L) resolvedSource.durationMs else updatedSong.durationMs,
                    sourcePlatform = resolvedSource.platform,
                    sourceTag = if (resolvedSource.isFallback) "${resolvedSource.platform.displayName} (自动降级匹配)" else resolvedSource.platform.displayName,
                    originalPlatform = origPlatform
                )
                val resolvedPlaylist = updatedPlaylist.mapIndexed { idx, s ->
                    if (idx == safeIndex) resolvedSong else s
                }

                _playbackState.update {
                    it.copy(
                        currentPlaylist = resolvedPlaylist, 
                        currentSong = resolvedSong,
                        durationMs = resolvedSong.durationMs,
                        isBuffering = true,
                        errorMessage = null
                    )
                }

                // 若该网络歌曲缺少封面，在后台异步嗅探并持久化至本地库
                if (resolvedSong.albumArtUri.isNullOrBlank()) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val kw = "${resolvedSong.title} ${resolvedSong.artist}".trim()
                            val searchResults = com.orbit.music.data.online.repository.OnlineMusicRepository.getInstance().searchSongs(kw, page = 1, pageSize = 3).getOrNull() ?: emptyList()
                            val matched = searchResults.firstOrNull { !it.coverUrl.isNullOrBlank() }
                            if (matched != null && !matched.coverUrl.isNullOrBlank()) {
                                val finalArt = if (matched.coverUrl.startsWith("//")) "https:${matched.coverUrl}" else matched.coverUrl
                                com.orbit.music.data.repository.MusicRepository.getInstance(context).updateSongAlbumArt(resolvedSong.id, finalArt)
                                _playbackState.update { state ->
                                    val updatedWithCover = state.currentSong?.copy(albumArtUri = finalArt)
                                    val updatedPlaylistWithCover = state.currentPlaylist.map { s ->
                                        if (s.id == resolvedSong.id) s.copy(albumArtUri = finalArt) else s
                                    }
                                    state.copy(currentSong = updatedWithCover, currentPlaylist = updatedPlaylistWithCover)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                if (resolvedSource.isFallback) {
                    com.orbit.music.utils.FastToast.show(
                        context,
                        "原源音频缺失或试听短流，已自动切换至【${resolvedSource.platform.displayName}】",
                        2200L
                    )
                }

                try {
                    val mediaItem = createMediaItem(resolvedSong)
                    // 在线单轨播放必须关闭底层 ExoPlayer 的 repeatMode，确保单曲播放结束时触发 STATE_ENDED，由 handlePlaybackEnded 统一调度进入下一首
                    player.repeatMode = Player.REPEAT_MODE_OFF
                    player.playWhenReady = autoPlay
                    player.setMediaItem(mediaItem, startPositionMs)
                    player.prepare()
                    if (autoPlay) {
                        player.play()
                        com.orbit.music.service.MusicPlaybackService.start(context)
                    } else {
                        player.pause()
                    }
                    saveLastPlayedSong(resolvedSong, startPositionMs, syncImmediately = true)
                } catch (e: Exception) {
                    val errMsg = "播放初始化失败: ${e.localizedMessage ?: e.message}"
                    Log.e(TAG, "Failed to initialize ExoPlayer for online song: ${resolvedSong.title}", e)
                    _playbackState.update {
                        it.copy(
                            isPlaying = false,
                            isBuffering = false,
                            errorMessage = errMsg
                        )
                    }
                }
            }
        } else {
            // 本地歌曲分支：直接向 ExoPlayer 挂载播放列表，实现极速切歌与播放
            try {
                val mediaItems = updatedPlaylist.map { createMediaItem(it) }
                player.repeatMode = when (_playbackState.value.repeatMode) {
                    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                }
                player.playWhenReady = autoPlay
                player.setMediaItems(mediaItems, safeIndex, startPositionMs)
                player.prepare()

                if (autoPlay) {
                    player.play()
                    com.orbit.music.service.MusicPlaybackService.start(context)
                } else {
                    player.pause()
                }
                saveLastPlayedSong(updatedSong, startPositionMs, syncImmediately = true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play local track at index $safeIndex, attempting recovery...", e)
                try {
                    val mediaItem = createMediaItem(updatedSong)
                    player.playWhenReady = autoPlay
                    player.setMediaItem(mediaItem, startPositionMs)
                    player.prepare()
                    if (autoPlay) {
                        player.play()
                        com.orbit.music.service.MusicPlaybackService.start(context)
                    }
                    saveLastPlayedSong(updatedSong, startPositionMs, syncImmediately = true)
                } catch (_: Exception) {}
            }
        }

        // 🚀 全局静默预热流水线 (智能预热下一首、随机播放预选目标与候选池，连播与切歌 0ms 秒开)
        schedulePrefetchPipeline(safeIndex, updatedPlaylist)
    }

    /**
     * 播放整张在线歌单
     */
    fun playOnlineSongList(onlineSongs: List<OnlineSongItem>, startIndex: Int = 0) {
        if (onlineSongs.isEmpty()) return
        consecutiveResolveFailures = 0
        val songList = onlineSourceManager.toSongList(onlineSongs)
        playHistory.clear()
        _playbackState.update { it.copy(currentPlaylist = songList) }
        playTrackInternal(startIndex, 0L, autoPlay = true)
    }

    /**
     * 播放标准歌曲列表
     */
    fun playSongList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        consecutiveResolveFailures = 0
        playHistory.clear()
        _playbackState.update { it.copy(currentPlaylist = songs) }
        playTrackInternal(startIndex, 0L, autoPlay = true)
    }

    fun pause() {
        playbackSequenceId++ // 作废所有可能正在返回的异步解析回调
        currentOnlineResolveJob?.cancel()
        currentOnlineResolveJob = null
        player.pause()
        _playbackState.update { it.copy(isPlaying = false) }
        visualizerManager.setPlaying(false)
        stopProgressTracker()
        stopVisualizerWatchdog()
        saveLastPlayedSong(_playbackState.value.currentSong, player.currentPosition, syncImmediately = true)
    }

    fun play() {
        _playbackState.update { it.copy(isPlaying = true) }
        val currentSong = _playbackState.value.currentSong
        if (currentSong != null && currentSong.path.startsWith("online://")) {
            // 若为未完成直链解析的在线协议，重新启动单轨事务解析与播放
            playTrackInternal(_playbackState.value.currentIndex, _playbackState.value.currentPositionMs, autoPlay = true)
            return
        }
        if (player.playbackState == Player.STATE_IDLE || player.mediaItemCount == 0) {
            if (currentSong != null) {
                val mediaItem = createMediaItem(currentSong)
                val lastPos = _playbackState.value.currentPositionMs
                player.setMediaItem(mediaItem, lastPos)
                player.prepare()
            }
        }
        player.play()
        com.orbit.music.service.MusicPlaybackService.start(context)
    }

    fun togglePlayPause() {
        val isCurrentlyPlaying = _playbackState.value.isPlaying || player.playWhenReady || player.isPlaying
        if (isCurrentlyPlaying) {
            pause()
        } else {
            play()
        }
    }

    private fun pickNextShuffleIndex(playlist: List<Song>, currentIndex: Int): Int {
        if (playlist.size <= 1) return 0
        val strategy = _playbackState.value.shuffleStrategy
        val otherIndices = playlist.indices.filter { it != currentIndex }
        if (otherIndices.isEmpty()) return 0

        return when (strategy) {
            ShuffleStrategy.FAVORITE_FIRST -> {
                val favoriteIndices = otherIndices.filter { playlist[it].isFavorite }
                if (favoriteIndices.isNotEmpty()) {
                    favoriteIndices.random()
                } else {
                    otherIndices.random()
                }
            }
            ShuffleStrategy.LEAST_PLAYED -> {
                val minPlayCount = otherIndices.minOfOrNull { playlist[it].playCount } ?: 0
                val leastPlayedIndices = otherIndices.filter { playlist[it].playCount <= minPlayCount }
                if (leastPlayedIndices.isNotEmpty()) {
                    leastPlayedIndices.random()
                } else {
                    otherIndices.random()
                }
            }
            ShuffleStrategy.STANDARD -> {
                otherIndices.random()
            }
        }
    }

    /**
     * 精准预测并获取下一个目标曲目索引 (顺序/随机播放均高度确定并联动预解析)
     */
    fun calculateNextIndex(playlist: List<Song>, currentIndex: Int): Int {
        if (playlist.size <= 1) return 0
        if (_playbackState.value.isShuffleEnabled) {
            val planned = plannedNextShuffleIndex
            if (planned != null && planned in playlist.indices && planned != currentIndex) {
                return planned
            }
            return pickNextShuffleIndex(playlist, currentIndex)
        }
        return (currentIndex + 1) % playlist.size
    }

    /**
     * 智能预热流水线：预解析即将播放的曲目（支持顺序预热、随机策略精准预热与候选池轻量并发预热）
     */
    private fun schedulePrefetchPipeline(currentIndex: Int, playlist: List<Song>) {
        if (playlist.isEmpty() || currentIndex !in playlist.indices) return
        prefetchJob?.cancel()

        prefetchJob = scope.launch(Dispatchers.IO) {
            delay(350L) // 避开切歌初始启动瞬间的 CPU/网络争夺

            val isShuffle = _playbackState.value.isShuffleEnabled

            // 1. 确定下一个播放目标 (若为随机，提前生成确定性的 plannedNextShuffleIndex)
            val nextIndex = if (isShuffle && playlist.size > 1) {
                val next = pickNextShuffleIndex(playlist, currentIndex)
                plannedNextShuffleIndex = next
                next
            } else {
                plannedNextShuffleIndex = null
                if (playlist.size > 1) (currentIndex + 1) % playlist.size else -1
            }

            // 2. 第一优先级：必须预热第一确定的下一首 (命中率最高)
            if (nextIndex in playlist.indices && nextIndex != currentIndex) {
                val nextSong = playlist[nextIndex]
                if (nextSong.path.startsWith("online://")) {
                    runCatching { resolveOnlineSongSource(nextSong) }
                }
            }

            // 3. 第二优先级：若处于随机模式，额外预热 2 个高概率候选（防止用户快速跳歌）
            if (isShuffle && playlist.size > 2) {
                val otherCandidates = playlist.indices
                    .filter { it != currentIndex && it != nextIndex }
                    .shuffled()
                    .take(2)
                for (cIdx in otherCandidates) {
                    val cSong = playlist[cIdx]
                    if (cSong.path.startsWith("online://")) {
                        runCatching { resolveOnlineSongSource(cSong) }
                    }
                }
            }

            // 4. 第三优先级：若历史记录中有上一首未解析的 online 歌曲，也进行预热
            val prevIdx = playHistory.peekLast()
            if (prevIdx != null && prevIdx in playlist.indices && prevIdx != currentIndex && prevIdx != nextIndex) {
                val prevSong = playlist[prevIdx]
                if (prevSong.path.startsWith("online://")) {
                    runCatching { resolveOnlineSongSource(prevSong) }
                }
            }
        }
    }

    private fun seekToTrack(targetIndex: Int) {
        playTrackInternal(targetIndex, 0L, autoPlay = true)
    }

    fun playNext() {
        val playlist = _playbackState.value.currentPlaylist
        if (playlist.isEmpty()) return

        val currentSongId = _playbackState.value.currentSong?.id
        val resolvedIndex = if (currentSongId != null) {
            playlist.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }
        } else null

        val currentIndex = resolvedIndex
            ?: if (_playbackState.value.currentIndex in playlist.indices) {
                _playbackState.value.currentIndex
            } else {
                0
            }

        if (currentIndex in playlist.indices) {
            if (playHistory.peekLast() != currentIndex) {
                playHistory.addLast(currentIndex)
                if (playHistory.size > MAX_HISTORY_SIZE) {
                    playHistory.removeFirst()
                }
            }
        }

        val nextIndex = calculateNextIndex(playlist, currentIndex)
        plannedNextShuffleIndex = null // 消费掉当前预选索引，触发下一轮重选
        seekToTrack(nextIndex)
    }

    fun playPrevious() {
        val playlist = _playbackState.value.currentPlaylist
        if (playlist.isEmpty()) return

        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            _playbackState.update {
                it.copy(
                    currentPositionMs = 0L,
                    progress = 0f
                )
            }
            saveLastPlayedSong(_playbackState.value.currentSong, 0L, syncImmediately = true)
            if (!player.isPlaying) player.play()
            return
        }

        val currentSongId = _playbackState.value.currentSong?.id
        val resolvedIndex = if (currentSongId != null) {
            playlist.indexOfFirst { it.id == currentSongId }.takeIf { it >= 0 }
        } else null

        val currentIndex = resolvedIndex
            ?: if (_playbackState.value.currentIndex in playlist.indices) {
                _playbackState.value.currentIndex
            } else {
                0
            }

        // 优先从历史栈返回上一首真正听过的歌（完美适配随机播放回退）
        if (playHistory.isNotEmpty()) {
            val prevIndex = playHistory.removeLast()
            if (prevIndex in playlist.indices && prevIndex != currentIndex) {
                seekToTrack(prevIndex)
                return
            }
        }

        val prevIndex = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        seekToTrack(prevIndex)
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        _playbackState.update {
            val dur = it.durationMs.coerceAtLeast(1L)
            it.copy(
                currentPositionMs = positionMs,
                progress = (positionMs.toFloat() / dur).coerceIn(0f, 1f)
            )
        }
        saveLastPlayedSong(_playbackState.value.currentSong, positionMs, syncImmediately = true)
    }

    fun toggleShuffle(): Int {
        val currentShuffle = _playbackState.value.isShuffleEnabled
        val currentStrategy = _playbackState.value.shuffleStrategy

        val (nextShuffle, nextStrategy, toastResId) = when {
            !currentShuffle -> Triple(true, ShuffleStrategy.STANDARD, com.orbit.music.R.string.shuffle_standard)
            currentStrategy == ShuffleStrategy.STANDARD -> Triple(true, ShuffleStrategy.FAVORITE_FIRST, com.orbit.music.R.string.shuffle_favorite_first)
            currentStrategy == ShuffleStrategy.FAVORITE_FIRST -> Triple(true, ShuffleStrategy.LEAST_PLAYED, com.orbit.music.R.string.shuffle_least_played)
            else -> Triple(false, ShuffleStrategy.STANDARD, com.orbit.music.R.string.shuffle_off)
        }

        player.shuffleModeEnabled = nextShuffle
        _playbackState.update {
            it.copy(
                isShuffleEnabled = nextShuffle,
                shuffleStrategy = nextStrategy
            )
        }
        prefs.edit()
            .putBoolean(KEY_SHUFFLE_ENABLED, nextShuffle)
            .putInt(KEY_SHUFFLE_STRATEGY, nextStrategy.ordinal)
            .apply()

        schedulePrefetchPipeline(_playbackState.value.currentIndex, _playbackState.value.currentPlaylist)

        return toastResId
    }

    fun setShuffleStrategy(strategy: ShuffleStrategy) {
        _playbackState.update { it.copy(shuffleStrategy = strategy) }
        prefs.edit().putInt(KEY_SHUFFLE_STRATEGY, strategy.ordinal).apply()
        schedulePrefetchPipeline(_playbackState.value.currentIndex, _playbackState.value.currentPlaylist)
    }

    fun toggleRepeatMode() {
        val newMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        val isCurrentOnline = _playbackState.value.currentSong?.let { OnlineAudioSourceManager.isOnlineSong(it) } ?: false
        player.repeatMode = if (isCurrentOnline) {
            Player.REPEAT_MODE_OFF
        } else {
            when (newMode) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
        }
        _playbackState.update { it.copy(repeatMode = newMode) }
        prefs.edit().putInt(KEY_REPEAT_MODE, newMode.ordinal).apply()
    }

    private fun handlePlaybackEnded() {
        if (_playbackState.value.repeatMode == RepeatMode.ONE) {
            _playbackState.value.currentSong?.let { song ->
                val updatedSong = song.copy(playCount = song.playCount + 1)
                _playbackState.update { state ->
                    val updatedList = state.currentPlaylist.map {
                        if (it.path == song.path) updatedSong else it
                    }
                    state.copy(
                        currentPlaylist = updatedList,
                        currentSong = updatedSong,
                        currentPositionMs = 0L,
                        progress = 0f
                    )
                }
                recordPlayImmediately(song)
                saveLastPlayedSong(song, 0L, syncImmediately = true)
            }
            player.seekTo(0L)
            player.play()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            playNext()
        } else if (_playbackState.value.repeatMode == RepeatMode.OFF) {
            val playlist = _playbackState.value.currentPlaylist
            val curSongId = _playbackState.value.currentSong?.id
            val curIndex = if (curSongId != null) {
                playlist.indexOfFirst { it.id == curSongId }.takeIf { it >= 0 } ?: _playbackState.value.currentIndex
            } else {
                _playbackState.value.currentIndex
            }
            if (playlist.isNotEmpty() && curIndex < playlist.size - 1) {
                playNext()
            } else {
                _playbackState.update { it.copy(isPlaying = false, currentPositionMs = 0L, progress = 0f) }
            }
        }
    }

    private var lastSavedPosTime = 0L

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val currentPos = player.currentPosition
                val duration = player.duration.coerceAtLeast(1L)
                val progress = (currentPos.toFloat() / duration).coerceIn(0f, 1f)

                // 播放超过 20 秒视为有效播放，累计播放计数（兜底保障）
                if (currentPos >= 20000L && !hasRecordedPlayForCurrentSong) {
                    _playbackState.value.currentSong?.let { song ->
                        val updatedSong = song.copy(playCount = song.playCount + 1)
                        _playbackState.update { state ->
                            val updatedList = state.currentPlaylist.map {
                                if (it.path == song.path) updatedSong else it
                            }
                            state.copy(
                                currentPlaylist = updatedList,
                                currentSong = updatedSong
                            )
                        }
                        recordPlayImmediately(song)
                    }
                }

                _playbackState.update {
                    it.copy(
                        currentPositionMs = currentPos,
                        durationMs = duration,
                        progress = progress
                    )
                }

                // 实时节流持久化当前播放进度（每 1 秒保存一次），确保随时被直接 kill 也能秒级精准恢复
                val now = System.currentTimeMillis()
                if (now - lastSavedPosTime >= 1000L) {
                    lastSavedPosTime = now
                    saveCurrentPosition(currentPos)
                }

                delay(200L)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
        // 停止播放追踪时立刻同步刷盘
        saveCurrentPosition(player.currentPosition, syncImmediately = true)
    }

    /**
     * 智能频谱持续自愈看门狗：实时监控真实音频流状态，无论冷启动、队列扩充还是切歌，全自动秒级自愈重绑
     */
    private fun startVisualizerWatchdog() {
        visualizerWatchdogJob?.cancel()
        visualizerWatchdogJob = scope.launch {
            // 1. 立即挂载初次可用 Session
            val initialSession = player.audioSessionId
            if (initialSession != C.AUDIO_SESSION_ID_UNSET && initialSession != 0) {
                visualizerManager.attachSession(initialSession)
            }

            // 2. 持续守护轮询：若由于曲库加载重置、起播缓冲或硬件切换导致断联，自动自愈重绑真实音频流
            while (isActive && player.isPlaying) {
                delay(350L)
                if (!isActive || !player.isPlaying) break

                if (!visualizerManager.isReceivingRealFft()) {
                    val activeSession = player.audioSessionId
                    if (activeSession != C.AUDIO_SESSION_ID_UNSET && activeSession != 0) {
                        Log.i(TAG, "Visualizer watchdog: auto-healing connection to AudioSession $activeSession...")
                        visualizerManager.attachSession(activeSession, force = true)
                    }
                }
            }
        }
    }

    private fun stopVisualizerWatchdog() {
        visualizerWatchdogJob?.cancel()
        visualizerWatchdogJob = null
    }

    fun updateSongAttitude(songPath: String, isFavorite: Boolean, isDisliked: Boolean) {
        _playbackState.update { current ->
            val updatedSong = if (current.currentSong?.path == songPath) {
                current.currentSong.copy(isFavorite = isFavorite, isDisliked = isDisliked)
            } else current.currentSong

            val updatedPlaylist = current.currentPlaylist.map {
                if (it.path == songPath) it.copy(isFavorite = isFavorite, isDisliked = isDisliked) else it
            }

            current.copy(
                currentSong = updatedSong,
                currentPlaylist = updatedPlaylist
            )
        }
    }

    fun updateSongFavorite(songPath: String, isFavorite: Boolean) {
        updateSongAttitude(songPath, isFavorite = isFavorite, isDisliked = false)
    }

    fun updateSongMetadata(updatedSong: Song) {
        _playbackState.update { current ->
            val isCurrent = current.currentSong?.id == updatedSong.id
            val newCurrentSong = if (isCurrent) updatedSong else current.currentSong
            val updatedPlaylist = current.currentPlaylist.map {
                if (it.id == updatedSong.id) updatedSong else it
            }
            if (isCurrent) {
                saveLastPlayedSong(updatedSong, current.currentPositionMs, syncImmediately = true)
            }
            current.copy(
                currentSong = newCurrentSong,
                currentPlaylist = updatedPlaylist
            )
        }
    }

    fun resetAllPlayCounts() {
        _playbackState.update { current ->
            val resetPlaylist = current.currentPlaylist.map { it.copy(playCount = 0) }
            val resetCurrentSong = current.currentSong?.copy(playCount = 0)
            current.copy(
                currentPlaylist = resetPlaylist,
                currentSong = resetCurrentSong
            )
        }
    }

    fun removeSongFromPlayback(song: Song) {
        removeSongsFromPlayback(listOf(song))
    }

    fun removeSongsFromPlayback(songs: List<Song>) {
        if (songs.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val current = _playbackState.value
            val removeSongIds = songs.map { it.id }.toSet()
            val removeSongPaths = songs.map { it.path }.toSet()

            val toRemoveIndices = current.currentPlaylist.indices.filter { idx ->
                val s = current.currentPlaylist[idx]
                s.id in removeSongIds || s.path in removeSongPaths
            }

            // 如果被删除的歌曲都不在当前的播放队列中，无需更改播放器
            if (toRemoveIndices.isEmpty()) {
                return@launch
            }

            val newPlaylist = current.currentPlaylist.filter { it.id !in removeSongIds && it.path !in removeSongPaths }

            // 1. 如果播放队列全部被清空
            if (newPlaylist.isEmpty()) {
                stopProgressTracker()
                stopVisualizerWatchdog()
                player.stop()
                player.clearMediaItems()
                visualizerManager.reset()
                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        currentSong = null,
                        currentIndex = 0,
                        currentPlaylist = emptyList(),
                        currentPositionMs = 0L,
                        durationMs = 0L,
                        progress = 0f
                    )
                }
                prefs.edit().clear().apply()
                return@launch
            }

            val isCurrentPlayingRemoved = current.currentSong != null &&
                    (current.currentSong.id in removeSongIds || current.currentSong.path in removeSongPaths)

            if (isCurrentPlayingRemoved) {
                // 当前正在播放的歌曲被删除了，需要切换到下一首
                val oldIndex = current.currentIndex
                val nextIndex = if (oldIndex in newPlaylist.indices) oldIndex else 0
                val nextSong = newPlaylist[nextIndex]

                _playbackState.update {
                    it.copy(
                        currentPlaylist = newPlaylist,
                        currentIndex = nextIndex,
                        currentSong = nextSong,
                        currentPositionMs = 0L,
                        durationMs = nextSong.durationMs,
                        progress = 0f
                    )
                }

                try {
                    val mediaItems = newPlaylist.map { createMediaItem(it) }
                    player.setMediaItems(mediaItems, nextIndex, 0L)
                    player.prepare()
                    if (current.isPlaying) {
                        player.play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error switching track on song remove", e)
                }
                saveLastPlayedSong(nextSong, 0L, syncImmediately = true)
            } else {
                // 当前正在播放的歌曲未被删除：无缝从队列移除，绝不中断正在播放的音频流
                val newCurrentIndex = newPlaylist.indexOfFirst { it.id == current.currentSong?.id }.let {
                    if (it >= 0) it else 0
                }

                // 提前原子化更新 _playbackState，保证当前曲目和封面信息不受影响
                _playbackState.update {
                    it.copy(
                        currentPlaylist = newPlaylist,
                        currentIndex = newCurrentIndex
                    )
                }

                // 从后往前逐项从 ExoPlayer 中移除，避免索引偏移
                try {
                    toRemoveIndices.sortedDescending().forEach { index ->
                        if (index in 0 until player.mediaItemCount) {
                            player.removeMediaItem(index)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing media items from player queue", e)
                }
            }
        }
    }

    fun addSongsToQueueNext(songs: List<Song>) {
        if (songs.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            val current = _playbackState.value
            if (current.currentPlaylist.isEmpty() || current.currentSong == null) {
                // 当前队列为空时，直接播放此批歌曲
                playSongList(songs, 0)
                return@launch
            }

            val insertIndex = (current.currentIndex + 1).coerceAtMost(current.currentPlaylist.size)
            val newPlaylist = current.currentPlaylist.toMutableList().apply {
                addAll(insertIndex, songs)
            }

            _playbackState.update {
                it.copy(currentPlaylist = newPlaylist)
            }

            try {
                val newMediaItems = songs.map { createMediaItem(it) }
                player.addMediaItems(insertIndex, newMediaItems)
            } catch (e: Exception) {
                Log.e(TAG, "Error inserting songs next in queue", e)
            }
        }
    }

    fun release() {
        stopProgressTracker()
        stopVisualizerWatchdog()
        saveLastPlayedSong(_playbackState.value.currentSong, player.currentPosition, syncImmediately = true)
        player.release()
    }

    companion object {
        private const val TAG = "MusicPlayerManager"
        private const val PREFS_NAME = "music_player_state_prefs"
        private const val KEY_SONG_ID = "key_last_song_id"
        private const val KEY_SONG_TITLE = "key_last_song_title"
        private const val KEY_SONG_ARTIST = "key_last_song_artist"
        private const val KEY_SONG_ALBUM = "key_last_song_album"
        private const val KEY_ALBUM_ID = "key_last_album_id"
        private const val KEY_SONG_PATH = "key_last_song_path"
        private const val KEY_SIZE = "key_last_size"
        private const val KEY_SONG_DURATION = "key_last_song_duration"
        private const val KEY_SONG_ART_URI = "key_last_song_art_uri"
        private const val KEY_LAST_POS = "key_last_position_ms"
        private const val KEY_REPEAT_MODE = "key_repeat_mode"
        private const val KEY_SHUFFLE_ENABLED = "key_shuffle_enabled"
        private const val KEY_SHUFFLE_STRATEGY = "key_shuffle_strategy"

        @Volatile
        private var INSTANCE: MusicPlayerManager? = null

        fun getInstance(context: Context): MusicPlayerManager {
            return INSTANCE ?: synchronized(this) {
                val instance = MusicPlayerManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
