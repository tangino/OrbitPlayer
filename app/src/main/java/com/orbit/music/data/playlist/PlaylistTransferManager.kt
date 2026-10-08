package com.orbit.music.data.playlist

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.orbit.music.data.db.AppDatabase
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.engine.OnlineAudioSourceManager
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.repository.OnlineMusicRepository
import com.orbit.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 歌单导入导出数据传输结构与核心调度引擎
 * 支持本地歌单与网络歌单的导出（JSON / M3U8）、文件导入、网络链接分享解析导入
 */
object PlaylistTransferManager {

    private const val TAG = "PlaylistTransferMgr"

    data class TransferSongItem(
        val title: String,
        val artist: String,
        val album: String = "",
        val durationMs: Long = 0L,
        val path: String = "",
        val platform: OnlinePlatform? = null,
        val songId: String? = null,
        val coverUrl: String? = null
    )

    data class TransferPlaylist(
        val version: Int = 1,
        val title: String,
        val description: String = "",
        val coverUrl: String? = null,
        val platform: OnlinePlatform? = null,
        val originalId: String? = null,
        val creatorName: String? = null,
        val creatorAvatarUrl: String? = null,
        val playCount: Long = 0L,
        val trackCount: Int = 0,
        val exportTimestamp: Long = System.currentTimeMillis(),
        val songs: List<TransferSongItem>
    )

    enum class ExportFormat(val extension: String, val mimeType: String, val displayName: String) {
        JSON("json", "application/json", "JSON 完整备份格式 (.json)"),
        M3U8("m3u8", "audio/x-mpegurl", "M3U8 通用播放列表 (.m3u8)")
    }

    /**
     * 将本地歌单转换为通用 TransferPlaylist 对象
     */
    fun toTransferPlaylist(
        playlistName: String,
        songs: List<Song>,
        description: String = "",
        platform: OnlinePlatform? = null
    ): TransferPlaylist {
        val items = songs.map { song ->
            var detectedPlatform = song.sourcePlatform ?: song.originalPlatform
            var detectedSongId: String? = null

            if (song.path.startsWith("online://")) {
                val uri = Uri.parse(song.path)
                val platId = uri.host ?: ""
                detectedSongId = uri.lastPathSegment
                if (detectedPlatform == null) {
                    detectedPlatform = OnlinePlatform.values().firstOrNull { it.id == platId }
                }
            }

            TransferSongItem(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                path = song.path,
                platform = detectedPlatform,
                songId = detectedSongId,
                coverUrl = song.albumArtUri
            )
        }

        return TransferPlaylist(
            title = playlistName,
            description = description,
            platform = platform,
            trackCount = items.size,
            songs = items
        )
    }

    /**
     * 将在线网络歌单转换为 TransferPlaylist 对象
     */
    fun toTransferPlaylist(
        onlinePlaylist: OnlinePlaylist,
        songs: List<OnlineSongItem>
    ): TransferPlaylist {
        val items = songs.map { song ->
            TransferSongItem(
                title = song.title,
                artist = song.artist,
                album = song.album,
                durationMs = song.durationMs,
                path = "online://${song.platform.id}/${song.id}",
                platform = song.platform,
                songId = song.id,
                coverUrl = song.coverUrl
            )
        }

        return TransferPlaylist(
            title = onlinePlaylist.title,
            description = onlinePlaylist.description ?: "",
            coverUrl = onlinePlaylist.coverUrl,
            platform = onlinePlaylist.platform,
            originalId = onlinePlaylist.id,
            creatorName = onlinePlaylist.creatorName,
            creatorAvatarUrl = onlinePlaylist.creatorAvatarUrl,
            playCount = onlinePlaylist.playCount,
            trackCount = if (items.isNotEmpty()) items.size else onlinePlaylist.trackCount,
            songs = items
        )
    }

    /**
     * 将 TransferPlaylist 转为 OnlinePlaylist 实体对象（若具备平台信息）
     */
    fun toOnlinePlaylist(playlist: TransferPlaylist): OnlinePlaylist? {
        val platform = playlist.platform ?: return null
        val id = playlist.originalId ?: playlist.title.hashCode().toString()
        return OnlinePlaylist(
            id = id,
            platform = platform,
            title = playlist.title,
            coverUrl = playlist.coverUrl ?: "",
            playCount = playlist.playCount,
            trackCount = if (playlist.songs.isNotEmpty()) playlist.songs.size else playlist.trackCount,
            creatorName = playlist.creatorName,
            creatorAvatarUrl = playlist.creatorAvatarUrl,
            description = playlist.description
        )
    }

    /**
     * 恢复/注册网络歌单至收藏管理器
     */
    fun restoreToOnlineFavorites(context: Context, playlist: TransferPlaylist): OnlinePlaylist? {
        val online = toOnlinePlaylist(playlist) ?: return null
        com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager.getInstance(context).addFavorite(online)
        return online
    }

    /**
     * 序列化歌单为 JSON 字符串
     */
    fun exportToJson(playlist: TransferPlaylist): String {
        val root = JSONObject().apply {
            put("format", "OrBitPlayerPlaylist")
            put("version", playlist.version)
            put("title", playlist.title)
            put("description", playlist.description)
            put("coverUrl", playlist.coverUrl ?: "")
            put("platform", playlist.platform?.id ?: "")
            put("originalId", playlist.originalId ?: "")
            put("creatorName", playlist.creatorName ?: "")
            put("creatorAvatarUrl", playlist.creatorAvatarUrl ?: "")
            put("playCount", playlist.playCount)
            put("trackCount", playlist.songs.size)
            put("exportTimestamp", playlist.exportTimestamp)
            put("songCount", playlist.songs.size)

            val songsArray = JSONArray()
            for (song in playlist.songs) {
                val songObj = JSONObject().apply {
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("durationMs", song.durationMs)
                    put("path", song.path)
                    put("platform", song.platform?.id ?: "")
                    put("songId", song.songId ?: "")
                    put("coverUrl", song.coverUrl ?: "")
                }
                songsArray.put(songObj)
            }
            put("songs", songsArray)
        }
        return root.toString(2)
    }

    /**
     * 序列化歌单为标准 M3U8 格式
     */
    fun exportToM3u8(playlist: TransferPlaylist): String {
        val sb = java.lang.StringBuilder()
        sb.append("#EXTM3U\n")
        sb.append("#PLAYLIST:").append(playlist.title).append("\n")
        if (playlist.description.isNotBlank()) {
            sb.append("#DESCRIPTION:").append(playlist.description.replace("\n", " ")).append("\n")
        }
        if (!playlist.coverUrl.isNullOrBlank()) {
            sb.append("#EXT-ORBIT-COVER:").append(playlist.coverUrl).append("\n")
        }
        if (playlist.platform != null) {
            sb.append("#EXT-ORBIT-PLATFORM:").append(playlist.platform.id).append(":").append(playlist.originalId ?: "").append("\n")
        }
        if (!playlist.creatorName.isNullOrBlank()) {
            sb.append("#EXT-ORBIT-CREATOR:").append(playlist.creatorName).append("\n")
        }
        sb.append("\n")

        for (song in playlist.songs) {
            val seconds = (song.durationMs / 1000).coerceAtLeast(0)
            sb.append("#EXTINF:").append(seconds).append(",")
            sb.append(song.artist.ifBlank { "Unknown Artist" }).append(" - ").append(song.title).append("\n")
            if (!song.coverUrl.isNullOrBlank()) {
                sb.append("#EXTIMG:").append(song.coverUrl).append("\n")
            }
            if (song.platform != null && !song.songId.isNullOrBlank()) {
                sb.append("#EXT-ORBIT-ONLINE:").append(song.platform.id).append(":").append(song.songId).append("\n")
            }
            sb.append(song.path.ifBlank { "online://${song.platform?.id ?: "unknown"}/${song.songId ?: "0"}" }).append("\n\n")
        }

        return sb.toString()
    }

    /**
     * 将内容保存到本地文件并返回分享 Uri 或公开路径
     */
    suspend fun saveAndGetShareUri(
        context: Context,
        fileName: String,
        content: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val exportDir = File(context.cacheDir, "playlist_exports").apply { mkdirs() }
            val file = File(exportDir, sanitized)
            FileOutputStream(file).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { osw ->
                    osw.write(content)
                }
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save export file: ${e.message}", e)
            null
        }
    }

    /**
     * 将导出内容保存至系统的公开 Download 或 Music 目录
     */
    suspend fun saveToPublicDirectory(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val sanitized = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, sanitized)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OrBitPlayer")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw java.io.IOException("无法创建公共导出文件")
                resolver.openOutputStream(uri)?.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { osw ->
                        osw.write(content)
                    }
                }
                "已保存至「下载目录/OrBitPlayer/$sanitized」"
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "OrBitPlayer").apply { mkdirs() }
                val targetFile = File(dir, sanitized)
                FileOutputStream(targetFile).use { fos ->
                    OutputStreamWriter(fos, Charsets.UTF_8).use { osw ->
                        osw.write(content)
                    }
                }
                "已保存至「${targetFile.absolutePath}」"
            }
        }
    }

    /**
     * 调起系统分享面板分享歌单文件
     */
    fun sharePlaylistFile(context: Context, fileUri: Uri, title: String, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "分享歌单: $title")
            putExtra(Intent.EXTRA_TEXT, "来自 OrBitPlayer 的歌单: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "导出/分享歌单")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * 解析导入的 JSON 格式文本
     */
    fun parseJson(jsonStr: String): TransferPlaylist? {
        return try {
            val root = JSONObject(jsonStr)
            val title = root.optString("title", "导入歌单").ifBlank { "导入歌单" }
            val description = root.optString("description", "")
            val rawCover = root.optString("coverUrl", "").takeIf { it.isNotBlank() }
            val coverUrl = rawCover?.let { if (it.startsWith("//")) "https:$it" else it }
            val platformStr = root.optString("platform", "").takeIf { it.isNotBlank() }
            val platform = OnlinePlatform.values().firstOrNull { it.id == platformStr }
            val originalId = root.optString("originalId", "").takeIf { it.isNotBlank() }
            val creatorName = root.optString("creatorName", "").takeIf { it.isNotBlank() }
            val creatorAvatarUrl = root.optString("creatorAvatarUrl", "").takeIf { it.isNotBlank() }
            val playCount = root.optLong("playCount", 0L)
            val trackCount = root.optInt("trackCount", 0)
            val timestamp = root.optLong("exportTimestamp", System.currentTimeMillis())

            val songsArray = root.optJSONArray("songs") ?: JSONArray()
            val songs = mutableListOf<TransferSongItem>()

            for (i in 0 until songsArray.length()) {
                val item = songsArray.optJSONObject(i) ?: continue
                val sTitle = item.optString("title", "").trim()
                if (sTitle.isBlank()) continue
                val sArtist = item.optString("artist", "未知歌手").trim()
                val sAlbum = item.optString("album", "")
                val sDur = item.optLong("durationMs", 0L)
                val sPath = item.optString("path", "")
                val sPlatStr = item.optString("platform", "").takeIf { it.isNotBlank() }
                val sPlat = OnlinePlatform.values().firstOrNull { it.id == sPlatStr } ?: platform
                val sId = item.optString("songId", "").takeIf { it.isNotBlank() }
                val rawSongCover = item.optString("coverUrl", "").ifBlank { item.optString("albumArtUri", "") }.takeIf { it.isNotBlank() }
                val sCover = rawSongCover?.let { if (it.startsWith("//")) "https:$it" else it }

                songs.add(
                    TransferSongItem(
                        title = sTitle,
                        artist = sArtist,
                        album = sAlbum,
                        durationMs = sDur,
                        path = sPath,
                        platform = sPlat,
                        songId = sId,
                        coverUrl = sCover
                    )
                )
            }

            TransferPlaylist(
                title = title,
                description = description,
                coverUrl = coverUrl,
                platform = platform,
                originalId = originalId,
                creatorName = creatorName,
                creatorAvatarUrl = creatorAvatarUrl,
                playCount = playCount,
                trackCount = if (songs.isNotEmpty()) songs.size else trackCount,
                exportTimestamp = timestamp,
                songs = songs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON playlist: ${e.message}", e)
            null
        }
    }

    /**
     * 解析导入的 M3U / M3U8 格式文本
     */
    fun parseM3u8(content: String, fallbackTitle: String = "导入歌单"): TransferPlaylist {
        var title = fallbackTitle
        var description = ""
        var coverUrl: String? = null
        var platform: OnlinePlatform? = null
        var originalId: String? = null
        var creatorName: String? = null
        val songs = mutableListOf<TransferSongItem>()

        var currentExtInfTitle = ""
        var currentExtInfArtist = ""
        var currentDurationMs = 0L
        var currentCoverUrl: String? = null
        var currentOnlinePlatform: OnlinePlatform? = null
        var currentOnlineSongId: String? = null

        val lines = content.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                title = line.substringAfter(":").trim().ifBlank { fallbackTitle }
                continue
            }
            if (line.startsWith("#DESCRIPTION:", ignoreCase = true)) {
                description = line.substringAfter(":").trim()
                continue
            }
            if (line.startsWith("#EXT-ORBIT-COVER:", ignoreCase = true)) {
                coverUrl = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                continue
            }
            if (line.startsWith("#EXT-ORBIT-PLATFORM:", ignoreCase = true)) {
                val data = line.substringAfter(":").trim()
                val platStr = data.substringBefore(":")
                val plId = data.substringAfter(":").takeIf { it.isNotBlank() }
                platform = OnlinePlatform.values().firstOrNull { it.id == platStr }
                originalId = plId
                continue
            }
            if (line.startsWith("#EXT-ORBIT-CREATOR:", ignoreCase = true)) {
                creatorName = line.substringAfter(":").trim().takeIf { it.isNotBlank() }
                continue
            }
            if (line.startsWith("#EXTIMG:", ignoreCase = true)) {
                currentCoverUrl = line.substringAfter(":").trim()
                continue
            }
            if (line.startsWith("#EXT-ORBIT-ONLINE:", ignoreCase = true)) {
                val data = line.substringAfter(":").trim()
                val platStr = data.substringBefore(":")
                val songId = data.substringAfter(":")
                currentOnlinePlatform = OnlinePlatform.values().firstOrNull { it.id == platStr }
                currentOnlineSongId = songId
                continue
            }
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val afterTag = line.substringAfter(":")
                val durationSecStr = afterTag.substringBefore(",").trim()
                currentDurationMs = (durationSecStr.toLongOrNull() ?: 0L) * 1000L

                val info = afterTag.substringAfter(",").trim()
                if (info.contains(" - ")) {
                    currentExtInfArtist = info.substringBefore(" - ").trim()
                    currentExtInfTitle = info.substringAfter(" - ").trim()
                } else {
                    currentExtInfTitle = info
                    currentExtInfArtist = "未知歌手"
                }
                continue
            }

            if (!line.startsWith("#")) {
                // 此时 line 为音频路径或在线 URI
                val path = line
                var sTitle = currentExtInfTitle
                var sArtist = currentExtInfArtist

                if (sTitle.isBlank()) {
                    val name = File(path).nameWithoutExtension
                    if (name.contains(" - ")) {
                        sArtist = name.substringBefore(" - ").trim()
                        sTitle = name.substringAfter(" - ").trim()
                    } else {
                        sTitle = name.ifBlank { "未知歌曲" }
                        sArtist = "未知歌手"
                    }
                }

                var plat = currentOnlinePlatform ?: platform
                var sId = currentOnlineSongId

                if (path.startsWith("online://")) {
                    val uri = Uri.parse(path)
                    val platId = uri.host ?: ""
                    sId = uri.lastPathSegment
                    plat = OnlinePlatform.values().firstOrNull { it.id == platId } ?: plat
                }

                songs.add(
                    TransferSongItem(
                        title = sTitle,
                        artist = sArtist,
                        durationMs = currentDurationMs,
                        path = path,
                        platform = plat,
                        songId = sId,
                        coverUrl = currentCoverUrl
                    )
                )

                // 重置临时标签
                currentExtInfTitle = ""
                currentExtInfArtist = ""
                currentDurationMs = 0L
                currentCoverUrl = null
                currentOnlinePlatform = null
                currentOnlineSongId = null
            }
        }

        return TransferPlaylist(
            title = title,
            description = description,
            coverUrl = coverUrl,
            platform = platform,
            originalId = originalId,
            creatorName = creatorName,
            trackCount = songs.size,
            songs = songs
        )
    }

    /**
     * 从文本或分享链接中导入（支持各主流音乐平台歌单链接及纯歌名清单）
     */
    suspend fun importFromLinkOrText(
        context: Context,
        input: String
    ): Result<TransferPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = input.trim()
            if (trimmed.isBlank()) throw IllegalArgumentException("输入内容不能为空")

            // 1. 尝试通过网络链接解析平台与歌单 ID
            val onlineRepo = OnlineMusicRepository.getInstance()
            val parsedPlatformPair = onlineRepo.parseLinkOrText(trimmed)

            if (parsedPlatformPair != null) {
                val (platform, playlistId) = parsedPlatformPair
                val detailResult = onlineRepo.getPlaylistDetail(playlistId, platform, forceRefresh = true)
                val (onlinePlaylist, onlineSongs) = detailResult.getOrThrow()
                return@runCatching toTransferPlaylist(onlinePlaylist, onlineSongs)
            }

            // 2. 尝试作为 JSON 导入
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val jsonParsed = parseJson(trimmed)
                if (jsonParsed != null && jsonParsed.songs.isNotEmpty()) {
                    return@runCatching jsonParsed
                }
            }

            // 3. 尝试作为 M3U8 导入
            if (trimmed.contains("#EXTM3U") || trimmed.contains("#EXTINF")) {
                val m3uParsed = parseM3u8(trimmed)
                if (m3uParsed.songs.isNotEmpty()) {
                    return@runCatching m3uParsed
                }
            }

            // 4. 作为文本歌单列表解析 (每行一首: "歌名 - 歌手" 或 "歌手 - 歌名" 或 "歌名")
            val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("//") && !it.startsWith("#") }
            if (lines.isEmpty()) throw IllegalArgumentException("未能识别到有效的歌单链接或歌曲列表")

            val songs = mutableListOf<TransferSongItem>()
            for (line in lines) {
                val parts = if (line.contains(" - ")) {
                    line.split(" - ", limit = 2)
                } else if (line.contains("—")) {
                    line.split("—", limit = 2)
                } else if (line.contains("\t")) {
                    line.split("\t", limit = 2)
                } else {
                    listOf(line, "未知歌手")
                }

                val title = parts[0].trim()
                val artist = parts.getOrNull(1)?.trim() ?: "未知歌手"

                if (title.isNotBlank()) {
                    songs.add(
                        TransferSongItem(
                            title = title,
                            artist = artist,
                            path = ""
                        )
                    )
                }
            }

            val defaultTitle = "文本导入歌单 (${SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date())})"
            TransferPlaylist(
                title = defaultTitle,
                description = "由文本歌曲清单批量解析生成",
                songs = songs
            )
        }
    }

    /**
     * 将导入的 TransferPlaylist 写入本地数据库并创建自建歌单
     * 自动智能关联本地匹配歌曲与在线曲目，返回新建歌单 ID
     */
    suspend fun saveTransferPlaylistToLocal(
        context: Context,
        playlist: TransferPlaylist,
        customTitle: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val finalTitle = (customTitle ?: playlist.title).trim().ifBlank { "导入歌单" }
        val db = AppDatabase.getInstance(context)
        val musicRepo = MusicRepository.getInstance(context)
        val allLocalSongs = musicRepo.allSongs.value

        // 1. 创建本地自建歌单
        val playlistId = db.songDao.insertPlaylist(finalTitle)

        // 2. 遍历歌曲项，查找匹配本地已有的歌曲，或注册在线歌曲
        val songsToInsert = mutableListOf<Song>()

        for (item in playlist.songs) {
            // 优先通过绝对路径精确查找
            var matchedLocal = if (item.path.isNotBlank() && !item.path.startsWith("online://")) {
                allLocalSongs.firstOrNull { it.path.equals(item.path, ignoreCase = true) }
            } else null

            // 其次通过 歌名 + 歌手 模糊匹配本地曲库
            if (matchedLocal == null && item.title.isNotBlank()) {
                matchedLocal = allLocalSongs.firstOrNull { local ->
                    local.title.trim().equals(item.title.trim(), ignoreCase = true) &&
                    (item.artist.isBlank() || item.artist == "未知歌手" || local.artist.contains(item.artist, ignoreCase = true) || item.artist.contains(local.artist, ignoreCase = true))
                }
            }

            if (matchedLocal != null) {
                songsToInsert.add(matchedLocal)
            } else {
                // 本地不存在，创建为标准在线/虚拟歌曲
                val plat = item.platform ?: playlist.platform ?: OnlinePlatform.NETEASE
                val songId = item.songId ?: item.title.hashCode().toString()
                val virtualId = OnlineAudioSourceManager.generateVirtualSongId(plat, songId)
                val path = if (item.path.startsWith("online://") || item.path.startsWith("http")) {
                    item.path
                } else {
                    "online://${plat.id}/$songId"
                }

                val virtualSong = Song(
                    id = virtualId,
                    title = item.title,
                    artist = item.artist.ifBlank { "未知歌手" },
                    album = item.album.ifBlank { playlist.title },
                    albumId = virtualId,
                    durationMs = item.durationMs,
                    path = path,
                    size = 0L,
                    albumArtUri = item.coverUrl ?: playlist.coverUrl,
                    folderPath = "导入歌单 - $finalTitle",
                    mimeType = "audio/mpeg",
                    sourcePlatform = plat,
                    sourceTag = plat.displayName,
                    originalPlatform = plat
                )
                songsToInsert.add(virtualSong)
            }
        }

        // 3. 将所有歌曲注入数据库并绑定到当前歌单
        if (songsToInsert.isNotEmpty()) {
            db.songDao.insertAll(songsToInsert)
            db.songDao.insertSongsToPlaylist(playlistId, songsToInsert.map { it.id })
        }

        musicRepo.refreshPlaylists()
        playlistId
    }
}
