package com.orbit.installer.core.engine

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.lang.reflect.Method

object ShizukuInstallEngine {

    /**
     * 检查 Shizuku 服务是否存活且可用
     */
    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 检查是否已获得 Shizuku 权限
     */
    fun hasPermission(): Boolean {
        return try {
            if (!isAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 请求 Shizuku 授权
     */
    fun requestPermission(requestCode: Int = 1001): Boolean {
        return try {
            if (isAvailable() && !hasPermission()) {
                Shizuku.requestPermission(requestCode)
                true
            } else {
                false
            }
        } catch (e: Throwable) {
            false
        }
    }

    /**
     * 通过 Shizuku 反射执行远程进程命令
     */
    private fun executeCommand(commands: Array<String>): Pair<Int, String> {
        val newProcessMethod: Method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        ).apply { isAccessible = true }

        val process = newProcessMethod.invoke(null, commands, null, null) as Process

        val inputStream: InputStream = process.inputStream
        val errorStream: InputStream = process.errorStream

        val reader = BufferedReader(InputStreamReader(inputStream))
        val errReader = BufferedReader(InputStreamReader(errorStream))
        val output = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        while (errReader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        val exitCode = process.waitFor()
        return Pair(exitCode, output.toString().trim())
    }

    /**
     * 通过 Shizuku 静默/免确认安装 APK
     */
    suspend fun installApk(apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            return@withContext Result.failure(IllegalStateException("Shizuku 服务未运行，请先在车机上启动 Shizuku"))
        }
        if (!hasPermission()) {
            return@withContext Result.failure(IllegalStateException("未获得 Shizuku 权限，请在 Shizuku 中授予本应用权限"))
        }

        try {
            val command = arrayOf("sh", "-c", "pm install -r -d -t \"${apkFile.absolutePath}\"")
            val (exitCode, resultStr) = executeCommand(command)

            if (exitCode == 0 && resultStr.contains("Success", ignoreCase = true)) {
                Result.success("Shizuku 提权安装成功")
            } else {
                Result.failure(Exception("安装执行失败 (退出码: $exitCode): $resultStr"))
            }
        } catch (e: Throwable) {
            Result.failure(Exception("Shizuku 执行异常: ${e.localizedMessage}", e))
        }
    }

    /**
     * 通过 Shizuku 静默卸载应用
     */
    suspend fun uninstallApp(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isAvailable() || !hasPermission()) {
            return@withContext Result.failure(IllegalStateException("Shizuku 未授权或未运行"))
        }

        try {
            val command = arrayOf("sh", "-c", "pm uninstall \"$packageName\"")
            val (exitCode, resultStr) = executeCommand(command)

            if (exitCode == 0 && resultStr.contains("Success", ignoreCase = true)) {
                Result.success("已通过 Shizuku 完成静默卸载")
            } else {
                Result.failure(Exception("卸载失败: $resultStr"))
            }
        } catch (e: Throwable) {
            Result.failure(Exception("Shizuku 卸载异常: ${e.localizedMessage}", e))
        }
    }
}
