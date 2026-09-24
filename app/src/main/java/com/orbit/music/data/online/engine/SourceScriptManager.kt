package com.orbit.music.data.online.engine

import android.content.Context
import android.content.SharedPreferences
import com.orbit.music.data.online.model.SourceScriptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * 在线音源脚本持久化与生命周期管理器
 */
class SourceScriptManager private constructor(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("online_source_scripts_pref", Context.MODE_PRIVATE)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _scripts = MutableStateFlow<List<SourceScriptItem>>(emptyList())
    val scripts: StateFlow<List<SourceScriptItem>> = _scripts.asStateFlow()

    private val _activeScript = MutableStateFlow<SourceScriptItem?>(null)
    val activeScript: StateFlow<SourceScriptItem?> = _activeScript.asStateFlow()

    private val _preferredQuality = MutableStateFlow("flac")
    val preferredQuality: StateFlow<String> = _preferredQuality.asStateFlow()

    init {
        loadSavedConfig()
    }

    private fun loadSavedConfig() {
        val quality = prefs.getString("preferred_quality", "flac") ?: "flac"
        _preferredQuality.value = quality

        val rawJson = prefs.getString("scripts_list_json", null)
        val loadedList = mutableListOf<SourceScriptItem>()

        if (!rawJson.isNullOrBlank()) {
            try {
                val array = JSONArray(rawJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    loadedList.add(SourceScriptItem.fromJson(obj))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _scripts.value = loadedList
        val active = loadedList.firstOrNull { it.isEnabled }
        _activeScript.value = active
    }

    private fun saveConfig() {
        val array = JSONArray()
        for (item in _scripts.value) {
            array.put(item.toJson())
        }
        prefs.edit()
            .putString("scripts_list_json", array.toString())
            .putString("preferred_quality", _preferredQuality.value)
            .apply()
    }

    fun setPreferredQuality(quality: String) {
        _preferredQuality.value = quality
        prefs.edit().putString("preferred_quality", quality).apply()
    }

    /**
     * 从网络 URL 下载并导入音源脚本
     */
    suspend fun importFromUrl(url: String): Result<SourceScriptItem> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = httpClient.newCall(req).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP 请求失败: 状态码 ${response.code}"))
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure(Exception("脚本内容为空"))
            }

            return@withContext importFromText(body, sourceUrl = url)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * 从文本内容导入音源脚本
     */
    fun importFromText(
        scriptContent: String,
        customName: String? = null,
        sourceUrl: String? = null
    ): Result<SourceScriptItem> {
        if (scriptContent.isBlank()) {
            return Result.failure(Exception("脚本内容为空"))
        }

        // 解析脚本头部元数据 (如 @name, @version, @author, @description)
        val meta = parseScriptMeta(scriptContent)
        val name = customName?.ifBlank { null } ?: meta.name.ifBlank { "自定义洛雪音源" }

        val id = UUID.randomUUID().toString()
        val newItem = SourceScriptItem(
            id = id,
            name = name,
            version = meta.version.ifBlank { "1.0.0" },
            author = meta.author.ifBlank { "社区开发者" },
            description = meta.description,
            scriptContent = scriptContent,
            sourceUrl = sourceUrl,
            isEnabled = true, // 默认导入后即刻激活该新源
            supportPlatforms = meta.platforms.ifEmpty { listOf("wy", "tx", "kg", "kw", "mg") },
            importedAt = System.currentTimeMillis()
        )

        // 激活当前新导入的脚本，并禁用其它
        val currentList = _scripts.value.map { it.copy(isEnabled = false) }.toMutableList()
        currentList.add(0, newItem)

        _scripts.value = currentList
        _activeScript.value = newItem
        saveConfig()

        // 联动通知 OnlineAudioSourceManager 热加载
        OnlineAudioSourceManager.getInstance(context).loadCustomScript(scriptContent)

        return Result.success(newItem)
    }

    /**
     * 启用指定音源（单选激活）
     */
    fun enableScript(id: String) {
        val updated = _scripts.value.map { item ->
            if (item.id == id) item.copy(isEnabled = true) else item.copy(isEnabled = false)
        }
        _scripts.value = updated
        val active = updated.firstOrNull { it.id == id }
        _activeScript.value = active
        saveConfig()

        if (active != null) {
            OnlineAudioSourceManager.getInstance(context).loadCustomScript(active.scriptContent)
        } else {
            OnlineAudioSourceManager.getInstance(context).clearCustomScript()
        }
    }

    /**
     * 禁用当前音源
     */
    fun disableAllScripts() {
        val updated = _scripts.value.map { it.copy(isEnabled = false) }
        _scripts.value = updated
        _activeScript.value = null
        saveConfig()

        OnlineAudioSourceManager.getInstance(context).clearCustomScript()
    }

    /**
     * 删除指定音源
     */
    fun deleteScript(id: String) {
        val itemToDelete = _scripts.value.firstOrNull { it.id == id }
        val updated = _scripts.value.filter { it.id != id }
        _scripts.value = updated

        if (itemToDelete?.isEnabled == true) {
            val nextActive = updated.firstOrNull()
            if (nextActive != null) {
                enableScript(nextActive.id)
            } else {
                _activeScript.value = null
                OnlineAudioSourceManager.getInstance(context).clearCustomScript()
            }
        }
        saveConfig()
    }

    /**
     * 在线检查更新脚本
     */
    suspend fun updateScript(id: String): Result<SourceScriptItem> = withContext(Dispatchers.IO) {
        val target = _scripts.value.firstOrNull { it.id == id }
            ?: return@withContext Result.failure(Exception("未找到该音源"))

        val url = target.sourceUrl
        if (url.isNullOrBlank()) {
            return@withContext Result.failure(Exception("该音源无在线订阅地址，无法自动更新"))
        }

        try {
            val req = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = httpClient.newCall(req).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP 请求失败: 状态码 ${response.code}"))
            }

            val body = response.body?.string()
            if (body.isNullOrBlank()) {
                return@withContext Result.failure(Exception("更新内容为空"))
            }

            val meta = parseScriptMeta(body)
            val updatedItem = target.copy(
                name = meta.name.ifBlank { target.name },
                version = meta.version.ifBlank { target.version },
                author = meta.author.ifBlank { target.author },
                description = meta.description.ifBlank { target.description },
                scriptContent = body,
                supportPlatforms = meta.platforms.ifEmpty { target.supportPlatforms }
            )

            val list = _scripts.value.map { if (it.id == id) updatedItem else it }
            _scripts.value = list
            if (updatedItem.isEnabled) {
                _activeScript.value = updatedItem
                OnlineAudioSourceManager.getInstance(context).loadCustomScript(body)
            }
            saveConfig()

            return@withContext Result.success(updatedItem)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * 辅助方法：从脚本注释或常量中智能提取元数据
     */
    private fun parseScriptMeta(script: String): ParsedMeta {
        var name = ""
        var version = ""
        var author = ""
        var desc = ""
        val platforms = mutableListOf<String>()

        // 匹配 @name: xxx 或 @name xxx
        val nameMatch = Pattern.compile("@name[:\\s]+([^\\r\\n*]+)").matcher(script)
        if (nameMatch.find()) name = nameMatch.group(1)?.trim() ?: ""

        val verMatch = Pattern.compile("@version[:\\s]+([^\\r\\n*]+)").matcher(script)
        if (verMatch.find()) version = verMatch.group(1)?.trim() ?: ""

        val authorMatch = Pattern.compile("@author[:\\s]+([^\\r\\n*]+)").matcher(script)
        if (authorMatch.find()) author = authorMatch.group(1)?.trim() ?: ""

        val descMatch = Pattern.compile("@description[:\\s]+([^\\r\\n*]+)").matcher(script)
        if (descMatch.find()) desc = descMatch.group(1)?.trim() ?: ""

        // 检查常见平台关键字
        if (script.contains("wy") || script.contains("netease")) platforms.add("wy")
        if (script.contains("tx") || script.contains("qq")) platforms.add("tx")
        if (script.contains("kg") || script.contains("kugou")) platforms.add("kg")
        if (script.contains("kw") || script.contains("kuwo")) platforms.add("kw")
        if (script.contains("mg") || script.contains("migu")) platforms.add("mg")

        return ParsedMeta(name, version, author, desc, platforms)
    }

    private data class ParsedMeta(
        val name: String,
        val version: String,
        val author: String,
        val description: String,
        val platforms: List<String>
    )

    companion object {
        @Volatile
        private var INSTANCE: SourceScriptManager? = null

        fun getInstance(context: Context): SourceScriptManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SourceScriptManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
