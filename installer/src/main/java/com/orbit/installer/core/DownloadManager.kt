package com.orbit.installer.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float, val currentBytes: Long, val totalBytes: Long) : DownloadState()
    data class Success(val file: File) : DownloadState()
    data class Error(val message: String, val cause: Throwable? = null) : DownloadState()
}

class DownloadManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * 下载网络 APK 文件
     * @param url 下载直链
     * @param onProgress 进度回调 (0.0 ~ 1.0, currentBytes, totalBytes)
     */
    suspend fun downloadApk(
        url: String,
        onProgress: (Float, Long, Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty() || (!trimmedUrl.startsWith("http://") && !trimmedUrl.startsWith("https://"))) {
            return@withContext Result.failure(IllegalArgumentException("请输入以 http:// 或 https:// 开头的有效链接"))
        }

        val cacheDir = context.externalCacheDir ?: context.cacheDir
        val apkDir = File(cacheDir, "downloads").apply { if (!exists()) mkdirs() }
        val targetFile = File(apkDir, "downloaded_target.apk")

        try {
            if (targetFile.exists()) {
                targetFile.delete()
            }

            val request = Request.Builder()
                .url(trimmedUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; OrbitInstaller/1.0.0)")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(IOException("服务器响应异常，HTTP 状态码: ${response.code}"))
            }

            val body = response.body ?: return@withContext Result.failure(IOException("服务器返回内容为空"))
            val totalBytes = body.contentLength()

            // 存储空间检查
            if (totalBytes > 0 && !PackageInstallerHelper.checkAvailableStorage(totalBytes)) {
                return@withContext Result.failure(IOException("车机存储空间不足，无法下载该应用安装包"))
            }

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L
                    var lastReportTime = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastReportTime > 100 || totalRead == totalBytes) {
                            lastReportTime = currentTime
                            val progress = if (totalBytes > 0) totalRead.toFloat() / totalBytes else 0f
                            onProgress(progress, totalRead, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            Result.success(targetFile)

        } catch (e: UnknownHostException) {
            Result.failure(IOException("无法连接网络或域名解析失败，请检查车机网络连接", e))
        } catch (e: SocketTimeoutException) {
            Result.failure(IOException("网络连接超时，地下车库或弱网环境下可稍后重试", e))
        } catch (e: SSLHandshakeException) {
            Result.failure(IOException("SSL 安全证书验证失败，请确认车机系统时间是否准确", e))
        } catch (e: MalformedURLException) {
            Result.failure(IllegalArgumentException("下载链接格式错误: ${e.localizedMessage}", e))
        } catch (e: IOException) {
            Result.failure(IOException("读写安装包失败: ${e.localizedMessage}", e))
        } catch (e: Exception) {
            Result.failure(Exception("下载过程中发生未知异常: ${e.localizedMessage}", e))
        }
    }
}
