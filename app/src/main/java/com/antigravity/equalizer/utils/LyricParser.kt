package com.antigravity.equalizer.utils

import java.io.File
import java.nio.charset.Charset
import java.util.regex.Pattern

/**
 * 单行歌词数据模型
 */
data class LyricLine(
    val timeMs: Long,
    val text: String
)

/**
 * 专业 LRC 歌词文件解析器
 * 1. 自动寻找音频文件同目录下的同名 .lrc 或 .LRC 文件；
 * 2. 兼容常见中文编码（UTF-8、GBK、GB2312、UTF-16），避免歌词乱码；
 * 3. 完美兼容标准与非标准时间戳（如 [01:23.45]、[01:23.456]、[01:23] 以及多时间戳同行）。
 */
object LyricParser {

    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]")

    /**
     * 根据音频绝对路径查找并解析同目录下的同名歌词文件
     */
    fun loadLyricForSong(audioFilePath: String?): List<LyricLine> {
        if (audioFilePath.isNullOrBlank()) return emptyList()

        try {
            val audioFile = File(audioFilePath)
            if (!audioFile.exists()) return emptyList()

            val parentDir = audioFile.parentFile ?: return emptyList()
            val baseName = audioFile.nameWithoutExtension

            // 优先查找完全同名的 .lrc / .LRC 文件
            val candidates = listOf(
                File(parentDir, "$baseName.lrc"),
                File(parentDir, "$baseName.LRC"),
                File(parentDir, "$baseName.txt")
            )

            val lrcFile = candidates.firstOrNull { it.exists() && it.isFile && it.length() > 0 }
                ?: parentDir.listFiles()?.firstOrNull {
                    it.isFile && it.nameWithoutExtension.equals(baseName, ignoreCase = true) &&
                            (it.extension.equals("lrc", ignoreCase = true) || it.extension.equals("txt", ignoreCase = true))
                }

            if (lrcFile != null) {
                return parseFile(lrcFile)
            }
        } catch (_: Exception) {
            // 文件访问异常处理
        }

        return emptyList()
    }

    /**
     * 解析 LRC 文件内容并兼容多种字符编码
     */
    fun parseFile(file: File): List<LyricLine> {
        return try {
            val bytes = file.readBytes()
            // 依次尝试 UTF-8、GBK (常见于中文老歌词)、GB2312、UTF-16
            val encodings = listOf("UTF-8", "GBK", "GB2312", "UTF-16LE", "UTF-16BE")
            var content: String? = null

            for (enc in encodings) {
                try {
                    val charset = Charset.forName(enc)
                    val decoded = String(bytes, charset)
                    if (!decoded.contains("")) {
                        content = decoded
                        break
                    }
                } catch (_: Exception) {
                }
            }

            parseText(content ?: String(bytes, Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * 解析字符串格式的歌词内容
     */
    fun parseText(text: String): List<LyricLine> {
        if (text.isBlank()) return emptyList()

        val lines = mutableListOf<LyricLine>()

        text.lineSequence().forEach { rawLine ->
            val trimmed = rawLine.trim()
            if (trimmed.isEmpty()) return@forEach

            // 过滤元数据标签：[ti:], [ar:], [al:], [by:], [offset:]
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:")
            ) {
                return@forEach
            }

            val matcher = TIME_TAG_PATTERN.matcher(trimmed)
            val timeOffsets = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                val min = matcher.group(1)?.toLongOrNull() ?: 0L
                val sec = matcher.group(2)?.toLongOrNull() ?: 0L
                val msStr = matcher.group(3)
                val ms = when {
                    msStr == null -> 0L
                    msStr.length == 1 -> msStr.toLong() * 100L
                    msStr.length == 2 -> msStr.toLong() * 10L
                    else -> msStr.take(3).toLong()
                }

                val totalMs = min * 60 * 1000L + sec * 1000L + ms
                timeOffsets.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timeOffsets.isNotEmpty()) {
                val lyricContent = trimmed.substring(lastMatchEnd).trim()
                if (lyricContent.isNotEmpty()) {
                    for (time in timeOffsets) {
                        lines.add(LyricLine(timeMs = time, text = lyricContent))
                    }
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }
}
