package com.antigravity.equalizer.data.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.antigravity.equalizer.utils.CoverHelper
import java.io.FileNotFoundException

/**
 * 音乐专属封面内容提供者 (ContentProvider)
 *
 * 彻底废除 Android MediaStore 旧版共享 albumart URI，
 * 专门对外及应用内部提供针对单曲 ID 与音频文件真实路径绑定的高保真封面流。
 */
class AudioCoverProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val match = uriMatcher.match(uri)
        if (match != MATCH_SONG_COVER) {
            throw FileNotFoundException("Unknown URI: $uri")
        }

        val ctx = context ?: throw FileNotFoundException("Context is null")
        val songId = uri.lastPathSegment?.toLongOrNull() ?: throw FileNotFoundException("Invalid song ID in $uri")
        val path = uri.getQueryParameter("path")
        val album = uri.getQueryParameter("album")

        val coverFile = CoverHelper.getOrExtractCoverFile(ctx, songId, path, album)
            ?: throw FileNotFoundException("No cover for song $songId ($path)")

        return ParcelFileDescriptor.open(coverFile, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.antigravity.equalizer.cover"
        private const val MATCH_SONG_COVER = 1

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "song/*", MATCH_SONG_COVER)
        }

        /**
         * 构造单曲唯一绑定的专属封面 URI
         */
        fun buildSongCoverUri(songId: Long, path: String, album: String? = null): String {
            return Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath("song")
                .appendPath(songId.toString())
                .appendQueryParameter("path", path)
                .apply {
                    if (!album.isNullOrBlank()) {
                        appendQueryParameter("album", album)
                    }
                }
                .build()
                .toString()
        }
    }
}
