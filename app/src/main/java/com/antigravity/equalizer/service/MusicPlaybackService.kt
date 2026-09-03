package com.antigravity.equalizer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.*
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.antigravity.equalizer.R
import com.antigravity.equalizer.audio.MusicPlayerManager
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 核心前台音乐播放服务：
 * 1. 采用自定义 RemoteViews 左右布局（左侧高保真大缩略图，右侧歌曲信息与控制条）
 * 2. 播放控制按键（上一曲、播放/暂停圆形大按键、下一曲）强制统一为程序专属荧光青高亮色 (#00E5FF)
 * 3. 彻底避免 Android 系统厂商 ROM 对 Action 进行灰色/黑色遮罩篡改
 */
class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var stateObserverJob: Job? = null
    private var cachedCoverBitmap: Bitmap? = null
    private var lastCoverSongId: Long = -1L

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "MusicPlaybackService onCreate (Custom Neon Cyan RemoteViews)")
        createNotificationChannel()

        val playerManager = MusicPlayerManager.getInstance(this)

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // 构造 Media3 MediaSession
        mediaSession = MediaSession.Builder(this, playerManager.exoPlayer)
            .setSessionActivity(openAppIntent)
            .setCallback(object : MediaSession.Callback {})
            .build()

        // 初始拉起前台
        val initialSong = playerManager.playbackState.value.currentSong
        val initialNotification = buildNeonCyanNotification(
            song = initialSong,
            isPlaying = playerManager.playbackState.value.isPlaying,
            coverBitmap = null
        )
        safeStartForeground(initialNotification)

        // 监听播放状态实时刷新通知 (关键优化：仅在切歌或播放暂停状态变化时更新，进度变化绝不刷新，保证缩略图绝对定格显示！)
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    private suspend fun loadCoverBitmap(song: Song): Bitmap? = withContext(Dispatchers.IO) {
        if (song.id == lastCoverSongId && cachedCoverBitmap != null) {
            return@withContext cachedCoverBitmap
        }

        var bitmap: Bitmap? = null

        // 1. 尝试从 MediaStore URI 解码
        if (!song.albumArtUri.isNullOrBlank()) {
            try {
                contentResolver.openInputStream(Uri.parse(song.albumArtUri))?.use { stream ->
                    bitmap = BitmapFactory.decodeStream(stream)
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        // 2. 尝试从音频文件读取内置 ID3 封面
        if (bitmap == null && song.path.isNotBlank()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(song.path)
                val rawArt = retriever.embeddedPicture
                if (rawArt != null && rawArt.isNotEmpty()) {
                    bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size)
                }
                retriever.release()
            } catch (e: Exception) {
                // ignore
            }
        }

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
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MusicPlaybackService"
        const val CHANNEL_ID = "music_playback_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_PREV = "com.antigravity.equalizer.ACTION_PREV"
        const val ACTION_PLAY_PAUSE = "com.antigravity.equalizer.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.antigravity.equalizer.ACTION_NEXT"

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
