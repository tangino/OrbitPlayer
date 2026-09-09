package com.antigravity.equalizer.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Collections

/**
 * 音乐专属封面高保真提取、缓存与防串台辅助引擎
 *
 * 核心设计准则：
 * 1. 严格单曲绑定：绝不使用 Android MediaStore 粗暴按 albumId 共享的旧版相册 URI，避免未知专辑或同目录歌曲封面错乱。
 * 2. 严密提取优先级：单曲专属内嵌 ID3 封面 > 同名图片封面 > 明确专辑的同目录封面 > 确认为无封面。
 * 3. 绝不跨歌曲借调：若歌曲本身无封面，坚决返回 null，确保由 UI 和通知栏呈现纯净的默认矢量音符占位，不移花接木。
 * 4. 大尺寸在线升级：支持与 MusicBrainz / Cover Art Archive 对比，仅在网络封面尺寸更大时安全覆盖更新。
 */
object CoverHelper {

    private const val TAG = "CoverHelper"
    private const val COVERS_DIR_NAME = "covers"

    // 内存 Bitmap 缓存 (上限约 24MB)
    private val memoryCache: LruCache<Long, Bitmap> by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSizeKb = (maxMemoryKb / 8).coerceIn(4096, 32768)
        object : LruCache<Long, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: Long, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    // 记录已知无封面的歌曲 ID，避免频繁进行无意义的文件 I/O
    private val noCoverSet = Collections.synchronizedSet(HashSet<Long>())

    // 记录已知已尝试在线搜索或无更高清封面的歌曲 ID，避免频繁重复网络调用
    private val onlineSearchAttemptedSet = Collections.synchronizedSet(HashSet<Long>())

    // 封面更新事件流，通知 UI 与播放服务即时刷新
    private val _coverUpdatedFlow = MutableSharedFlow<Long>(extraBufferCapacity = 16)
    val coverUpdatedFlow: SharedFlow<Long> = _coverUpdatedFlow.asSharedFlow()

    // 封面全局版本时间戳，用于 Compose 界面重组与 Coil 缓存重载感知
    private val _coverVersion = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val coverVersion: kotlinx.coroutines.flow.StateFlow<Long> = _coverVersion

    /**
     * 获取或按需提取歌曲专属封面缓存文件
     */
    fun getOrExtractCoverFile(
        context: Context,
        songId: Long,
        path: String?,
        album: String?
    ): File? {
        if (songId == 0L || path.isNullOrBlank()) return null

        val coversDir = File(context.cacheDir, COVERS_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        val cacheFile = File(coversDir, "cover_${songId}.jpg")

        // 1. 若本地专属缓存已存在且有效，直接秒开返回
        if (cacheFile.exists() && cacheFile.length() > 128) {
            return cacheFile
        }

        // 2. 若已记录为确认无封面歌曲，直接跳过
        if (noCoverSet.contains(songId)) {
            return null
        }

        val audioFile = File(path)
        if (!audioFile.exists() || !audioFile.isFile) {
            noCoverSet.add(songId)
            return null
        }

        // 3. 优先级 1：提取单曲自身内嵌 APIC / PIC ID3 封面图片
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val embeddedBytes = retriever.embeddedPicture
            retriever.release()

            if (embeddedBytes != null && embeddedBytes.isNotEmpty()) {
                FileOutputStream(cacheFile).use { out ->
                    out.write(embeddedBytes)
                    out.flush()
                }
                return cacheFile
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract embedded artwork for: $path", e)
        }

        val parentDir = audioFile.parentFile

        // 4. 优先级 2：检查同目录下专属同名图片 (如 SongName.jpg / SongName.png)
        if (parentDir != null && parentDir.exists() && parentDir.isDirectory) {
            val baseName = audioFile.nameWithoutExtension
            val candidateExts = listOf("jpg", "jpeg", "png", "webp")
            for (ext in candidateExts) {
                val sameNameImg = File(parentDir, "$baseName.$ext")
                if (sameNameImg.exists() && sameNameImg.length() > 128) {
                    return sameNameImg
                }
            }

            // 5. 优先级 3：仅当歌曲拥有明确专辑名 (非空且非 Unknown) 时，检查同目录下 album 封面
            val hasExplicitAlbum = !album.isNullOrBlank() &&
                    album != "Unknown Album" &&
                    album != "<unknown>" &&
                    album != "未知专辑"

            if (hasExplicitAlbum) {
                val albumCandidates = listOf("cover.jpg", "cover.png", "folder.jpg", "album.jpg")
                for (name in albumCandidates) {
                    val albumImg = File(parentDir, name)
                    if (albumImg.exists() && albumImg.length() > 128) {
                        return albumImg
                    }
                }
            }
        }

        // 6. 确定无封面，记入集合，坚决返回 null，杜绝跨曲借调错乱
        noCoverSet.add(songId)
        return null
    }

    /**
     * 获取歌曲专属封面 Bitmap（优先读内存 LRU，次查专属缓存）
     */
    fun getCoverBitmap(
        context: Context,
        songId: Long,
        path: String?,
        album: String?
    ): Bitmap? {
        if (songId == 0L) return null

        val memCached = memoryCache.get(songId)
        if (memCached != null && !memCached.isRecycled) {
            return memCached
        }

        val file = getOrExtractCoverFile(context, songId, path, album) ?: return null
        return try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                memoryCache.put(songId, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Decode cover bitmap error: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * 高效获取歌曲本地当前封面的像素尺寸 (宽 x 高)，仅解码边界不载入完整位图
     * 若歌曲当前无有效封面则返回 null
     */
    fun getCoverDimensions(
        context: Context,
        songId: Long,
        path: String?,
        album: String?
    ): Pair<Int, Int>? {
        val file = getOrExtractCoverFile(context, songId, path, album) ?: return null
        if (!file.exists() || file.length() < 128) return null

        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                Pair(options.outWidth, options.outHeight)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode cover dimensions for song $songId", e)
            null
        }
    }

    /**
     * 将在线下载的更大尺寸封面安全持久化覆盖为单曲专属封面
     * 并同步更新内存缓存与通知事件流
     */
    fun updateCoverFile(
        context: Context,
        songId: Long,
        sourceFile: File
    ): Boolean {
        if (songId == 0L || !sourceFile.exists() || sourceFile.length() < 128) return false

        try {
            val coversDir = File(context.cacheDir, COVERS_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val targetFile = File(coversDir, "cover_${songId}.jpg")

            // 复制临时大图文件至缓存文件
            sourceFile.inputStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }

            // 从无封面集合中移除
            noCoverSet.remove(songId)

            // 刷新内存缓存
            val newBitmap = BitmapFactory.decodeFile(targetFile.absolutePath)
            if (newBitmap != null) {
                memoryCache.put(songId, newBitmap)
            } else {
                memoryCache.remove(songId)
            }

            // 发送更新通知
            _coverVersion.value = System.currentTimeMillis()
            _coverUpdatedFlow.tryEmit(songId)
            Log.i(TAG, "Successfully updated large cover for song $songId (size: ${targetFile.length()} bytes)")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update cover file for song $songId", e)
            return false
        }
    }

    /**
     * 标记该歌曲已经尝试过在线封面检索（无论成功或未匹配），避免后续重复发起无谓网络请求
     */
    fun markOnlineSearchAttempted(songId: Long) {
        onlineSearchAttemptedSet.add(songId)
    }

    /**
     * 检查该歌曲是否已经尝试过在线封面检索
     */
    fun isOnlineSearchAttempted(songId: Long): Boolean {
        return onlineSearchAttemptedSet.contains(songId)
    }

    /**
     * 重置歌曲在线检索状态 (例如用户手动点击重新匹配时)
     */
    fun resetOnlineSearchStatus(songId: Long) {
        onlineSearchAttemptedSet.remove(songId)
        noCoverSet.remove(songId)
    }

    /**
     * 判断指定歌曲是否已下载过本地专属封面文件
     */
    fun hasDownloadedCover(context: Context, songId: Long): Boolean {
        if (songId == 0L) return false
        val coversDir = File(context.cacheDir, COVERS_DIR_NAME)
        val cacheFile = File(coversDir, "cover_${songId}.jpg")
        return cacheFile.exists() && cacheFile.length() > 128
    }

    /**
     * 清理所有封面缓存 (可在用户触发重新扫描时调用)
     */
    fun clearCache(context: Context) {
        memoryCache.evictAll()
        noCoverSet.clear()
        onlineSearchAttemptedSet.clear()
        try {
            val dir = File(context.cacheDir, COVERS_DIR_NAME)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Clear cover cache error", e)
        }
    }
}
