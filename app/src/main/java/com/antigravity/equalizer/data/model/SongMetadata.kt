package com.antigravity.equalizer.data.model

import android.media.MediaMetadataRetriever
import android.util.Log
import java.io.File

/**
 * 完整歌曲元数据标签模型 (Poweramp 风格编辑项)
 */
data class SongMetadata(
    val title: String = "",
    val track: String = "",
    val year: String = "",
    val genre: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val composer: String = "",
    val comment: String = ""
)

/**
 * 音频技术规格参数模型 (弹窗顶部信息)
 */
data class AudioTechSpecs(
    val format: String = "AUDIO",
    val durationSec: Long = 0L,
    val durationFormatted: String = "0:00",
    val sampleRateHz: Int = 44100,
    val bitDepth: Int = 16,
    val channelsText: String = "立体声",
    val bitrateKbps: Int = 0,
    val isGapless: Boolean = true,
    val sizeKb: Long = 0L
) {
    /**
     * 生成如截图所示的技术参数文本：
     * "FLAC, 255 秒 (4:15), 44100 HZ, 16 BIT, 立体声, 892 KBPS, GAPLESS, 27771KB"
     */
    val specsSummary: String
        get() {
            val parts = mutableListOf<String>()
            parts.add(format.uppercase())
            parts.add("$durationSec 秒 ($durationFormatted)")
            if (sampleRateHz > 0) parts.add("$sampleRateHz HZ")
            if (bitDepth > 0) parts.add("$bitDepth BIT")
            parts.add(channelsText)
            if (bitrateKbps > 0) parts.add("$bitrateKbps KBPS")
            if (isGapless) parts.add("GAPLESS")
            if (sizeKb > 0) parts.add("${sizeKb}KB")
            return parts.joinToString(", ")
        }
}

/**
 * 音频元数据与技术参数提取工具
 */
object SongMetadataHelper {

    private const val TAG = "SongMetadataHelper"

    /**
     * 读取音频文件的技术规格参数
     */
    fun extractTechSpecs(song: Song): AudioTechSpecs {
        val file = File(song.path)
        val ext = file.extension.uppercase().ifBlank {
            when {
                song.mimeType.contains("flac", true) -> "FLAC"
                song.mimeType.contains("mp3", true) -> "MP3"
                song.mimeType.contains("wav", true) -> "WAV"
                song.mimeType.contains("ogg", true) -> "OGG"
                song.mimeType.contains("aac", true) || song.mimeType.contains("m4a", true) -> "AAC"
                else -> "AUDIO"
            }
        }

        val totalSec = song.durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        val durFormatted = "%d:%02d".format(min, sec)
        val sizeKb = (if (song.size > 0) song.size else file.length()) / 1024

        var sampleRate = 44100
        var bitDepth = if (ext == "FLAC" || ext == "WAV") 16 else 16
        var bitrateKbps = if (totalSec > 0 && sizeKb > 0) ((sizeKb * 8) / totalSec).toInt() else 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(song.path)
            val srStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
            val brStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)

            srStr?.toIntOrNull()?.let { if (it > 0) sampleRate = it }
            brStr?.toIntOrNull()?.let { if (it > 0) bitrateKbps = it / 1000 }
            if (bitrateKbps > 1500) {
                bitDepth = 24
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read tech specs for ${song.path}", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }

        return AudioTechSpecs(
            format = ext,
            durationSec = totalSec,
            durationFormatted = durFormatted,
            sampleRateHz = sampleRate,
            bitDepth = bitDepth,
            channelsText = "立体声",
            bitrateKbps = bitrateKbps,
            isGapless = true,
            sizeKb = sizeKb
        )
    }

    /**
     * 读取歌曲元数据标签，优先合并数据库已存标签，缺失项从文件 ID3 提取
     */
    fun extractInitialMetadata(song: Song, dbMetadata: SongMetadata?): SongMetadata {
        var track = dbMetadata?.track.orEmpty()
        var year = dbMetadata?.year.orEmpty()
        var genre = dbMetadata?.genre.orEmpty()
        var albumArtist = dbMetadata?.albumArtist.orEmpty()
        var composer = dbMetadata?.composer.orEmpty()
        var comment = dbMetadata?.comment.orEmpty()

        var title = dbMetadata?.title?.ifBlank { null } ?: song.title
        var artist = dbMetadata?.artist?.ifBlank { null } ?: song.artist
        var album = dbMetadata?.album?.ifBlank { null } ?: song.album

        if (year.isBlank() && song.year > 0) {
            year = song.year.toString()
        }

        // 若部分扩展属性在数据库尚未记录，从音频文件头部提取
        if (track.isBlank() || genre.isBlank() || composer.isBlank() || albumArtist.isBlank()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(song.path)
                if (track.isBlank()) {
                    val rawTrack = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                    if (!rawTrack.isNullOrBlank()) track = rawTrack.split("/").first().trim()
                }
                if (year.isBlank()) {
                    val rawDate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    if (!rawDate.isNullOrBlank()) year = rawDate.take(4)
                }
                if (genre.isBlank()) {
                    val rawGenre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                    if (!rawGenre.isNullOrBlank()) genre = rawGenre
                }
                if (albumArtist.isBlank()) {
                    val rawAlbumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                    if (!rawAlbumArtist.isNullOrBlank()) albumArtist = rawAlbumArtist
                }
                if (composer.isBlank()) {
                    val rawComposer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                        ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER)
                    if (!rawComposer.isNullOrBlank()) composer = rawComposer
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract metadata tags for ${song.path}", e)
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {}
            }
        }

        return SongMetadata(
            title = title,
            track = track,
            year = year,
            genre = genre,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            composer = composer,
            comment = comment
        )
    }

    /**
     * 获取友好路径展示，例如 PRIMARY/音乐/M1/BEYOND - 谁伴我闯荡.FLAC
     */
    fun formatDisplayPath(rawPath: String): String {
        if (rawPath.isBlank()) return "PRIMARY/Music"
        val normalized = rawPath.replace("\\", "/")
        return if (normalized.contains("/storage/emulated/0/")) {
            "PRIMARY/" + normalized.substringAfter("/storage/emulated/0/")
        } else if (normalized.startsWith("/")) {
            "PRIMARY" + normalized
        } else {
            normalized
        }
    }
}
