package com.orbit.installer.core.engine

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket

object LocalAdbEngine {

    const val DEFAULT_ADB_HOST = "127.0.0.1"
    const val DEFAULT_ADB_PORT = 5555
    val COMMON_PORTS = listOf(5555, 5556, 6666, 8888, 2333, 7777)

    private const val PREFS_NAME = "orbit_adb_prefs"
    private const val KEY_ADB_PORT = "custom_adb_port"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 获取当前配置的 ADB 端口号
     */
    fun getAdbPort(context: Context): Int {
        return getPrefs(context).getInt(KEY_ADB_PORT, DEFAULT_ADB_PORT)
    }

    /**
     * 保存自定义 ADB 端口号
     */
    fun setAdbPort(context: Context, port: Int) {
        val validPort = if (port in 1..65535) port else DEFAULT_ADB_PORT
        getPrefs(context).edit().putInt(KEY_ADB_PORT, validPort).apply()
    }

    /**
     * 测试车机本地指定 ADB 端口是否处于开放状态
     */
    suspend fun testAdbPortOpen(host: String = DEFAULT_ADB_HOST, port: Int = DEFAULT_ADB_PORT): Boolean = withContext(Dispatchers.IO) {
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 600)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 快速探测车机开放的 ADB 端口（优先探测当前自定义端口，其次探测常用车机端口）
     */
    suspend fun probeOpenPort(context: Context): Int? = withContext(Dispatchers.IO) {
        val configuredPort = getAdbPort(context)
        // 优先探测已配置端口
        if (testAdbPortOpen(port = configuredPort)) {
            return@withContext configuredPort
        }
        // 探测常见候选端口
        for (port in COMMON_PORTS) {
            if (port != configuredPort && testAdbPortOpen(port = port)) {
                return@withContext port
            }
        }
        null
    }

    /**
     * 检查车机系统是否可以直接通过 Runtime Shell 执行 pm 指令（针对部分工程调试固件）
     */
    suspend fun canExecuteDirectShell(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("which pm")
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 通过本地 Shell / Root / 调试端口执行安装
     */
    suspend fun installApk(context: Context, apkFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentPort = getAdbPort(context)
            val isPortOpen = testAdbPortOpen(port = currentPort)

            // 尝试通过 su 或者 直接 sh 执行 pm install -r -d
            val commands = listOf(
                "pm install -r -d -t \"${apkFile.absolutePath}\"",
                "su -c 'pm install -r -d -t \"${apkFile.absolutePath}\"'"
            )

            for (cmd in commands) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    while (errReader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }

                    val exitCode = process.waitFor()
                    val resultStr = output.toString().trim()

                    if (exitCode == 0 && resultStr.contains("Success", ignoreCase = true)) {
                        return@withContext Result.success("通过本地 ADB/Shell 命令提权安装成功")
                    }
                } catch (e: Exception) {
                    // 尝试下一条命令
                }
            }

            if (isPortOpen) {
                Result.failure(Exception("已检测到本地端口 $currentPort 开放，但当前应用未授权 ADB 配对密钥或 Shell 执行受限"))
            } else {
                Result.failure(Exception("车机本地端口 $currentPort 未开放，请在引擎设置中自定义端口或于车机工程模式中开启无线调试"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("本地 ADB 执行异常: ${e.localizedMessage}", e))
        }
    }

    /**
     * 通过本地 Shell / Root 执行卸载
     */
    suspend fun uninstallApp(context: Context, packageName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val currentPort = getAdbPort(context)
            val isPortOpen = testAdbPortOpen(port = currentPort)

            val commands = listOf(
                "pm uninstall \"$packageName\"",
                "su -c 'pm uninstall \"$packageName\"'"
            )

            for (cmd in commands) {
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd))
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val errReader = BufferedReader(InputStreamReader(process.errorStream))
                    val output = StringBuilder()
                    var line: String?

                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    while (errReader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }

                    val exitCode = process.waitFor()
                    val resultStr = output.toString().trim()

                    if (exitCode == 0 && resultStr.contains("Success", ignoreCase = true)) {
                        return@withContext Result.success("通过本地 ADB/Shell 完成静默卸载")
                    }
                } catch (e: Exception) {
                    // 尝试下一条命令
                }
            }

            if (isPortOpen) {
                Result.failure(Exception("已检测到本地端口 $currentPort 开放，但当前应用未授权 ADB 配对密钥或 Shell 执行受限"))
            } else {
                Result.failure(Exception("车机本地端口 $currentPort 未开放，请在引擎设置中自定义端口或于车机工程模式中开启无线调试"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("本地 ADB 卸载异常: ${e.localizedMessage}", e))
        }
    }
}
