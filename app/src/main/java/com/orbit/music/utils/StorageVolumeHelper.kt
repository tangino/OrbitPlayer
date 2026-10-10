package com.orbit.music.utils

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.*

/**
 * 结构化存储设备与卷信息
 */
data class AppStorageVolume(
    val id: String,
    val name: String,
    val path: File,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val totalBytes: Long = 0L,
    val freeBytes: Long = 0L
) {
    val totalSizeFormatted: String get() = StorageVolumeHelper.formatBytes(totalBytes)
    val freeSizeFormatted: String get() = StorageVolumeHelper.formatBytes(freeBytes)

    val displayLabel: String get() {
        return if (totalBytes > 0) {
            "$name ($freeSizeFormatted 可用 / $totalSizeFormatted)"
        } else {
            name
        }
    }
}

object StorageVolumeHelper {

    /**
     * 获取设备上所有可读的存储卷（包括内置主存储、外置 SD 卡、USB U 盘、车载扩展盘等）
     */
    fun getAllStorageVolumes(context: Context): List<AppStorageVolume> {
        val result = mutableListOf<AppStorageVolume>()
        val seenPaths = mutableSetOf<String>()

        // 1. 优先通过系统官方 StorageManager 服务获取已挂载的全部 StorageVolume
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val volumes = sm.storageVolumes
                for (vol in volumes) {
                    val state = getVolumeState(vol)
                    if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                        continue
                    }

                    val dir = getVolumeDirectory(vol)
                    if (dir != null && dir.exists() && dir.canRead()) {
                        val absPath = dir.absolutePath
                        if (!seenPaths.contains(absPath)) {
                            seenPaths.add(absPath)
                            val isPrimary = vol.isPrimary
                            val isRemovable = vol.isRemovable
                            val desc = vol.getDescription(context)
                            val name = when {
                                isPrimary -> "内部存储"
                                !desc.isNullOrBlank() && desc != "null" -> desc
                                isRemovable -> "外置存储 (${dir.name})"
                                else -> "存储设备 (${dir.name})"
                            }
                            val (total, free) = queryStorageSpace(dir)
                            result.add(
                                AppStorageVolume(
                                    id = "sm_${absPath.hashCode()}",
                                    name = name,
                                    path = dir,
                                    isRemovable = isRemovable,
                                    isPrimary = isPrimary,
                                    totalBytes = total,
                                    freeBytes = free
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. 补漏：通过 getExternalFilesDirs 获取所有物理介质并回溯至介质根目录
        try {
            val extFilesDirs = context.getExternalFilesDirs(null)
            for (f in extFilesDirs) {
                if (f != null && f.exists() && f.canRead()) {
                    val root = findStorageRoot(f)
                    if (root != null && root.exists() && root.canRead()) {
                        val absPath = root.absolutePath
                        if (!seenPaths.contains(absPath)) {
                            seenPaths.add(absPath)
                            val isPrimary = absPath.contains("emulated/0")
                            val name = if (isPrimary) "内部存储" else "外置存储卡 (${root.name})"
                            val (total, free) = queryStorageSpace(root)
                            result.add(
                                AppStorageVolume(
                                    id = "ef_${absPath.hashCode()}",
                                    name = name,
                                    path = root,
                                    isRemovable = !isPrimary,
                                    isPrimary = isPrimary,
                                    totalBytes = total,
                                    freeBytes = free
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. 补漏：扫描 /storage 目录以及车机常见的外置挂载点 (/mnt/media_rw, /mnt/usbhost, /mnt/sdcard 等)
        val candidateMounts = listOf(
            "/storage",
            "/mnt/media_rw",
            "/mnt/usbhost",
            "/mnt/usb_storage",
            "/mnt/extSdCard",
            "/mnt/sdcard2"
        )
        for (mountDirStr in candidateMounts) {
            try {
                val mountDir = File(mountDirStr)
                if (mountDir.exists() && mountDir.isDirectory) {
                    mountDir.listFiles()?.forEach { subFile ->
                        if (subFile.isDirectory && subFile.canRead() && !subFile.name.startsWith(".")) {
                            val name = subFile.name
                            if (name != "emulated" && name != "self" && name != "knox") {
                                val absPath = subFile.absolutePath
                                if (!seenPaths.contains(absPath)) {
                                    seenPaths.add(absPath)
                                    val (total, free) = queryStorageSpace(subFile)
                                    val label = if (name.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}"))) {
                                        "外置 SD 卡 / USB U 盘 ($name)"
                                    } else {
                                        "车载扩展存储 ($name)"
                                    }
                                    result.add(
                                        AppStorageVolume(
                                            id = "scan_${absPath.hashCode()}",
                                            name = label,
                                            path = subFile,
                                            isRemovable = true,
                                            isPrimary = false,
                                            totalBytes = total,
                                            freeBytes = free
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. 补漏：读取 /proc/mounts 过滤外部挂载点 (vfat, exfat, ntfs, fuse, sdcardfs)
        try {
            val mountsFile = File("/proc/mounts")
            if (mountsFile.exists() && mountsFile.canRead()) {
                BufferedReader(FileReader(mountsFile)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 3) {
                            val mountPath = parts[1]
                            val fsType = parts[2].lowercase(Locale.getDefault())
                            if (mountPath.startsWith("/storage/") || mountPath.startsWith("/mnt/")) {
                                if (fsType.contains("vfat") || fsType.contains("exfat") || fsType.contains("ntfs") ||
                                    fsType.contains("fuse") || fsType.contains("sdcardfs") || fsType.contains("ext4")) {
                                    val dir = File(mountPath)
                                    if (dir.exists() && dir.isDirectory && dir.canRead()) {
                                        val absPath = dir.absolutePath
                                        if (!seenPaths.contains(absPath) && !absPath.endsWith("/emulated") && !absPath.endsWith("/self")) {
                                            seenPaths.add(absPath)
                                            val (total, free) = queryStorageSpace(dir)
                                            result.add(
                                                AppStorageVolume(
                                                    id = "proc_${absPath.hashCode()}",
                                                    name = "挂载存储 (${dir.name})",
                                                    path = dir,
                                                    isRemovable = true,
                                                    isPrimary = false,
                                                    totalBytes = total,
                                                    freeBytes = free
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }
        } catch (_: Exception) {}

        // 确保内部存储始终置顶排在第 1 位
        return result.sortedWith(compareByDescending<AppStorageVolume> { it.isPrimary }.thenBy { it.name })
    }

    private fun getVolumeState(volume: StorageVolume): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.state
        } else {
            try {
                val method = volume.javaClass.getMethod("getState")
                method.invoke(volume) as? String ?: Environment.MEDIA_MOUNTED
            } catch (_: Exception) {
                Environment.MEDIA_MOUNTED
            }
        }
    }

    private fun getVolumeDirectory(volume: StorageVolume): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory
        } else {
            try {
                val method = volume.javaClass.getMethod("getPathFile")
                method.invoke(volume) as? File
            } catch (_: Exception) {
                try {
                    val method = volume.javaClass.getMethod("getPath")
                    (method.invoke(volume) as? String)?.let { File(it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    private fun findStorageRoot(subPath: File): File? {
        var curr: File? = subPath
        while (curr != null) {
            val parent = curr.parentFile
            if (parent != null) {
                val pPath = parent.absolutePath
                if (pPath == "/storage" || pPath == "/mnt" || pPath == "/mnt/media_rw") {
                    return curr
                }
            }
            curr = parent
        }
        return null
    }

    private fun queryStorageSpace(dir: File): Pair<Long, Long> {
        return try {
            val stat = StatFs(dir.absolutePath)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            Pair(totalBlocks * blockSize, availableBlocks * blockSize)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        val formatted = String.format(Locale.getDefault(), "%.1f", bytes / Math.pow(1024.0, digitGroups.toDouble()))
        return "$formatted ${units[digitGroups]}"
    }
}
