package com.antigravity.equalizer.data.scanner

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.antigravity.equalizer.data.db.AppDatabase
import com.antigravity.equalizer.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreScanner(private val context: Context) {

    private val db = AppDatabase.getInstance(context)

    suspend fun scanLocalMedia(): List<Song> = withContext(Dispatchers.IO) {
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

                    val id = cursor.getLong(idCol)
                    val rawTitle = cursor.getString(titleCol)
                    val title = if (!rawTitle.isNullOrBlank() && rawTitle != "<unknown>") rawTitle else file.nameWithoutExtension
                    val rawArtist = cursor.getString(artistCol)
                    val artist = if (!rawArtist.isNullOrBlank() && rawArtist != "<unknown>") rawArtist else "Unknown Artist"
                    val rawAlbum = cursor.getString(albumCol)
                    val album = if (!rawAlbum.isNullOrBlank() && rawAlbum != "<unknown>") rawAlbum else "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val year = if (yearCol != -1) cursor.getInt(yearCol) else 0
                    val mime = if (mimeCol != -1) cursor.getString(mimeCol) ?: "audio/*" else "audio/*"

                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val folderPath = file.parent ?: ""

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

        // 2. 深度直接文件系统扫描（覆盖 Music / Download / 常用目录）
        try {
            scanCommonDirectories(songMap)
        } catch (e: Exception) {
            Log.e(TAG, "Direct directory scan error", e)
        }

        val resultList = songMap.values.toList().sortedBy { it.title.lowercase() }

        // 持久化存入原生 SQLite
        if (resultList.isNotEmpty()) {
            db.songDao.clearAll()
            db.songDao.insertAll(resultList)
            Log.i(TAG, "Successfully scanned and indexed ${resultList.size} audio tracks.")
        }

        resultList
    }

    private fun scanCommonDirectories(songMap: MutableMap<String, Song>) {
        val rootDirs = mutableListOf<File>()
        val externalStorage = Environment.getExternalStorageDirectory()
        if (externalStorage != null && externalStorage.exists()) {
            rootDirs.add(File(externalStorage, "Music"))
            rootDirs.add(File(externalStorage, "Download"))
            rootDirs.add(File(externalStorage, "netease/cloudmusic/Music"))
            rootDirs.add(File(externalStorage, "qqmusic/song"))
            rootDirs.add(File(externalStorage, "KuGou/Song"))
            rootDirs.add(externalStorage) // 全局扫描
        }

        val retriever = MediaMetadataRetriever()
        for (dir in rootDirs) {
            if (dir.exists()) {
                scanDirRecursive(dir, songMap, retriever, maxDepth = 4)
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
        maxDepth: Int
    ) {
        if (maxDepth <= 0 || !dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles() ?: return

        for (f in files) {
            if (f.isDirectory) {
                if (!f.name.startsWith(".")) {
                    scanDirRecursive(f, songMap, retriever, maxDepth - 1)
                }
            } else if (f.isFile && isAudioFile(f.name) && f.length() > 50000) {
                val path = f.absolutePath
                if (!songMap.containsKey(path)) {
                    var title = f.nameWithoutExtension
                    var artist = "Unknown Artist"
                    var album = "Unknown Album"
                    var durationMs = 180000L

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
                        // 忽略无法读取元数据的错误，使用文件名
                    }

                    val song = Song(
                        id = path.hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = 0L,
                        durationMs = durationMs,
                        path = path,
                        size = f.length(),
                        albumArtUri = null,
                        folderPath = f.parent ?: "",
                        year = 0,
                        mimeType = "audio/*"
                    )
                    songMap[path] = song
                }
            }
        }
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
