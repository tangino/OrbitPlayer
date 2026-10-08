package com.orbit.music.data.scanner

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.orbit.music.data.model.Song
import com.orbit.music.data.provider.AudioCoverProvider
import com.orbit.music.utils.AudioTagExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.fs.UsbFile
import me.jahnen.libaums.core.fs.UsbFileInputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 方案二：基于 USB Host API (libaums) 的 U 盘音频扫描与管理引擎
 * 针对 Android 11 车机无权限弹窗场景设计：直接与 USB 大容量存储设备通信，绕过系统权限框架。
 */
class UsbStorageScanner(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val usbCacheDir = File(context.cacheDir, "usb_media_cache").apply { if (!exists()) mkdirs() }

    /**
     * 扫描所有已连接的 USB Mass Storage (U 盘) 设备并提取音频
     */
    suspend fun scanUsbDevices(
        targetDirectoryName: String? = null,
        onProgress: ((current: Int, total: Int, name: String) -> Unit)? = null
    ): List<Song> = withContext(Dispatchers.IO) {
        val resultSongs = mutableListOf<Song>()
        val massStorageDevices = UsbMassStorageDevice.getMassStorageDevices(context)

        if (massStorageDevices.isEmpty()) {
            Log.d(TAG, "No USB Mass Storage devices found via USB Host.")
            return@withContext emptyList()
        }

        Log.i(TAG, "Found ${massStorageDevices.size} USB mass storage device(s).")

        for (device in massStorageDevices) {
            val usbDevice = device.usbDevice
            // 检查是否有 USB 硬件权限
            if (!usbManager.hasPermission(usbDevice)) {
                Log.w(TAG, "No permission for USB device: ${usbDevice.deviceName}, requesting permission...")
                requestUsbPermission(usbDevice)
                continue
            }

            try {
                // 1. 初始化底层 USB 块设备
                device.init()

                // 2. 遍历所有文件系统分区 (FAT32/exFAT 等)
                for (partition in device.partitions) {
                    val fileSystem = partition.fileSystem
                    val root = fileSystem.rootDirectory
                    Log.i(TAG, "Scanning USB partition: ${fileSystem.volumeLabel.ifBlank { "USB Drive" }}, capacity: ${fileSystem.capacity}")

                    // 3. 递归遍历指定目录或根目录
                    val audioFiles = mutableListOf<UsbFile>()
                    if (!targetDirectoryName.isNullOrBlank()) {
                        val targetDir = findDirectory(root, targetDirectoryName)
                        if (targetDir != null) {
                            collectAudioFiles(targetDir, audioFiles, maxDepth = 4)
                        } else {
                            collectAudioFiles(root, audioFiles, maxDepth = 4)
                        }
                    } else {
                        collectAudioFiles(root, audioFiles, maxDepth = 4)
                    }

                    Log.i(TAG, "Found ${audioFiles.size} audio file(s) in USB drive.")

                    // 4. 解析音频文件元数据并生成 Song 对象
                    for ((index, usbFile) in audioFiles.withIndex()) {
                        try {
                            onProgress?.invoke(index + 1, audioFiles.size, usbFile.name)
                            val song = processUsbAudioFile(usbFile, fileSystem.volumeLabel)
                            if (song != null) {
                                resultSongs.add(song)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing USB audio file ${usbFile.name}", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error accessing USB Mass Storage device", e)
            } finally {
                try {
                    device.close()
                } catch (_: Exception) {}
            }
        }

        resultSongs
    }

    /**
     * 递归寻找特定名称的目录 (如 "Music" 或 "我的音乐")
     */
    private fun findDirectory(currentDir: UsbFile, targetName: String): UsbFile? {
        if (!currentDir.isDirectory) return null
        val files = try { currentDir.listFiles() } catch (e: Exception) { return null }
        for (file in files) {
            if (file.isDirectory) {
                if (file.name.equals(targetName, ignoreCase = true)) {
                    return file
                }
                val found = findDirectory(file, targetName)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * 递归收集所有音频文件
     */
    private fun collectAudioFiles(dir: UsbFile, results: MutableList<UsbFile>, maxDepth: Int) {
        if (maxDepth <= 0 || !dir.isDirectory) return
        val files = try { dir.listFiles() } catch (e: Exception) { return }
        for (file in files) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".")) {
                    collectAudioFiles(file, results, maxDepth - 1)
                }
            } else if (isAudioFile(file.name) && file.length > 50000) {
                results.add(file)
            }
        }
    }

    /**
     * 处理 USB 音频：读取文件头标签，必要时准备可供 ExoPlayer 读取的私有缓存副本
     */
    private fun processUsbAudioFile(usbFile: UsbFile, volumeName: String): Song? {
        val fileName = usbFile.name
        val nameWithoutExt = fileName.substringBeforeLast(".")
        val length = usbFile.length

        // 将文件缓存或同步到应用私有目录，确保播放与元数据解析100%零权限依赖
        val cachedFile = File(usbCacheDir, "${fileName.hashCode()}_$fileName")
        if (!cachedFile.exists() || cachedFile.length() != length) {
            // 将 USB 数据流写入私有缓存文件
            try {
                UsbFileInputStream(usbFile).use { input ->
                    FileOutputStream(cachedFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cache USB file: $fileName", e)
                return null
            }
        }

        val localPath = cachedFile.absolutePath

        var title = nameWithoutExt
        var artist = "Unknown Artist"
        var album = if (volumeName.isNotBlank()) volumeName else "U盘音乐"
        var durationMs = 180000L
        var year = 0

        // 优先使用 MediaMetadataRetriever 读取时长等基础信息
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(localPath)
            val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!dur.isNullOrBlank()) {
                durationMs = dur.toLongOrNull() ?: 180000L
            }
            val metaTitle = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
            val metaArtist = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val metaAlbum = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (!metaTitle.isNullOrBlank()) title = metaTitle
            if (!metaArtist.isNullOrBlank()) artist = metaArtist
            if (!metaAlbum.isNullOrBlank()) album = metaAlbum
            retriever.release()
        } catch (_: Exception) {}

        // 使用原生 AudioTagExtractor 进行深度无损元数据与年份提取
        try {
            val tags = AudioTagExtractor.extractMetadata(localPath)
            if (!tags.title.isNullOrBlank()) title = tags.title
            if (!tags.artist.isNullOrBlank()) artist = tags.artist
            if (!tags.album.isNullOrBlank()) album = tags.album
            if (tags.year != null && tags.year > 0) year = tags.year
        } catch (_: Exception) {}

        // 文件名智能拆解 (如 "周杰伦 - 晴天.mp3")
        if (artist == "Unknown Artist" || artist.isBlank()) {
            val clean = nameWithoutExt.replaceFirst(Regex("""^\d{1,3}[\s\.\-_]+"""), "").trim()
            if (clean.contains(" - ")) {
                val parts = clean.split(" - ")
                if (parts.size >= 2) {
                    artist = parts[0].trim()
                    if (title == nameWithoutExt) {
                        title = parts.drop(1).joinToString(" - ").trim()
                    }
                }
            }
        }

        val songId = ("usb_" + usbFile.name + "_" + length).hashCode().toLong()
        val albumArtUri = AudioCoverProvider.buildSongCoverUri(songId, localPath, album)

        return Song(
            id = songId,
            title = title,
            artist = artist,
            album = album,
            albumId = 0L,
            durationMs = durationMs,
            path = localPath,
            size = length,
            albumArtUri = albumArtUri,
            folderPath = "U盘: ${volumeName.ifBlank { "USB" }}",
            year = year,
            mimeType = "audio/*"
        )
    }

    /**
     * 动态请求 USB 硬件授权
     */
    fun requestUsbPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun isAudioFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp3") ||
                lower.endsWith(".flac") ||
                lower.endsWith(".wav") ||
                lower.endsWith(".m4a") ||
                lower.endsWith(".ogg") ||
                lower.endsWith(".aac") ||
                lower.endsWith(".ape") ||
                lower.endsWith(".wma") ||
                lower.endsWith(".opus")
    }

    companion object {
        private const val TAG = "UsbStorageScanner"
        const val ACTION_USB_PERMISSION = "com.orbit.music.USB_PERMISSION"
    }
}
