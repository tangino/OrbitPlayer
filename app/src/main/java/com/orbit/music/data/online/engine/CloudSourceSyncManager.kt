package com.orbit.music.data.online.engine

import android.content.Context
import android.content.SharedPreferences
import com.orbit.music.data.online.model.SourceScriptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 基于 Cloudflare D1 边缘数据库的音源脚本多端云同步管理器（账号密码多端同步）
 */
class CloudSourceSyncManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("orbit_cloud_sync_pref", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val defaultBaseUrl = "https://orbit.ginodung.workers.dev"

    companion object {
        private const val KEY_SYNC_USERNAME = "cloud_sync_username"
        private const val KEY_SYNC_PASSWORD = "cloud_sync_password"
        private const val KEY_LAST_BACKUP_TIME = "cloud_last_backup_time"
        private const val KEY_CUSTOM_SERVER_URL = "cloud_custom_server_url"

        @Volatile
        private var instance: CloudSourceSyncManager? = null

        fun getInstance(context: Context): CloudSourceSyncManager {
            return instance ?: synchronized(this) {
                instance ?: CloudSourceSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 获取当前生效的云端 API 服务器地址
     */
    fun getServerUrl(): String {
        val custom = prefs.getString(KEY_CUSTOM_SERVER_URL, null)
        return if (!custom.isNullOrBlank()) custom.trim().removeSuffix("/") else defaultBaseUrl
    }

    /**
     * 设置自定义云端 API 服务器地址 (如绑定了自定义域名的 Worker)
     */
    fun setServerUrl(url: String) {
        val clean = url.trim().removeSuffix("/")
        if (clean.isBlank() || clean == defaultBaseUrl) {
            prefs.edit().remove(KEY_CUSTOM_SERVER_URL).apply()
        } else {
            val formatted = if (!clean.startsWith("http://") && !clean.startsWith("https://")) "https://$clean" else clean
            prefs.edit().putString(KEY_CUSTOM_SERVER_URL, formatted).apply()
        }
    }

    /**
     * 检查当前是否已登录同步账号
     */
    fun isLoggedIn(): Boolean {
        return !getUsername().isNullOrBlank()
    }

    /**
     * 获取当前登录的账号名称
     */
    fun getUsername(): String? {
        val user = prefs.getString(KEY_SYNC_USERNAME, null)
        return if (user.isNullOrBlank()) null else user.trim()
    }

    /**
     * 获取当前保存的密码
     */
    fun getPassword(): String? {
        return prefs.getString(KEY_SYNC_PASSWORD, null)
    }

    /**
     * 账号登录或自动注册
     */
    suspend fun loginOrRegister(username: String, password: String): Result<AuthResult> = withContext(Dispatchers.IO) {
        try {
            val cleanUser = username.trim()
            val cleanPwd = password.trim()
            if (cleanUser.length < 2) {
                return@withContext Result.failure(Exception("账号长度至少需要 2 个字符"))
            }
            if (cleanPwd.length < 4) {
                return@withContext Result.failure(Exception("密码长度至少需要 4 位"))
            }

            val baseUrl = getServerUrl()
            val reqJson = JSONObject().apply {
                put("username", cleanUser)
                put("password", cleanPwd)
            }

            val requestBody = reqJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/user/auth")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val resBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = runCatching { JSONObject(resBody).optString("message") }.getOrNull()
                    ?: "HTTP ${response.code}: 登录失败"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonRes = JSONObject(resBody)
            if (jsonRes.optBoolean("success", false)) {
                // 保存登录凭证
                prefs.edit()
                    .putString(KEY_SYNC_USERNAME, cleanUser)
                    .putString(KEY_SYNC_PASSWORD, cleanPwd)
                    .apply()

                val isNew = jsonRes.optBoolean("isNew", false)
                val msg = jsonRes.optString("message", if (isNew) "注册并登录成功" else "登录成功")
                Result.success(AuthResult(username = cleanUser, isNew = isNew, message = msg))
            } else {
                Result.failure(Exception(jsonRes.optString("message", "登录失败")))
            }
        } catch (e: Exception) {
            val friendlyMsg = formatNetworkException(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * 退出当前登录账号
     */
    fun logout() {
        prefs.edit()
            .remove(KEY_SYNC_USERNAME)
            .remove(KEY_SYNC_PASSWORD)
            .remove(KEY_LAST_BACKUP_TIME)
            .apply()
    }

    /**
     * 获取上次云端备份时间戳
     */
    fun getLastBackupTime(): Long {
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0L)
    }

    /**
     * 将本地全部音源脚本上传备份至云端当前账号
     */
    suspend fun backupSourcesToCloud(scripts: List<SourceScriptItem>): Result<BackupResult> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername()
                ?: return@withContext Result.failure(Exception("请先登录同步账号后再执行备份"))
            val password = getPassword() ?: ""

            val baseUrl = getServerUrl()
            val jsonArray = JSONArray()
            for (script in scripts) {
                jsonArray.put(script.toJson())
            }

            val requestJson = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("sourcesJson", jsonArray.toString())
                put("sourceCount", scripts.size)
            }

            val requestBody = requestJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/user/sources/backup")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = runCatching { JSONObject(responseBody).optString("message") }.getOrNull()
                    ?: "HTTP ${response.code}: 备份请求失败"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonRes = JSONObject(responseBody)
            if (jsonRes.optBoolean("success", false)) {
                val now = System.currentTimeMillis()
                prefs.edit().putLong(KEY_LAST_BACKUP_TIME, now).apply()
                Result.success(
                    BackupResult(
                        username = username,
                        sourceCount = scripts.size,
                        updatedAt = now,
                        message = jsonRes.optString("message", "备份成功")
                    )
                )
            } else {
                Result.failure(Exception(jsonRes.optString("message", "备份失败")))
            }
        } catch (e: Exception) {
            val friendlyMsg = formatNetworkException(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * 从云端当前账号拉取并恢复音源脚本
     */
    suspend fun restoreSourcesFromCloud(): Result<List<SourceScriptItem>> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername()
                ?: return@withContext Result.failure(Exception("请先登录同步账号后再执行拉取"))
            val password = getPassword() ?: ""

            val baseUrl = getServerUrl()
            val reqJson = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = reqJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/user/sources/restore")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = runCatching { JSONObject(responseBody).optString("message") }.getOrNull()
                    ?: "HTTP ${response.code}: 云端拉取失败"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonRes = JSONObject(responseBody)
            if (!jsonRes.optBoolean("success", false)) {
                return@withContext Result.failure(Exception(jsonRes.optString("message", "未找到云端备份")))
            }

            val dataObj = jsonRes.optJSONObject("data")
                ?: return@withContext Result.failure(Exception("云端返回数据格式异常"))

            val sourcesJsonStr = dataObj.optString("sources_json", "[]")
            val array = JSONArray(sourcesJsonStr)
            val list = mutableListOf<SourceScriptItem>()
            for (i in 0 until array.length()) {
                val itemObj = array.getJSONObject(i)
                list.add(SourceScriptItem.fromJson(itemObj))
            }

            Result.success(list)
        } catch (e: Exception) {
            val friendlyMsg = formatNetworkException(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * 删除当前账号在云端的音源备份数据
     */
    suspend fun deleteCloudSourceBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername()
                ?: return@withContext Result.failure(Exception("请先登录同步账号后再执行删除"))
            val password = getPassword() ?: ""

            val baseUrl = getServerUrl()
            val reqJson = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = reqJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/user/sources/delete")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = runCatching { JSONObject(responseBody).optString("message") }.getOrNull()
                    ?: "HTTP ${response.code}: 删除云端音源失败"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonRes = JSONObject(responseBody)
            if (jsonRes.optBoolean("success", false)) {
                prefs.edit().remove(KEY_LAST_BACKUP_TIME).apply()
                Result.success(jsonRes.optString("message", "云端音源备份已成功删除"))
            } else {
                Result.failure(Exception(jsonRes.optString("message", "删除失败")))
            }
        } catch (e: Exception) {
            val friendlyMsg = formatNetworkException(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    /**
     * 清空当前账号在云端的所有数据（音源、歌单备份与偏好）
     */
    suspend fun clearAllCloudData(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val username = getUsername()
                ?: return@withContext Result.failure(Exception("请先登录同步账号"))
            val password = getPassword() ?: ""

            val baseUrl = getServerUrl()
            val reqJson = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val requestBody = reqJson.toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/user/data/clear")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorMsg = runCatching { JSONObject(responseBody).optString("message") }.getOrNull()
                    ?: "HTTP ${response.code}: 清空云端数据失败"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val jsonRes = JSONObject(responseBody)
            if (jsonRes.optBoolean("success", false)) {
                prefs.edit().remove(KEY_LAST_BACKUP_TIME).apply()
                Result.success(jsonRes.optString("message", "已清空该账号全部云端数据"))
            } else {
                Result.failure(Exception(jsonRes.optString("message", "清空失败")))
            }
        } catch (e: Exception) {
            val friendlyMsg = formatNetworkException(e)
            Result.failure(Exception(friendlyMsg, e))
        }
    }

    private fun formatNetworkException(e: Exception): String {
        val msg = e.localizedMessage ?: e.message ?: ""
        return when {
            msg.contains("failed to connect", ignoreCase = true) || msg.contains("timeout", ignoreCase = true) -> {
                "连接超时：国内网络访问 workers.dev 域名受阻。建议开启代理测试，或在 Cloudflare 绑定自定义域名。"
            }
            msg.contains("Unable to resolve host", ignoreCase = true) -> {
                "无法解析服务器域名，请检查网络连接或服务器配置。"
            }
            else -> msg.ifBlank { "网络请求异常，请检查网络状态" }
        }
    }

    data class AuthResult(
        val username: String,
        val isNew: Boolean,
        val message: String
    )

    data class BackupResult(
        val username: String,
        val sourceCount: Int,
        val updatedAt: Long,
        val message: String
    )
}
