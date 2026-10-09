package com.orbit.music.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.orbit.music.R
import com.orbit.music.audio.MusicPlayerManager
import com.orbit.music.data.model.Song
import com.orbit.music.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 核心前台音乐播放服务 (支持车载 MediaBrowserService 与 Android Automotive OS 协议)：
 * 1. 继承 MediaLibraryService，向车机系统暴露完整的 MediaBrowserService / MediaLibraryService 接口
 * 2. 支持车机桌面卡片、仪表盘、中控音源切换器读取曲库、播放状态与封面
 * 3. 采用系统标准 MediaStyle 通知，支持锁屏与车机中心实时同步
 * 4. 播放控制按键强制统一为程序专属荧光青高亮色 (#00E5FF)
 * 5. 通过 ForwardingPlayer、MediaLibrarySession.Callback 及车机服务广播全面响应车载切歌与交互指令
 */
class MusicPlaybackService : MediaLibraryService() {

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null
    private var cachedCoverBitmap: Bitmap? = null
    private var lastCoverSongId: Long = -1L

    private val rootMediaItem by lazy {
        MediaItem.Builder()
            .setMediaId(ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Orbit Player")
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    // 车机通用音乐指令广播接收器 (兼容如吉利、比亚迪、长城等车机系统的硬件按键与音源指令)
    private val carMusicCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val action = intent.action ?: return
            val playerManager = MusicPlayerManager.getInstance(this@MusicPlaybackService)
            Log.d(TAG, "carMusicCommandReceiver received action: $action")
            if (action == ACTION_MUSIC_SERVICE_COMMAND || action == "com.android.music.musicservicecommand") {
                val cmd = intent.getStringExtra("command")
                Log.d(TAG, "car music command: $cmd")
                when (cmd?.lowercase()) {
                    "togglepause", "playpause" -> playerManager.togglePlayPause()
                    "play" -> playerManager.play()
                    "pause" -> playerManager.pause()
                    "next" -> playerManager.playNext()
                    "previous", "prev" -> playerManager.playPrevious()
                    "stop" -> playerManager.pause()
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MusicPlaybackService onCreate (MediaLibraryService for Automotive)")
        createNotificationChannel()

        // 动态注册车机广播接收器
        val filter = IntentFilter().apply {
            addAction(ACTION_MUSIC_SERVICE_COMMAND)
            addAction("com.android.music.musicservicecommand")
            addAction("android.media.AUDIO_BECOMING_NOISY")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(carMusicCommandReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(carMusicCommandReceiver, filter)
        }

        val playerManager = MusicPlayerManager.getInstance(this)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 构造 ForwardingPlayer 确保车机/蓝牙查询可用操作时始终支持上一曲/下一曲，并将切歌指令重定向至 PlayerManager
        val forwardingPlayer = object : ForwardingPlayer(playerManager.exoPlayer) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                        playerManager.playbackState.value.currentPlaylist.isNotEmpty()
                    }
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun hasNextMediaItem(): Boolean {
                return playerManager.playbackState.value.currentPlaylist.isNotEmpty()
            }

            override fun hasPreviousMediaItem(): Boolean {
                return playerManager.playbackState.value.currentPlaylist.isNotEmpty()
            }

            override fun seekToNext() {
                Log.d(TAG, "ForwardingPlayer: seekToNext triggered from car/controller")
                playerManager.playNext()
            }

            override fun seekToNextMediaItem() {
                Log.d(TAG, "ForwardingPlayer: seekToNextMediaItem triggered from car/controller")
                playerManager.playNext()
            }

            override fun seekToPrevious() {
                Log.d(TAG, "ForwardingPlayer: seekToPrevious triggered from car/controller")
                playerManager.playPrevious()
            }

            override fun seekToPreviousMediaItem() {
                Log.d(TAG, "ForwardingPlayer: seekToPreviousMediaItem triggered from car/controller")
                playerManager.playPrevious()
            }
        }

        // 构造 Media3 MediaLibrarySession 并配置完备的回调以处理车载/MediaBrowser 各种控制与浏览协议
        val libraryCallback = object : MediaLibrarySession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val baseResult = super.onConnect(session, controller)
                val availablePlayerCommands = baseResult.availablePlayerCommands.buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .add(Player.COMMAND_PLAY_PAUSE)
                    .add(Player.COMMAND_STOP)
                    .build()
                return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    .setAvailablePlayerCommands(availablePlayerCommands)
                    .build()
            }

            override fun onGetLibraryRoot(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<MediaItem>> {
                Log.d(TAG, "onGetLibraryRoot requested from: ${browser.packageName}")
                return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, params))
            }

            override fun onGetChildren(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                parentId: String,
                page: Int,
                pageSize: Int,
                params: LibraryParams?
            ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
                Log.d(TAG, "onGetChildren requested for parentId: $parentId from ${browser.packageName}")
                val currentList: List<Song> = playerManager.playbackState.value.currentPlaylist
                val mediaItems: List<MediaItem> = currentList.map { song: Song -> songToMediaItem(song) }
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params))
            }

            override fun onGetItem(
                session: MediaLibrarySession,
                browser: MediaSession.ControllerInfo,
                mediaId: String
            ): ListenableFuture<LibraryResult<MediaItem>> {
                val song = playerManager.playbackState.value.currentPlaylist.firstOrNull { it.id.toString() == mediaId }
                    ?: playerManager.playbackState.value.currentSong
                return if (song != null) {
                    Futures.immediateFuture(LibraryResult.ofItem(songToMediaItem(song), null))
                } else {
                    Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
            }

            override fun onSetMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: MutableList<MediaItem>,
                startIndex: Int,
                startPositionMs: Long
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (mediaItems.isNotEmpty() && startIndex in mediaItems.indices) {
                    val targetId = mediaItems[startIndex].mediaId.toLongOrNull()
                    val playlist = playerManager.playbackState.value.currentPlaylist
                    val foundIndex = playlist.indexOfFirst { it.id == targetId }
                    if (foundIndex >= 0) {
                        playerManager.playSongList(playlist, foundIndex)
                    }
                }
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs)
                )
            }

            override fun onMediaButtonEvent(
                session: MediaSession,
                controllerInfo: MediaSession.ControllerInfo,
                intent: Intent
            ): Boolean {
                val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "MediaSession onMediaButtonEvent keyCode: ${keyEvent.keyCode}")
                    when (keyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_NEXT,
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                            com.orbit.music.car.EcarxMediaBridge.takeFocus()
                            playerManager.playNext()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                        KeyEvent.KEYCODE_MEDIA_REWIND,
                        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
                        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                            com.orbit.music.car.EcarxMediaBridge.takeFocus()
                            playerManager.playPrevious()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            com.orbit.music.car.EcarxMediaBridge.takeFocus()
                            playerManager.play()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            playerManager.pause()
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                        KeyEvent.KEYCODE_HEADSETHOOK -> {
                            com.orbit.music.car.EcarxMediaBridge.takeFocus()
                            playerManager.togglePlayPause()
                            return true
                        }
                    }
                }
                return super.onMediaButtonEvent(session, controllerInfo, intent)
            }
        }

        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, libraryCallback)
            .setSessionActivity(openAppIntent)
            .setId("OrbitMediaLibrarySession")
            .build()

        // 初始拉起前台
        val initialSong = playerManager.playbackState.value.currentSong
        val initialNotification = buildNeonCyanNotification(
            song = initialSong,
            isPlaying = playerManager.playbackState.value.isPlaying,
            coverBitmap = null
        )
        safeStartForeground(initialNotification)

        // 监听播放状态实时刷新通知
        stateObserverJob = serviceScope.launch {
            playerManager.playbackState
                .distinctUntilChanged { old, new ->
                    old.currentSong?.id == new.currentSong?.id && old.isPlaying == new.isPlaying
                }
                .collectLatest { state ->
                    val song = state.currentSong
                    val cover = if (song != null) loadCoverBitmap(song) else null
                    val notification = buildNeonCyanNotification(song, state.isPlaying, cover)
                    updateNotification(notification)
                }
        }

        // 监听大尺寸封面更新通知，热重载通知栏封面
        serviceScope.launch {
            com.orbit.music.utils.CoverHelper.coverUpdatedFlow.collect { updatedSongId ->
                val currentSong = playerManager.playbackState.value.currentSong
                if (currentSong != null && currentSong.id == updatedSongId) {
                    lastCoverSongId = -1L
                    cachedCoverBitmap = null
                    val newCover = loadCoverBitmap(currentSong)
                    val notification = buildNeonCyanNotification(currentSong, playerManager.playbackState.value.isPlaying, newCover)
                    updateNotification(notification)
                }
            }
        }
    }

    private fun safeStartForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
                startForeground(NOTIFICATION_ID, notification, serviceType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to startForeground for MusicPlaybackService", e)
        }
    }

    private fun updateNotification(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    private fun songToMediaItem(song: Song): MediaItem {
        return MediaItem.Builder()
            .setMediaId(song.id.toString())
            .setUri(Uri.parse(song.path))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.albumArtUri?.let { Uri.parse(it) })
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build()
            )
            .build()
    }

    private suspend fun loadCoverBitmap(song: Song): Bitmap? = withContext(Dispatchers.IO) {
        if (song.id == lastCoverSongId && cachedCoverBitmap != null) {
            return@withContext cachedCoverBitmap
        }

        // 优先使用 CoverHelper 获取单曲专属高保真封面 (杜绝 albumId 混淆串台)
        val bitmap = com.orbit.music.utils.CoverHelper.getCoverBitmap(
            context = this@MusicPlaybackService,
            songId = song.id,
            path = song.path,
            album = song.album
        )

        // 3. 统一处理为定格圆角微光封面
        val processedBitmap = bitmap?.let { createRoundedBitmap(it, 20f) }

        lastCoverSongId = song.id
        cachedCoverBitmap = processedBitmap
        processedBitmap
    }

    /**
     * 为封面图添加精致防狗牙平滑圆角
     */
    private fun createRoundedBitmap(src: Bitmap, cornerRadiusPx: Float): Bitmap {
        return try {
            val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
            canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(src, 0f, 0f, paint)
            output
        } catch (e: Exception) {
            src
        }
    }

    private var defaultAlbumArtBitmap: Bitmap? = null

    private fun getDefaultAlbumArt(): Bitmap {
        val cached = defaultAlbumArtBitmap
        if (cached != null && !cached.isRecycled) return cached
        val size = 256
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 深黑底色带平滑圆角
        paint.color = 0xFF181B22.toInt()
        val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
        canvas.drawRoundRect(rect, 36f, 36f, paint)

        // 黑胶同心环
        paint.style = Paint.Style.STROKE
        paint.color = 0xFF2C3240.toInt()
        paint.strokeWidth = 5f
        canvas.drawCircle(size / 2f, size / 2f, size * 0.35f, paint)

        paint.color = 0xFF00E5FF.toInt()
        paint.alpha = 150
        paint.strokeWidth = 3.5f
        canvas.drawCircle(size / 2f, size / 2f, size * 0.22f, paint)

        // 中心圆盘
        paint.style = Paint.Style.FILL
        paint.color = 0xFF00E5FF.toInt()
        paint.alpha = 220
        canvas.drawCircle(size / 2f, size / 2f, size * 0.08f, paint)

        defaultAlbumArtBitmap = bitmap
        return bitmap
    }

    /**
     * 构建纯净沉浸式系统媒体通知 (NotificationCompat.MediaStyle)
     * 1. 彻底去除系统强制的 Header（无播放器名称、无左侧多余 ICON）
     * 2. 缩略图（LargeIcon）直接定格显示在卡片右侧/背景大图上
     * 3. 歌曲播放过程中零刷新零闪烁
     */
    private fun buildNeonCyanNotification(song: Song?, isPlaying: Boolean, coverBitmap: Bitmap?): Notification {
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val prevPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(ACTION_PREV).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPausePendingIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(ACTION_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextPendingIntent = PendingIntent.getBroadcast(
            this,
            3,
            Intent(ACTION_NEXT).setPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 仅保留真实的歌曲名和歌手名，绝不输出任何播放器名称
        val title = song?.title?.takeIf { it.isNotBlank() } ?: "未在播放"
        val artist = if (song != null) {
            val a = song.artist.trim()
            val b = song.album.trim()
            when {
                a.isNotBlank() && a != "<unknown>" && b.isNotBlank() && b != "<unknown>" -> "$a • $b"
                a.isNotBlank() && a != "<unknown>" -> a
                b.isNotBlank() && b != "<unknown>" -> b
                else -> ""
            }
        } else ""

        val playPauseIcon = if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        val effectiveCover = coverBitmap ?: getDefaultAlbumArt()

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transparent) // 纯透明小图标，不显示任何多余白色角标
            .setContentTitle(title)
            .setContentText(artist)
            .setLargeIcon(effectiveCover) // 缩略图作为 LargeIcon 原生定格显示！
            .setContentIntent(openAppPendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .setShowWhen(false)
            .setColor(0xFFFFEAA7.toInt())
            .setColorized(true)
            .addAction(R.drawable.ic_notif_prev, "Previous", prevPendingIntent)
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", playPausePendingIntent)
            .addAction(R.drawable.ic_notif_next, "Next", nextPendingIntent)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows now playing music controls and album art"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "MusicPlaybackService onTaskRemoved: persisting immediate playback position")
        val playerManager = MusicPlayerManager.getInstance(this)
        val pos = playerManager.exoPlayer.currentPosition
        val song = playerManager.playbackState.value.currentSong
        playerManager.saveLastPlayedSong(song, pos, syncImmediately = true)
    }

    override fun onDestroy() {
        stateObserverJob?.cancel()
        try {
            unregisterReceiver(carMusicCommandReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "carMusicCommandReceiver already unregistered", e)
        }
        mediaSession?.run {
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ROOT_ID = "ROOT_ORBIT_MUSIC"
        const val ACTION_MUSIC_SERVICE_COMMAND = "com.android.music.musicservicecommand"

        const val ACTION_PREV = "com.orbit.music.ACTION_PREV"
        const val ACTION_PLAY_PAUSE = "com.orbit.music.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.orbit.music.ACTION_NEXT"

        fun start(context: Context) {
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    context.startForegroundService(intent)
                } catch (e: Exception) {
                    context.startService(intent)
                }
            } else {
                context.startService(intent)
            }
        }
    }
}
