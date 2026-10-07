package com.orbit.installer.core

import android.content.Context
import com.orbit.installer.core.engine.LocalAdbEngine
import com.orbit.installer.core.engine.ShizukuInstallEngine
import java.io.File

enum class InstallEngineMode(val displayName: String, val desc: String) {
    AUTO("智能优选", "优先尝试 Shizuku/ADB 免弹窗提权，失败自动降级系统安装"),
    SHIZUKU("Shizuku 提权", "免 Root 借用 ADB 权限直接静默安装/卸载"),
    LOCAL_ADB("本地无线 ADB", "连接车机 127.0.0.1 调试端口执行静默安装/卸载"),
    SYSTEM("系统标准模式", "通过系统 PackageInstaller 弹窗确认安装/卸载")
}

data class EngineStatus(
    val isShizukuAvailable: Boolean,
    val hasShizukuPermission: Boolean,
    val isLocalAdbPortOpen: Boolean,
    val localAdbPort: Int
)

sealed class DispatchInstallResult {
    data class SilentSuccess(val engineUsed: String, val message: String) : DispatchInstallResult()
    object FallbackToSystemIntent : DispatchInstallResult()
    data class Failure(val message: String, val cause: Throwable? = null) : DispatchInstallResult()
}

object InstallerDispatcher {

    var currentMode: InstallEngineMode = InstallEngineMode.AUTO

    /**
     * 检测当前各引擎的运行状态
     */
    suspend fun checkEngineStatus(context: Context): EngineStatus {
        val shizukuAvail = ShizukuInstallEngine.isAvailable()
        val shizukuPerm = if (shizukuAvail) ShizukuInstallEngine.hasPermission() else false
        val port = LocalAdbEngine.getAdbPort(context)
        val adbPortOpen = LocalAdbEngine.testAdbPortOpen(port = port)
        return EngineStatus(
            isShizukuAvailable = shizukuAvail,
            hasShizukuPermission = shizukuPerm,
            isLocalAdbPortOpen = adbPortOpen,
            localAdbPort = port
        )
    }

    /**
     * 统一安装分发
     */
    suspend fun dispatchInstall(
        context: Context,
        apkFile: File,
        mode: InstallEngineMode = currentMode
    ): DispatchInstallResult {
        // 1. 预校验 APK
        val verifyRes = PackageInstallerHelper.verifyApk(context, apkFile)
        if (verifyRes.isFailure) {
            return DispatchInstallResult.Failure(
                "安装包解析校验失败: ${verifyRes.exceptionOrNull()?.localizedMessage ?: "文件可能损坏"}"
            )
        }

        // 2. 根据模式执行
        when (mode) {
            InstallEngineMode.SHIZUKU -> {
                val res = ShizukuInstallEngine.installApk(apkFile)
                return if (res.isSuccess) {
                    DispatchInstallResult.SilentSuccess("Shizuku", res.getOrNull() ?: "安装成功")
                } else {
                    DispatchInstallResult.Failure(res.exceptionOrNull()?.localizedMessage ?: "Shizuku 安装失败")
                }
            }
            InstallEngineMode.LOCAL_ADB -> {
                val res = LocalAdbEngine.installApk(context, apkFile)
                return if (res.isSuccess) {
                    DispatchInstallResult.SilentSuccess("本地 ADB", res.getOrNull() ?: "安装成功")
                } else {
                    DispatchInstallResult.Failure(res.exceptionOrNull()?.localizedMessage ?: "本地 ADB 安装失败")
                }
            }
            InstallEngineMode.SYSTEM -> {
                return DispatchInstallResult.FallbackToSystemIntent
            }
            InstallEngineMode.AUTO -> {
                // 智能优选：Shizuku -> 本地 ADB -> 系统标准
                if (ShizukuInstallEngine.isAvailable() && ShizukuInstallEngine.hasPermission()) {
                    val shizukuRes = ShizukuInstallEngine.installApk(apkFile)
                    if (shizukuRes.isSuccess) {
                        return DispatchInstallResult.SilentSuccess("Shizuku", shizukuRes.getOrNull() ?: "安装成功")
                    }
                }

                val currentPort = LocalAdbEngine.getAdbPort(context)
                if (LocalAdbEngine.testAdbPortOpen(port = currentPort)) {
                    val adbRes = LocalAdbEngine.installApk(context, apkFile)
                    if (adbRes.isSuccess) {
                        return DispatchInstallResult.SilentSuccess("本地 ADB", adbRes.getOrNull() ?: "安装成功")
                    }
                }

                // 自动降级至系统标准安装
                return DispatchInstallResult.FallbackToSystemIntent
            }
        }
    }

    /**
     * 统一卸载分发
     */
    suspend fun dispatchUninstall(
        context: Context,
        packageName: String,
        mode: InstallEngineMode = currentMode
    ): Result<String> {
        when (mode) {
            InstallEngineMode.SHIZUKU -> {
                return ShizukuInstallEngine.uninstallApp(packageName)
            }
            InstallEngineMode.LOCAL_ADB -> {
                return LocalAdbEngine.uninstallApp(context, packageName)
            }
            InstallEngineMode.AUTO -> {
                if (ShizukuInstallEngine.isAvailable() && ShizukuInstallEngine.hasPermission()) {
                    val res = ShizukuInstallEngine.uninstallApp(packageName)
                    if (res.isSuccess) return res
                }
                val currentPort = LocalAdbEngine.getAdbPort(context)
                if (LocalAdbEngine.testAdbPortOpen(port = currentPort)) {
                    val res = LocalAdbEngine.uninstallApp(context, packageName)
                    if (res.isSuccess) return res
                }
                // 降级使用系统卸载 Intent
                val sysRes = AppManager.uninstallApp(context, packageName)
                return if (sysRes.isSuccess) {
                    Result.success("已调起系统卸载确认流程")
                } else {
                    Result.failure(sysRes.exceptionOrNull() ?: Exception("卸载调用失败"))
                }
            }
            InstallEngineMode.SYSTEM -> {
                val sysRes = AppManager.uninstallApp(context, packageName)
                return if (sysRes.isSuccess) {
                    Result.success("已调起系统卸载确认流程")
                } else {
                    Result.failure(sysRes.exceptionOrNull() ?: Exception("卸载调用失败"))
                }
            }
        }
    }
}
