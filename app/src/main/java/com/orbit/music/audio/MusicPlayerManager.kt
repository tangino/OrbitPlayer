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
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val isShuffleEnabled: Boolean = false,
    val shuffleStrategy: ShuffleStrategy = ShuffleStrategy.STANDARD,
    val currentPlaylist: List<Song> = emptyList(),
    val currentIndex: Int = -1
)

class MusicPlayerManager private constructor(private val context: Context) {

    private val effectManager = AudioEffectManager.getInstance(context)
    val visualizerManager = AudioVisualizerManager.getInstance(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null
    private var visualizerWatchdogJob: Job? = null
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val savedRepeatOrdinal = prefs.getInt(KEY_REPEAT_MODE, RepeatMode.ALL.ordinal)
    private val initialRepeatMode = RepeatMode.values().getOrElse(savedRepeatOrdinal) { RepeatMode.ALL }
    private val initialShuffle = prefs.getBoolean(KEY_SHUFFLE_ENABLED, false)
    private val savedStrategyOrdinal = prefs.getInt(KEY_SHUFFLE_STRATEGY, ShuffleStrategy.STANDARD.ordinal)
    private val initialStrategy = ShuffleStrategy.values().getOrElse(savedStrategyOrdinal) { ShuffleStrategy.STANDARD }

    private val playHistory = ArrayDeque<Int>()
    private val MAX_HISTORY_SIZE = 50
    private var hasRecordedPlayForCurrentSong = false

    private val player: ExoPlayer = ExoPlayer.Builder(context)
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
                _playbackState.update { it.copy(isPlaying = isPlaying) }
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
                        it.copy(durationMs = player.duration.coerceAtLeast(0L))
                    }
                } else if (playbackState == Player.STATE_ENDED) {
                    handlePlaybackEnded()
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
                val index = player.currentMediaItemIndex
                val currentList = _playbackState.value.currentPlaylist
                if (index in currentList.indices) {
                    val song = currentList[index]
                    val isSameSong = _playbackState.value.currentSong?.id == song.id
                    // 如果是冷启动或队列扩充触发的同曲目过渡，保留已有进度；只有切换不同曲目时才归零
                    val targetPos = if (isSameSong) {
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
                    saveLastPlayedSong(song, targetPos)
                }
            }
        })
    }

    private fun createMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setUri(Uri.parse(song.path))
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

    fun playSongList(songs: List<Song>, startIndex: Int = 0) {
        if (songs.isEmpty()) return
        playHistory.clear()

        val safeIndex = startIndex.coerceIn(0, songs.size - 1)
        val targetSong = songs[safeIndex]
        val updatedSong = targetSong.copy(playCount = targetSong.playCount + 1)
        val updatedSongs = songs.mapIndexed { idx, s ->
            if (idx == safeIndex) updatedSong else s
        }

        val mediaItems = updatedSongs.map { createMediaItem(it) }
        visualizerManager.reset()

        _playbackState.update {
            it.copy(
                currentPlaylist = updatedSongs,
                currentIndex = safeIndex,
                currentSong = updatedSong
            )
        }

        recordPlayImmediately(targetSong)

        player.setMediaItems(mediaItems, safeIndex, 0L)
        player.prepare()
        player.play()
        com.orbit.music.service.MusicPlaybackService.start(context)
        saveLastPlayedSong(updatedSong, 0L)
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            saveLastPlayedSong(_playbackState.value.currentSong, player.currentPosition)
        } else {
            if (player.playbackState == Player.STATE_IDLE) {
                val currentSong = _playbackState.value.currentSong
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
    }

    private fun pickNextShuffleIndex(playlist: List<Song>, currentIndex: Int): Int {
        if (playlist.size <= 1) return 0
        val historySet = playHistory.toSet()

        return when (_playbackState.value.shuffleStrategy) {
            ShuffleStrategy.LEAST_PLAYED -> {
                // 排除当前正在播放的歌曲及标记为不喜欢的歌曲
                val validIndices = playlist.indices.filter { it != currentIndex && !playlist[it].isDisliked }.ifEmpty {
                    playlist.indices.filter { it != currentIndex }
                }
                if (validIndices.isEmpty()) return 0

                // 寻找有效曲库中的全局最低播放次数
                val minPlayCount = validIndices.minOfOrNull { playlist[it].playCount } ?: 0
                // 仅筛选播放次数等于最低次数的歌曲入候选池（次数更高的全部严格排除）
                val minPool = validIndices.filter { playlist[it].playCount <= minPlayCount }

                // 在最低播放次数的候选池中，优先挑选未在最近历史栈中的歌曲
                val freshCandidates = minPool.filter { it !in historySet }
                if (freshCandidates.isNotEmpty()) {
                    freshCandidates.random()
                } else {
                    minPool.random()
                }
            }
            ShuffleStrategy.FAVORITE_FIRST -> {
                var candidates = playlist.indices.filter { it != currentIndex && it !in historySet && !playlist[it].isDisliked }
                if (candidates.isEmpty()) {
                    candidates = playlist.indices.filter { it != currentIndex && !playlist[it].isDisliked }
                    if (candidates.isEmpty()) {
                        candidates = playlist.indices.filter { it != currentIndex }
                        if (candidates.isEmpty()) return 0
                    }
                }
                val favCandidates = candidates.filter { playlist[it].isFavorite }
                if (favCandidates.isNotEmpty()) {
                    favCandidates.random()
                } else {
                    candidates.random()
                }
            }
            ShuffleStrategy.STANDARD -> {
                var candidates = playlist.indices.filter { it != currentIndex && it !in historySet && !playlist[it].isDisliked }
                if (candidates.isEmpty()) {
                    candidates = playlist.indices.filter { it != currentIndex && !playlist[it].isDisliked }
                    if (candidates.isEmpty()) {
                        candidates = playlist.indices.filter { it != currentIndex }
                        if (candidates.isEmpty()) return 0
                    }
                }
                candidates.random()
            }
        }
    }

    /**
     * 高稳健性直接切歌调度引擎：自动修复队列脱节、IDLE状态自动唤醒准备并同步前台状态
     */
    private fun seekToTrack(targetIndex: Int) {
        val playlist = _playbackState.value.currentPlaylist
        if (playlist.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(0, playlist.size - 1)
        val targetSong = playlist[safeIndex]
        val updatedSong = targetSong.copy(playCount = targetSong.playCount + 1)
        val updatedPlaylist = playlist.mapIndexed { idx, s ->
            if (idx == safeIndex) updatedSong else s
        }

        visualizerManager.reset()
        _playbackState.update {
            it.copy(
                currentPlaylist = updatedPlaylist,
                currentIndex = safeIndex,
                currentSong = updatedSong,
                currentPositionMs = 0L,
                durationMs = updatedSong.durationMs,
                progress = 0f
            )
        }

        recordPlayImmediately(targetSong)

        try {
            // 如果底层 ExoPlayer 内部媒体项数量与播放列表脱节（例如冷启动或部分加载），立即完整同步
            if (player.mediaItemCount != updatedPlaylist.size) {
                val mediaItems = updatedPlaylist.map { createMediaItem(it) }
                player.setMediaItems(mediaItems, safeIndex, 0L)
                player.prepare()
            } else {
                player.seekTo(safeIndex, 0L)
            }

            if (player.playbackState == Player.STATE_IDLE) {
                player.prepare()
            }
            player.play()
            com.orbit.music.service.MusicPlaybackService.start(context)
            saveLastPlayedSong(updatedSong, 0L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek to track at index $safeIndex, recovering...", e)
            try {
                val mediaItems = updatedPlaylist.map { createMediaItem(it) }
                player.setMediaItems(mediaItems, safeIndex, 0L)
                player.prepare()
                player.play()
            } catch (_: Exception) {}
        }
    }

    fun playNext() {
        val playlist = _playbackState.value.currentPlaylist
        if (playlist.isEmpty()) return

        val currentIndex = if (player.currentMediaItemIndex in playlist.indices) {
            player.currentMediaItemIndex
        } else if (_playbackState.value.currentIndex in playlist.indices) {
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

        // 自定义智能随机策略调度
        if (_playbackState.value.isShuffleEnabled && playlist.size > 1) {
            val nextIndex = pickNextShuffleIndex(playlist, currentIndex)
            seekToTrack(nextIndex)
            return
        }

        // 顺序模式：无论循环模式如何，用户手动点击切歌必定平滑推进到下一曲（到达末尾自动循环回首曲）
        val nextIndex = if (playlist.size > 1) {
            (currentIndex + 1) % playlist.size
        } else {
            0
        }
        seekToTrack(nextIndex)
    }

    fun playPrevious() {
        val playlist = _playbackState.value.currentPlaylist
        if (playlist.isEmpty()) return

        if (player.currentPosition > 3000L) {
            player.seekTo(0L)
            if (!player.isPlaying) player.play()
            return
        }

        // 优先从历史栈返回上一首真正听过的歌（完美适配随机播放回退）
        if (playHistory.isNotEmpty()) {
            val prevIndex = playHistory.removeLast()
            if (prevIndex in playlist.indices && prevIndex != player.currentMediaItemIndex) {
                seekToTrack(prevIndex)
                return
            }
        }

        val currentIndex = if (player.currentMediaItemIndex in playlist.indices) {
            player.currentMediaItemIndex
        } else if (_playbackState.value.currentIndex in playlist.indices) {
            _playbackState.value.currentIndex
        } else {
            0
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
        saveLastPlayedSong(_playbackState.value.currentSong, positionMs)
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

        return toastResId
    }

    fun setShuffleStrategy(strategy: ShuffleStrategy) {
        _playbackState.update { it.copy(shuffleStrategy = strategy) }
        prefs.edit().putInt(KEY_SHUFFLE_STRATEGY, strategy.ordinal).apply()
    }

    fun toggleRepeatMode() {
        val newMode = when (_playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        player.repeatMode = when (newMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
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
                        currentSong = updatedSong
                    )
                }
                recordPlayImmediately(song)
            }
            player.seekTo(0L)
            player.play()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL) {
            playNext()
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
        scope.launch(Dispatchers.Main) {
            val current = _playbackState.value
            val isCurrentPlaying = current.currentSong?.id == song.id || current.currentSong?.path == song.path
            val newPlaylist = current.currentPlaylist.filter { it.id != song.id && it.path != song.path }

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

            if (isCurrentPlaying) {
                val oldIndex = current.currentIndex
                val nextIndex = if (oldIndex in newPlaylist.indices) oldIndex else 0
                val nextSong = newPlaylist[nextIndex]
                
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
                saveLastPlayedSong(nextSong, 0L, syncImmediately = true)
            } else {
                val newCurrentIndex = newPlaylist.indexOfFirst { it.id == current.currentSong?.id }.let {
                    if (it >= 0) it else 0
                }
                
                try {
                    val currentPos = player.currentPosition
                    val isPlaying = player.isPlaying
                    val mediaItems = newPlaylist.map { createMediaItem(it) }
                    player.setMediaItems(mediaItems, newCurrentIndex, currentPos)
                    player.prepare()
                    if (isPlaying) {
                        player.play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating queue on song remove", e)
                }
                
                _playbackState.update {
                    it.copy(
                        currentPlaylist = newPlaylist,
                        currentIndex = newCurrentIndex
                    )
                }
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
