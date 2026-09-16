package com.orbit.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.orbit.music.data.db.AppDatabase
import com.orbit.music.data.model.Song
import com.orbit.music.data.provider.AudioCoverProvider
import com.orbit.music.utils.AudioTagExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    suspend fun scanLocalMedia(
        includedFolders: Set<String> = emptySet(),
        excludedFolders: Set<String> = emptySet()
    ): List<Song> = withContext(Dispatchers.IO) {
        val songMap = mutableMapOf<String, Song>()

        // 1. 通过 MediaStore 进行系统级扫描 (放宽过滤条件)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE
        )

        // 过滤时长小于 3 秒或文件小于 20KB 的微短音效，支持所有音频
        val selection = "${MediaStore.Audio.Media.SIZE} >= 20480"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                val mimeCol = cursor.getColumnIndex(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    if (!file.exists() || !isAudioFile(path)) continue

                    val folderPath = file.parent ?: ""
                    // 文件夹过滤规则校验
                    if (isFolderExcluded(folderPath, excludedFolders)) continue
                    if (!isFolderIncluded(folderPath, includedFolders)) continue

                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val rawArtist = cursor.getString(artistCol)
                    val rawAlbum = cursor.getString(albumCol)

                    var title = if (!rawTitle.isNullOrBlank() && rawTitle != "<unknown>") rawTitle else file.nameWithoutExtension
                    var artist = if (!rawArtist.isNullOrBlank() && rawArtist != "<unknown>") rawArtist else "Unknown Artist"
                    var album = if (!rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>") rawAlbum else "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    var year = if (yearCol != -1) cursor.getInt(yearCol) else 0
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "audio/*" else "audio/*"

                    val parentFolderName = file.parentFile?.name ?: ""
                    val isAlbumFolderFallback = !rawAlbum.isNullOrBlank() && (
                        rawAlbum.equals(parentFolderName, ignoreCase = true) ||
                        rawAlbum.equals("Music", ignoreCase = true) ||
                        rawAlbum.equals("Download", ignoreCase = true) ||
                        rawAlbum.equals("Audio", ignoreCase = true) ||
                        rawAlbum.equals("netease", ignoreCase = true) ||
                        rawAlbum.equals("qqmusic", ignoreCase = true) ||
                        rawAlbum.equals("kuwo", ignoreCase = true) ||
                        rawAlbum.equals("kugou", ignoreCase = true) ||
                        rawAlbum == "<unknown>"
                    )

                    val shouldDeepExtract = artist == "Unknown Artist" || 
                                            album == "Unknown Album" || 
                                            title == file.nameWithoutExtension || 
                                            isAlbumFolderFallback

                    // 当系统 MediaStore 未能有效解析标签时，或专辑名称被系统退化为目录名时，使用原生 AudioTagExtractor 深度读取
                    if (shouldDeepExtract) {
                        val tags = AudioTagExtractor.extractMetadata(path)
                        if (!tags.title.isNullOrBlank()) title = tags.title
                        if (!tags.artist.isNullOrBlank()) artist = tags.artist
                        if (tags.year != null && tags.year > 0) year = tags.year

                        if (!tags.album.isNullOrBlank()) {
                            album = tags.album
                        } else if (isAlbumFolderFallback) {
                            // 经原生解析确认文件无有效专辑标签，纠正系统 MediaStore 自动填充的目录名称
                            album = "Unknown Album"
                        }
                    }

                    // 若仍缺少艺术家，尝试从文件名结构（如 "歌手 - 歌曲名"）智能解析
                    if (artist == "Unknown Artist" || artist.isBlank()) {
                        val parsed = parseArtistAndTitleFromFileName(file.nameWithoutExtension)
                        if (parsed != null) {
                            artist = parsed.first
                            if (title == file.nameWithoutExtension) {
                                title = parsed.second
                            }
                        }
                    }

                    // 使用单曲唯一绑定的专属封面 URI，彻底告别旧版按 albumId 共享引起的封面错乱与串台
                    val albumArtUri = AudioCoverProvider.buildSongCoverUri(id, path, album)

                    val song = Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = albumId,
                        durationMs = if (duration > 0) duration else 180000L,
                        path = path,
                        size = size,
                        albumArtUri = albumArtUri,
                        folderPath = folderPath,
                        year = year,
                        mimeType = mime
                    )

                    songMap[path] = song
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore query error", e)
        }

        // 2. 深度直接文件系统扫描（覆盖指定目录或 Music / Download / 常用目录）
        try {
            scanCommonDirectories(songMap, includedFolders, excludedFolders)
        } catch (e: Exception) {
            Log.e(TAG, "Direct directory scan error", e)
        }

        val resultList = songMap.values.toList().sortedBy { it.title.lowercase() }

        // 持久化存入原生 SQLite
        db.songDao.clearAll()
        if (resultList.isNotEmpty()) {
            db.songDao.insertAll(resultList)
            Log.i(TAG, "Successfully scanned and indexed ${resultList.size} audio tracks.")
        }

        // 返回包含统计数据 (isFavorite, playCount) 的完整曲库列表
        db.songDao.getAllSongs()
    }

    private fun isFolderExcluded(folderPath: String, excludedFolders: Set<String>): Boolean {
        if (excludedFolders.isEmpty()) return false
        val normalized = folderPath.trimEnd('/')
        return excludedFolders.any { excl ->
            val exNorm = excl.trimEnd('/')
            exNorm.isNotBlank() && (normalized == exNorm || normalized.startsWith("$exNorm/"))
        }
    }

    private fun isFolderIncluded(folderPath: String, includedFolders: Set<String>): Boolean {
        if (includedFolders.isEmpty()) return true
        val normalized = folderPath.trimEnd('/')
        return includedFolders.any { inc ->
            val incNorm = inc.trimEnd('/')
            incNorm.isNotBlank() && (normalized == incNorm || normalized.startsWith("$incNorm/"))
        }
    }

    private fun scanCommonDirectories(
        songMap: MutableMap<String, Song>,
        includedFolders: Set<String>,
        excludedFolders: Set<String>
    ) {
        val rootDirs = mutableListOf<File>()

        if (includedFolders.isNotEmpty()) {
            // 若用户指定了特定文件夹，则仅扫描指定的文件夹
            for (folderPath in includedFolders) {
                val dir = File(folderPath)
                if (dir.exists() && dir.isDirectory) {
                    rootDirs.add(dir)
                }
            }
        } else {
            // 未指定时默认全量扫描
            val externalStorage = Environment.getExternalStorageDirectory()
            if (externalStorage != null && externalStorage.exists()) {
                rootDirs.add(File(externalStorage, "Music"))
                rootDirs.add(File(externalStorage, "Download"))
                rootDirs.add(File(externalStorage, "netease/cloudmusic/Music"))
                rootDirs.add(File(externalStorage, "qqmusic/song"))
                rootDirs.add(File(externalStorage, "KuGou/Song"))
                rootDirs.add(externalStorage) // 全局扫描
            }
        }

        val retriever = MediaMetadataRetriever()
        for (dir in rootDirs) {
            if (dir.exists() && !isFolderExcluded(dir.absolutePath, excludedFolders)) {
                scanDirRecursive(dir, songMap, retriever, maxDepth = 4, excludedFolders = excludedFolders)
            }
        }
        try {
            retriever.release()
        } catch (e: Exception) {}
    }

    private fun scanDirRecursive(
        dir: File,
        songMap: MutableMap<String, Song>,
        retriever: MediaMetadataRetriever,
        maxDepth: Int,
        excludedFolders: Set<String>
    ) {
        if (maxDepth <= 0 || !dir.exists() || !dir.isDirectory) return
        if (isFolderExcluded(dir.absolutePath, excludedFolders)) return

        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    scanDirRecursive(f, songMap, retriever, maxDepth - 1, excludedFolders = excludedFolders)
                }
            } else if (f.isFile && isAudioFile(f.name) && f.length() > 50000) {
                val path = f.absolutePath
                if (!songMap.containsKey(path)) {
                    var title = f.nameWithoutExtension
                    var artist = "Unknown Artist"
                    var album = "Unknown Album"
                    var durationMs = 180000L

                    var year = 0
                    try {
                        retriever.setDataSource(path)
                        val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        val metaDur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                        if (!metaTitle.isNullOrBlank()) title = metaTitle
                        if (!metaArtist.isNullOrBlank()) artist = metaArtist
                        if (!metaAlbum.isNullOrBlank()) album = metaAlbum
                        if (!metaDur.isNullOrBlank()) durationMs = metaDur.toLongOrNull() ?: 180000L
                    } catch (e: Exception) {
                        // 忽略系统读取元数据的错误
                    }

                    val parentFolderName = f.parentFile?.name ?: ""
                    val isAlbumFolderFallback = !album.isNullOrBlank() && (
                        album.equals(parentFolderName, ignoreCase = true) ||
                        album.equals("Music", ignoreCase = true) ||
                        album.equals("Download", ignoreCase = true) ||
                        album.equals("Audio", ignoreCase = true) ||
                        album.equals("netease", ignoreCase = true) ||
                        album.equals("qqmusic", ignoreCase = true) ||
                        album.equals("kuwo", ignoreCase = true) ||
                        album.equals("kugou", ignoreCase = true) ||
                        album == "<unknown>"
                    )

                    val shouldDeepExtract = artist == "Unknown Artist" || 
                                            album == "Unknown Album" || 
                                            title == f.nameWithoutExtension || 
                                            isAlbumFolderFallback

                    // 使用原生 AudioTagExtractor 进行深度元数据补齐（尤其是 APE/FLAC/WAV/无损格式）
                    if (shouldDeepExtract) {
                        val tags = AudioTagExtractor.extractMetadata(path)
                        if (!tags.title.isNullOrBlank()) title = tags.title
                        if (!tags.artist.isNullOrBlank()) artist = tags.artist
                        if (tags.year != null && tags.year > 0) year = tags.year

                        if (!tags.album.isNullOrBlank()) {
                            album = tags.album
                        } else if (isAlbumFolderFallback) {
                            album = "Unknown Album"
                        }
                    }

                    // 若仍为未知，尝试文件名智能拆解 (如 "周杰伦 - 晴天")
                    if (artist == "Unknown Artist" || artist.isBlank()) {
                        val parsed = parseArtistAndTitleFromFileName(f.nameWithoutExtension)
                        if (parsed != null) {
                            artist = parsed.first
                            if (title == f.nameWithoutExtension) {
                                title = parsed.second
                            }
                        }
                    }

                    val songId = path.hashCode().toLong()
                    val song = Song(
                        id = songId,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = 0L,
                        durationMs = durationMs,
                        path = path,
                        size = f.length(),
                        albumArtUri = AudioCoverProvider.buildSongCoverUri(songId, path, album),
                        folderPath = f.parent ?: "",
                        year = year,
                        mimeType = "audio/*"
                    )
                    songMap[path] = song
                }
            }
        }
    }

    /**
     * 从文件名智能拆解艺术家与歌曲名（兼容 "周杰伦 - 晴天"、"01. Eminem - Stan" 等规范）
     */
    private fun parseArtistAndTitleFromFileName(nameWithoutExt: String): Pair<String, String>? {
        // 去除开头可能存在的音轨号如 "01. "、"1 - "、"01 " 等
        val clean = nameWithoutExt.replaceFirst(Regex("""^\d{1,3}[\s\.\-_]+"""), "").trim()
        if (clean.contains(" - ")) {
            val parts = clean.split(" - ")
            if (parts.size >= 2) {
                val artistPart = parts[0].trim()
                val titlePart = parts.drop(1).joinToString(" - ").trim()
                if (artistPart.isNotBlank() && titlePart.isNotBlank()) {
                    return Pair(artistPart, titlePart)
                }
            }
        }
        return null
    }

    private fun isAudioFile(path: String): Boolean {
        val lower = path.lowercase()
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
        private const val TAG = "MediaStoreScanner"
    }
}
