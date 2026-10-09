package com.orbit.music.car

import android.net.Uri
import com.ecarx.eas.sdk.mediacenter.MediaInfo
import com.ecarx.eas.sdk.mediacenter.SourceType
import com.orbit.music.data.model.Song

/**
 * 车机媒体中心歌曲条目封装 (适配吉利/领克/银河/极氪 ECARX 架构)
 */
class OrbitCarMediaInfo(
    private val song: Song,
    private val queueIndex: Int = 0,
    private val lyricText: String? = null
) : MediaInfo() {

    override fun getTitle(): String = song.title

    override fun getArtist(): String = song.artist

    override fun getAlbum(): String = song.album

    override fun getDuration(): Long = song.durationMs

    override fun getMediaId(): String = song.id.toString()

    override fun getUuid(): String = "${song.title}|${song.artist}"

    override fun getArtwork(): Uri? {
        return song.albumArtUri?.let { Uri.parse(it) }
            ?: Uri.parse("content://com.orbit.music.cover/${song.id}")
    }

    override fun getSourceType(): Int = SourceType.SOURCE_TYPE_ONLINE

    override fun getPlayingItemPositionInQueue(): Int = queueIndex

    override fun getLyricContent(): String? = lyricText

    override fun getMediaPath(): Uri = Uri.parse(song.path)
}
