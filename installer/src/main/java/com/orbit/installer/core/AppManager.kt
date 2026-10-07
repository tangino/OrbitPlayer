package com.orbit.installer.core

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.orbit.installer.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppManager {

    /**
     * 异步获取系统已安装的应用列表
     * @param includeSystem 是否包含系统预装应用
     */
    suspend fun getInstalledApps(context: Context, includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()

        try {
            val flags = PackageManager.GET_META_DATA
            val packages: List<PackageInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                pm.getInstalledPackages(flags)
            }

            for (pkg in packages) {
                try {
                    val appInfo = pkg.applicationInfo ?: continue
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                    if (!includeSystem && isSystem) {
                        continue
                    }

                    val appName = try {
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkg.packageName
                    }

                    val icon = try {
                        pm.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    val apkSize = try {
                        val file = File(appInfo.sourceDir)
                        if (file.exists()) file.length() else 0L
                    } catch (e: Exception) {
                        0L
                    }

                    val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkg.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkg.versionCode.toLong()
                    }

                    val launchIntent = pm.getLaunchIntentForPackage(pkg.packageName)

                    result.add(
                        AppInfo(
                            appName = appName,
                            packageName = pkg.packageName,
                            versionName = pkg.versionName ?: "1.0",
                            versionCode = versionCode,
                            icon = icon,
                            isSystemApp = isSystem,
                            firstInstallTime = pkg.firstInstallTime,
                            lastUpdateTime = pkg.lastUpdateTime,
                            apkSize = apkSize,
                            hasLaunchIntent = launchIntent != null
                        )
                    )
                } catch (e: Exception) {
                    // 忽略单个 App 解析失败，保障整体列表展示
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 默认按最后更新时间倒序排序
        result.sortedByDescending { it.lastUpdateTime }
    }

    /**
     * 启动指定应用
     */
    fun launchApp(context: Context, packageName: String): Result<Unit> {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Result.success(Unit)
            } else {
                Result.failure(ActivityNotFoundException("该应用未提供启动入口（无桌面 Launcher）"))
            }
        } catch (e: ActivityNotFoundException) {
            Result.failure(e)
        } catch (e: SecurityException) {
            Result.failure(SecurityException("系统安全策略限制启动该应用", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 请求卸载应用（调用系统交互式卸载确认）
     */
    fun uninstallApp(context: Context, packageName: String): Result<Unit> {
        return try {
            val intent = Intent(Intent.ACTION_DELETE).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: ActivityNotFoundException) {
            // 尝试备用 Intent
            try {
                @Suppress("DEPRECATION")
                val fallbackIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                Result.success(Unit)
            } catch (fallbackEx: Exception) {
                Result.failure(ActivityNotFoundException("系统缺少应用卸载管理器组件或被车机固件禁用"))
            }
        } catch (e: SecurityException) {
            Result.failure(SecurityException("权限受限，无法发起卸载请求", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 打开系统的应用详情设置页
     */
    fun openAppSettings(context: Context, packageName: String): Result<Unit> {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "未知大小"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
