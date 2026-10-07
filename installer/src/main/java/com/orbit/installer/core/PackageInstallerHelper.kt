package com.orbit.installer.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

sealed class InstallResult {
    object Success : InstallResult()
    object NeedUnknownSourcePermission : InstallResult()
    data class Failure(val message: String, val cause: Throwable? = null) : InstallResult()
}

object PackageInstallerHelper {

    /**
     * 检查是否有安装未知来源应用的权限（Android 8.0+ / API 26+）
     */
    fun hasUnknownAppSourcesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }
    }

    /**
     * 打开系统“允许安装未知应用”设置页面
     */
    fun requestUnknownAppSourcesPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return true
            } catch (e: ActivityNotFoundException) {
                // 部分深度定制车机可能精简了该设置项，降级打开通用安全/应用设置
                try {
                    val fallbackIntent = Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallbackIntent)
                    return true
                } catch (fallbackEx: Exception) {
                    return false
                }
            } catch (e: Exception) {
                return false
            }
        }
        return true
    }

    /**
     * 预检查 APK 文件合法性并读取基础信息
     */
    fun verifyApk(context: Context, apkFile: File): Result<String> {
        return try {
            if (!apkFile.exists() || apkFile.length() <= 0) {
                return Result.failure(IllegalArgumentException("安装包文件不存在或大小为空"))
            }

            val pm = context.packageManager
            val pkgInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
                ?: return Result.failure(IllegalArgumentException("解析安装包失败，文件可能已损坏或格式不兼容"))

            val appName = pkgInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString() } ?: pkgInfo.packageName
            val versionName = pkgInfo.versionName ?: "未知版本"
            Result.success("$appName ($versionName)")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 检查存储空间是否充足（要求至少留有 APK 大小的 2 倍空间）
     */
    fun checkAvailableStorage(requiredBytes: Long): Boolean {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val available = stat.availableBlocksLong * stat.blockSizeLong
            available >= (requiredBytes * 2)
        } catch (e: Exception) {
            true // 获取失败时不强行拦截
        }
    }

    /**
     * 唤起系统安装器安装 APK
     */
    fun installApk(context: Context, apkFile: File): InstallResult {
        try {
            // 1. 基础文件存在性检查
            if (!apkFile.exists() || apkFile.length() <= 0) {
                return InstallResult.Failure("安装包文件不存在或大小为0: ${apkFile.name}")
            }

            // 2. Android 8.0+ 权限检查
            if (!hasUnknownAppSourcesPermission(context)) {
                return InstallResult.NeedUnknownSourcePermission
            }

            // 3. 构建安全 URI（通过 FileProvider）
            val apkUri: Uri = try {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
            } catch (e: IllegalArgumentException) {
                return InstallResult.Failure("生成安全共享链接失败，请检查文件路径权限配置", e)
            } catch (e: Exception) {
                return InstallResult.Failure("读取文件安全URI异常: ${e.localizedMessage}", e)
            }

            // 4. 构建安装 Intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 5. 启动系统安装器并捕获各种车机异常
            context.startActivity(intent)
            return InstallResult.Success

        } catch (e: ActivityNotFoundException) {
            return InstallResult.Failure("未找到系统安装程序，当前车机固件可能禁用了应用安装或缺少 PackageInstaller 组件", e)
        } catch (e: SecurityException) {
            return InstallResult.Failure("系统安全策略限制，无法启动安装: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            return InstallResult.Failure("唤起安装失败: ${e.localizedMessage ?: "未知错误"}", e)
        }
    }
}
