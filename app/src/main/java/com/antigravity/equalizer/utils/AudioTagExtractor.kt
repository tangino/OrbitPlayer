package com.antigravity.equalizer.utils

import android.util.Base64
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

/**
 * 原生全能音频元数据与内嵌封面解析引擎
 * 完全独立于 Android 系统 MediaMetadataRetriever 与 MediaStore，
 * 直接解析音频二进制头与标签块，兼容 ID3v1/v2、FLAC、M4A/MP4、APEv2 及大尺寸内嵌封面。
 */
object AudioTagExtractor {

    private const val TAG = "AudioTagExtractor"

    data class TagResult(
        val title: String? = null,
        val artist: String? = null,
        val album: String? = null,
        val year: Int? = null,
        val genre: String? = null,
        val trackNumber: Int? = null
    )

    /**
     * 提取音频文件内嵌封面原始字节数据（支持 ID3v2 APIC、FLAC PICTURE block、MP4 covr、APEv2 Cover Art）
     */
    fun extractEmbeddedPicture(filePath: String): ByteArray? {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() < 128) return null

        val ext = file.extension.lowercase()
        try {
            when (ext) {
                "flac" -> {
                    val pic = extractFlacPicture(file)
                    if (pic != null) return pic
                }
                "m4a", "aac", "mp4" -> {
                    val pic = extractMp4Cover(file)
                    if (pic != null) return pic
                }
                "ape" -> {
                    val pic = extractApeCover(file)
                    if (pic != null) return pic
                }
                else -> {
                    // 默认先尝试 ID3v2（MP3、WAV、以及内嵌 ID3 的各种格式）
                    val pic = extractId3v2Picture(file)
                    if (pic != null) return pic
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract picture from $filePath: ${e.message}")
        }
        return null
    }

    /**
     * 读取音频文件的标签元数据（标题、艺术家、专辑、年份、流派、曲目号）
     */
    fun extractMetadata(filePath: String): TagResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile || file.length() < 128) return TagResult()

        val ext = file.extension.lowercase()
        return try {
            when (ext) {
                "flac" -> extractFlacMetadata(file)
                "m4a", "aac", "mp4" -> extractMp4Metadata(file)
                "ape" -> extractApeMetadata(file)
                else -> extractId3Metadata(file)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to extract metadata from $filePath: ${e.message}")
            TagResult()
        }
    }

    // =========================================================================
    // ID3v2 / ID3v1 标签解析
    // =========================================================================

    private fun extractId3v2Picture(file: File): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return null
            }
            val majorVersion = header[3].toInt() and 0xFF // 2, 3, or 4
            val tagSize = readSynchSafeInt(header, 6)
            val tagEnd = 10L + tagSize

            while (raf.filePointer < tagEnd - 10) {
                val frameId: String
                val frameSize: Int
                if (majorVersion == 2) {
                    val fHeader = ByteArray(6)
                    if (raf.read(fHeader) < 6) break
                    frameId = String(fHeader, 0, 3, Charsets.ISO_8859_1)
                    frameSize = ((fHeader[3].toInt() and 0xFF) shl 16) or
                            ((fHeader[4].toInt() and 0xFF) shl 8) or
                            (fHeader[5].toInt() and 0xFF)
                } else {
                    val fHeader = ByteArray(10)
                    if (raf.read(fHeader) < 10) break
                    frameId = String(fHeader, 0, 4, Charsets.ISO_8859_1)
                    if (frameId.isBlank() || frameId[0] == '\u0000') break
                    frameSize = if (majorVersion == 4) {
                        readSynchSafeInt(fHeader, 4)
                    } else {
                        readInt32BE(fHeader, 4)
                    }
                }

                if (frameSize <= 0 || raf.filePointer + frameSize > file.length()) {
                    break
                }

                if (frameId == "APIC" || (majorVersion == 2 && frameId == "PIC")) {
                    val frameData = ByteArray(frameSize)
                    raf.readFully(frameData)
                    return parseApicData(frameData, majorVersion)
                } else {
                    raf.skipBytes(frameSize)
                }
            }
        }
        return null
    }

    private fun parseApicData(data: ByteArray, majorVersion: Int): ByteArray? {
        if (data.size < 10) return null
        val encoding = data[0].toInt() and 0xFF
        var offset = 1

        if (majorVersion == 2) {
            offset += 3 // 3 byte image format (e.g. JPG, PNG)
            offset += 1 // picture type
            // skip description
            offset = skipString(data, offset, encoding)
        } else {
            // MIME type (ISO-8859-1 null terminated)
            while (offset < data.size && data[offset] != 0.toByte()) {
                offset++
            }
            offset++ // skip 0
            if (offset >= data.size) return null
            offset++ // skip picture type (1 byte)
            // skip description
            offset = skipString(data, offset, encoding)
        }

        if (offset < data.size) {
            val imgLen = data.size - offset
            val imgData = ByteArray(imgLen)
            System.arraycopy(data, offset, imgData, 0, imgLen)
            return imgData
        }
        return null
    }

    private fun extractId3Metadata(file: File): TagResult {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year: Int? = null
        var genre: String? = null
        var track: Int? = null

        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                val majorVersion = header[3].toInt() and 0xFF
                val tagSize = readSynchSafeInt(header, 6)
                val tagEnd = 10L + tagSize

                while (raf.filePointer < tagEnd - 10) {
                    val frameId: String
                    val frameSize: Int
                    if (majorVersion == 2) {
                        val fHeader = ByteArray(6)
                        if (raf.read(fHeader) < 6) break
                        frameId = String(fHeader, 0, 3, Charsets.ISO_8859_1)
                        frameSize = ((fHeader[3].toInt() and 0xFF) shl 16) or
                                ((fHeader[4].toInt() and 0xFF) shl 8) or
                                (fHeader[5].toInt() and 0xFF)
                    } else {
                        val fHeader = ByteArray(10)
                        if (raf.read(fHeader) < 10) break
                        frameId = String(fHeader, 0, 4, Charsets.ISO_8859_1)
                        if (frameId.isBlank() || frameId[0] == '\u0000') break
                        frameSize = if (majorVersion == 4) {
                            readSynchSafeInt(fHeader, 4)
                        } else {
                            readInt32BE(fHeader, 4)
                        }
                    }

                    if (frameSize <= 0 || raf.filePointer + frameSize > file.length()) {
                        break
                    }

                    when (frameId) {
                        "TIT2", "TT2" -> {
                            val data = ByteArray(frameSize)
                            raf.readFully(data)
                            title = decodeId3Text(data)
                        }
                        "TPE1", "TP1" -> {
                            val data = ByteArray(frameSize)
                            raf.readFully(data)
                            artist = decodeId3Text(data)
                        }
                        "TALB", "TAL" -> {
                            val data = ByteArray(frameSize)
                            raf.readFully(data)
                            album = decodeId3Text(data)
                        }
                        "TYER", "TDRC", "TYE" -> {
                            val data = ByteArray(frameSize)
                            raf.readFully(data)
                            val yStr = decodeId3Text(data)
                            year = yStr.take(4).toIntOrNull()
                        }
                        "TCON", "TCO" -> {
                            val data = ByteArray(frameSize)
                            raf.readFully(data)
                            genre = decodeId3Text(data)
                        }
                        "TRCK", "TRK" -> {
                            val data = ByteArray(frameSize)
                            raf.readFully(data)
                            val tStr = decodeId3Text(data)
                            track = tStr.split("/").firstOrNull()?.trim()?.toIntOrNull()
                        }
                        else -> {
                            raf.skipBytes(frameSize)
                        }
                    }
                }
            } else if (file.length() > 128) {
                // 回退尝试 ID3v1 (末尾 128 字节)
                raf.seek(file.length() - 128)
                val buf = ByteArray(128)
                raf.readFully(buf)
                if (buf[0] == 'T'.code.toByte() && buf[1] == 'A'.code.toByte() && buf[2] == 'G'.code.toByte()) {
                    title = decodeSmartString(buf, 3, 30).trim()
                    artist = decodeSmartString(buf, 33, 30).trim()
                    album = decodeSmartString(buf, 63, 30).trim()
                    year = decodeSmartString(buf, 93, 4).trim().toIntOrNull()
                }
            }
        }

        return TagResult(title, artist, album, year, genre, track)
    }

    private fun decodeId3Text(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val encoding = data[0].toInt() and 0xFF
        val textBytes = data.copyOfRange(1, data.size)
        return when (encoding) {
            0 -> decodeSmartString(textBytes, 0, textBytes.size).trim('\u0000', ' ')
            1 -> String(textBytes, Charsets.UTF_16).trim('\u0000', ' ')
            2 -> String(textBytes, Charsets.UTF_16BE).trim('\u0000', ' ')
            3 -> String(textBytes, Charsets.UTF_8).trim('\u0000', ' ')
            else -> decodeSmartString(textBytes, 0, textBytes.size).trim('\u0000', ' ')
        }
    }

    // =========================================================================
    // FLAC 标签与图片块解析
    // =========================================================================

    private fun extractFlacPicture(file: File): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != "fLaC") return null

            while (raf.filePointer < file.length() - 4) {
                val header = ByteArray(4)
                if (raf.read(header) < 4) break
                val isLast = (header[0].toInt() and 0x80) != 0
                val blockType = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)

                if (blockType == 6) { // PICTURE
                    // Read picture block
                    raf.skipBytes(4) // skip picture type
                    val mimeLen = raf.readInt()
                    if (mimeLen in 0..1024) {
                        raf.skipBytes(mimeLen)
                        val descLen = raf.readInt()
                        if (descLen in 0..4096) {
                            raf.skipBytes(descLen)
                            raf.skipBytes(16) // width, height, depth, colors
                            val dataLen = raf.readInt()
                            if (dataLen in 1..20_000_000) { // 最大支持 20MB 封面图
                                val imgData = ByteArray(dataLen)
                                raf.readFully(imgData)
                                return imgData
                            }
                        }
                    }
                    return null
                } else if (blockType == 4) { // VORBIS_COMMENT - 检查是否有 Base64 的 METADATA_BLOCK_PICTURE
                    val commentData = ByteArray(length)
                    raf.readFully(commentData)
                    val base64Pic = extractPictureFromVorbisComment(commentData)
                    if (base64Pic != null) return base64Pic
                } else {
                    raf.skipBytes(length)
                }

                if (isLast) break
            }
        }
        return null
    }

    private fun extractFlacMetadata(file: File): TagResult {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year: Int? = null
        var genre: String? = null
        var track: Int? = null

        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            raf.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != "fLaC") return TagResult()

            while (raf.filePointer < file.length() - 4) {
                val header = ByteArray(4)
                if (raf.read(header) < 4) break
                val isLast = (header[0].toInt() and 0x80) != 0
                val blockType = header[0].toInt() and 0x7F
                val length = ((header[1].toInt() and 0xFF) shl 16) or
                        ((header[2].toInt() and 0xFF) shl 8) or
                        (header[3].toInt() and 0xFF)

                if (blockType == 4) { // VORBIS_COMMENT
                    val block = ByteArray(length)
                    raf.readFully(block)
                    val comments = parseVorbisComments(block)
                    title = comments["TITLE"] ?: comments["TIT2"]
                    artist = comments["ARTIST"] ?: comments["ALBUMARTIST"] ?: comments["TPE1"]
                    album = comments["ALBUM"] ?: comments["TALB"]
                    year = (comments["DATE"] ?: comments["YEAR"] ?: comments["TYER"])?.take(4)?.toIntOrNull()
                    genre = comments["GENRE"]
                    track = comments["TRACKNUMBER"]?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()
                    break
                } else {
                    raf.skipBytes(length)
                }
                if (isLast) break
            }
        }

        return TagResult(title, artist, album, year, genre, track)
    }

    private fun parseVorbisComments(data: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        var offset = 0
        if (data.size < 8) return map

        val vendorLen = readInt32LE(data, offset)
        offset += 4 + vendorLen
        if (offset + 4 > data.size) return map

        val userCommentListLen = readInt32LE(data, offset)
        offset += 4

        for (i in 0 until userCommentListLen) {
            if (offset + 4 > data.size) break
            val commentLen = readInt32LE(data, offset)
            offset += 4
            if (offset + commentLen > data.size || commentLen <= 0) break

            val commentStr = String(data, offset, commentLen, Charsets.UTF_8)
            offset += commentLen

            val eqIdx = commentStr.indexOf('=')
            if (eqIdx > 0) {
                val key = commentStr.substring(0, eqIdx).uppercase()
                val value = commentStr.substring(eqIdx + 1)
                map[key] = value
            }
        }
        return map
    }

    private fun extractPictureFromVorbisComment(data: ByteArray): ByteArray? {
        val comments = parseVorbisComments(data)
        val base64 = comments["METADATA_BLOCK_PICTURE"] ?: return null
        return try {
            val raw = Base64.decode(base64, Base64.DEFAULT)
            // Parse FLAC Picture structure from decoded bytes
            if (raw.size > 32) {
                var offset = 0
                offset += 4 // skip picture type
                val mimeLen = readInt32BE(raw, offset); offset += 4
                offset += mimeLen
                val descLen = readInt32BE(raw, offset); offset += 4
                offset += descLen
                offset += 16 // width, height, depth, colors
                val dataLen = readInt32BE(raw, offset); offset += 4
                if (offset + dataLen <= raw.size) {
                    val img = ByteArray(dataLen)
                    System.arraycopy(raw, offset, img, 0, dataLen)
                    return img
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // MP4 / M4A 标签与封面解析
    // =========================================================================

    private fun extractMp4Cover(file: File): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            val ilstOffset = findAtomPath(raf, listOf("moov", "udta", "meta", "ilst")) ?: return null
            raf.seek(ilstOffset.start)
            val ilstEnd = ilstOffset.end

            while (raf.filePointer < ilstEnd - 8) {
                val size = raf.readInt()
                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val type = String(typeBytes, Charsets.ISO_8859_1)

                if (type == "covr") {
                    // covr 里面是一个 data atom
                    val dataSize = raf.readInt()
                    val dataType = ByteArray(4)
                    raf.readFully(dataType)
                    if (String(dataType, Charsets.ISO_8859_1) == "data") {
                        raf.skipBytes(8) // version(1), flags(3), reserved(4)
                        val imgLen = dataSize - 16
                        if (imgLen in 1..20_000_000) {
                            val img = ByteArray(imgLen)
                            raf.readFully(img)
                            return img
                        }
                    }
                    return null
                } else {
                    if (size > 8) raf.skipBytes(size - 8) else break
                }
            }
        }
        return null
    }

    private fun extractMp4Metadata(file: File): TagResult {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year: Int? = null
        var genre: String? = null
        var track: Int? = null

        RandomAccessFile(file, "r").use { raf ->
            val ilstOffset = findAtomPath(raf, listOf("moov", "udta", "meta", "ilst")) ?: return TagResult()
            raf.seek(ilstOffset.start)
            val ilstEnd = ilstOffset.end

            while (raf.filePointer < ilstEnd - 8) {
                val size = raf.readInt()
                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val type = String(typeBytes, Charsets.ISO_8859_1)

                if (size < 8) break
                val atomEnd = raf.filePointer + (size - 8)

                when (type) {
                    "©nam" -> title = readMp4DataString(raf, size - 8)
                    "©ART" -> artist = readMp4DataString(raf, size - 8)
                    "©alb" -> album = readMp4DataString(raf, size - 8)
                    "©day" -> year = readMp4DataString(raf, size - 8)?.take(4)?.toIntOrNull()
                    "©gen" -> genre = readMp4DataString(raf, size - 8)
                    "trkn" -> {
                        // track number
                        val dSize = raf.readInt()
                        val dType = ByteArray(4)
                        raf.readFully(dType)
                        if (String(dType, Charsets.ISO_8859_1) == "data") {
                            raf.skipBytes(8)
                            if (dSize >= 20) {
                                raf.skipBytes(2)
                                track = raf.readShort().toInt() and 0xFFFF
                            }
                        }
                    }
                    else -> raf.seek(atomEnd)
                }
                raf.seek(atomEnd)
            }
        }

        return TagResult(title, artist, album, year, genre, track)
    }

    private data class AtomRange(val start: Long, val end: Long)

    private fun findAtomPath(raf: RandomAccessFile, path: List<String>): AtomRange? {
        var curStart = 0L
        var curEnd = raf.length()

        for (target in path) {
            raf.seek(curStart)
            if (target == "meta") {
                // QuickTime meta atom 之后常常紧跟 4 字节的 version/flags
                val size = raf.readInt()
                val type = ByteArray(4)
                raf.readFully(type)
                if (String(type, Charsets.ISO_8859_1) == "meta") {
                    raf.skipBytes(4) // skip version & flags
                    curStart = raf.filePointer
                    curEnd = curStart + size - 12
                    continue
                } else {
                    raf.seek(curStart)
                }
            }

            var found = false
            while (raf.filePointer < curEnd - 8) {
                val size = raf.readInt()
                val typeBytes = ByteArray(4)
                raf.readFully(typeBytes)
                val type = String(typeBytes, Charsets.ISO_8859_1)

                val actualSize = if (size == 1) raf.readLong() else size.toLong()
                val headerSize = if (size == 1) 16 else 8
                val dataEnd = raf.filePointer + (actualSize - headerSize)

                if (type == target) {
                    curStart = raf.filePointer
                    curEnd = dataEnd
                    found = true
                    break
                } else {
                    raf.seek(dataEnd)
                }
            }
            if (!found) return null
        }
        return AtomRange(curStart, curEnd)
    }

    private fun readMp4DataString(raf: RandomAccessFile, atomLength: Int): String? {
        if (atomLength < 16) return null
        val dataSize = raf.readInt()
        val dataType = ByteArray(4)
        raf.readFully(dataType)
        if (String(dataType, Charsets.ISO_8859_1) != "data") return null

        raf.skipBytes(8) // version, flags, reserved
        val strLen = dataSize - 16
        if (strLen <= 0) return null
        val strBytes = ByteArray(strLen)
        raf.readFully(strBytes)
        return String(strBytes, Charsets.UTF_8).trim('\u0000', ' ')
    }

    // =========================================================================
    // APEv2 标签与封面解析
    // =========================================================================

    private fun extractApeCover(file: File): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            if (file.length() < 160) return null
            // 扫描尾部 160 字节寻找 APETAGEX
            raf.seek(file.length() - 160)
            val buf = ByteArray(160)
            raf.readFully(buf)
            val apeIdx = indexOfBytes(buf, "APETAGEX".toByteArray(Charsets.US_ASCII))
            if (apeIdx >= 0) {
                val tagSize = readInt32LE(buf, apeIdx + 12)
                val tagStart = file.length() - 160 + apeIdx - tagSize + 32
                if (tagStart in 0 until file.length()) {
                    raf.seek(tagStart)
                    val tagData = ByteArray(tagSize)
                    raf.readFully(tagData)
                    return parseApeCoverData(tagData)
                }
            }
        }
        return null
    }

    private fun parseApeCoverData(data: ByteArray): ByteArray? {
        var offset = 32 // skip header if present
        if (data.size < 40) return null
        val itemCount = readInt32LE(data, 16)

        for (i in 0 until itemCount) {
            if (offset + 8 > data.size) break
            val valLen = readInt32LE(data, offset); offset += 4
            offset += 4 // skip flags
            // Key is null-terminated string
            val keyStart = offset
            while (offset < data.size && data[offset] != 0.toByte()) {
                offset++
            }
            val key = String(data, keyStart, offset - keyStart, Charsets.US_ASCII)
            offset++ // skip 0

            if (key.equals("Cover Art (Front)", ignoreCase = true) || key.equals("Cover Art", ignoreCase = true)) {
                // APE cover data starts with description\0 followed by binary image
                var imgOffset = offset
                while (imgOffset < offset + valLen && data[imgOffset] != 0.toByte()) {
                    imgOffset++
                }
                imgOffset++ // skip 0
                val imgLen = (offset + valLen) - imgOffset
                if (imgLen > 0 && imgOffset + imgLen <= data.size) {
                    val img = ByteArray(imgLen)
                    System.arraycopy(data, imgOffset, img, 0, imgLen)
                    return img
                }
            } else {
                offset += valLen
            }
        }
        return null
    }

    private fun extractApeMetadata(file: File): TagResult {
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var year: Int? = null
        var genre: String? = null
        var track: Int? = null

        RandomAccessFile(file, "r").use { raf ->
            if (file.length() < 160) return TagResult()
            raf.seek(file.length() - 160)
            val buf = ByteArray(160)
            raf.readFully(buf)
            val apeIdx = indexOfBytes(buf, "APETAGEX".toByteArray(Charsets.US_ASCII))
            if (apeIdx >= 0) {
                val tagSize = readInt32LE(buf, apeIdx + 12)
                val tagStart = file.length() - 160 + apeIdx - tagSize + 32
                if (tagStart in 0 until file.length()) {
                    raf.seek(tagStart)
                    val tagData = ByteArray(tagSize)
                    raf.readFully(tagData)

                    var offset = 32
                    val itemCount = readInt32LE(tagData, 16)
                    for (i in 0 until itemCount) {
                        if (offset + 8 > tagData.size) break
                        val valLen = readInt32LE(tagData, offset); offset += 4
                        offset += 4 // skip flags
                        val keyStart = offset
                        while (offset < tagData.size && tagData[offset] != 0.toByte()) {
                            offset++
                        }
                        val key = String(tagData, keyStart, offset - keyStart, Charsets.US_ASCII).uppercase()
                        offset++ // skip 0

                        if (offset + valLen <= tagData.size) {
                            val value = String(tagData, offset, valLen, Charsets.UTF_8).trim()
                            when (key) {
                                "TITLE" -> title = value
                                "ARTIST" -> artist = value
                                "ALBUM" -> album = value
                                "YEAR" -> year = value.take(4).toIntOrNull()
                                "GENRE" -> genre = value
                                "TRACK" -> track = value.split("/").firstOrNull()?.trim()?.toIntOrNull()
                            }
                            offset += valLen
                        }
                    }
                }
            }
        }

        return TagResult(title, artist, album, year, genre, track)
    }

    // =========================================================================
    // 智能字符集识别与辅助工具
    // =========================================================================

    /**
     * 智能识别中文/非UTF8字符编码（防乱码核心）
     */
    fun decodeSmartString(bytes: ByteArray, offset: Int, length: Int): String {
        if (length <= 0 || offset >= bytes.size) return ""
        val actualLen = minOf(length, bytes.size - offset)
        val slice = bytes.copyOfRange(offset, offset + actualLen)

        // 1. 先尝试 UTF-8
        try {
            val utf8Str = String(slice, Charsets.UTF_8)
            // 校验是否有乱码替换字符 '\uFFFD'
            if (!utf8Str.contains('\uFFFD')) {
                return utf8Str
            }
        } catch (_: Exception) {}

        // 2. 尝试 GB18030 / GBK（绝大多数早期华语 MP3 乱码根源）
        try {
            val gbkCharset = Charset.forName("GB18030")
            val gbkStr = String(slice, gbkCharset)
            if (gbkStr.isNotBlank()) {
                return gbkStr
            }
        } catch (_: Exception) {}

        // 3. 回退至 ISO-8859-1
        return String(slice, Charsets.ISO_8859_1)
    }

    private fun readSynchSafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
                ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
                ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
                (bytes[offset + 3].toInt() and 0x7F)
    }

    private fun readInt32BE(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readInt32LE(bytes: ByteArray, offset: Int): Int {
        return (bytes[offset].toInt() and 0xFF) or
                ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
                ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
                ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun skipString(data: ByteArray, startOffset: Int, encoding: Int): Int {
        var pos = startOffset
        if (encoding == 1 || encoding == 2) {
            // UTF-16: double 0x00 0x00 termination
            while (pos + 1 < data.size) {
                if (data[pos] == 0.toByte() && data[pos + 1] == 0.toByte()) {
                    return pos + 2
                }
                pos += 2
            }
            return data.size
        } else {
            // 1-byte 0x00 termination
            while (pos < data.size) {
                if (data[pos] == 0.toByte()) {
                    return pos + 1
                }
                pos++
            }
            return data.size
        }
    }

    private fun indexOfBytes(source: ByteArray, target: ByteArray): Int {
        if (target.isEmpty() || source.size < target.size) return -1
        for (i in 0..source.size - target.size) {
            var matched = true
            for (j in target.indices) {
                if (source[i + j] != target[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return i
        }
        return -1
    }
}
