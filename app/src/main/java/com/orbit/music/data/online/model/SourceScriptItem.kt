package com.orbit.music.data.online.model

import org.json.JSONObject

/**
 * 第三方洛雪/自定义音源脚本元数据与内容实体
 */
data class SourceScriptItem(
    val id: String,
    val name: String,
    val version: String,
    val author: String = "未知作者",
    val description: String = "",
    val scriptContent: String = "",
    val sourceUrl: String? = null,
    val isEnabled: Boolean = false,
    val supportPlatforms: List<String> = emptyList(), // wy, tx, kg, kw, mg
    val importedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("version", version)
            put("author", author)
            put("description", description)
            put("scriptContent", scriptContent)
            put("sourceUrl", sourceUrl ?: "")
            put("isEnabled", isEnabled)
            put("supportPlatforms", supportPlatforms.joinToString(","))
            put("importedAt", importedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): SourceScriptItem {
            val platformsStr = json.optString("supportPlatforms", "")
            val platforms = if (platformsStr.isNotBlank()) {
                platformsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                emptyList()
            }

            return SourceScriptItem(
                id = json.optString("id", java.util.UUID.randomUUID().toString()),
                name = json.optString("name", "自定义音源"),
                version = json.optString("version", "1.0.0"),
                author = json.optString("author", "未知作者"),
                description = json.optString("description", ""),
                scriptContent = json.optString("scriptContent", ""),
                sourceUrl = json.optString("sourceUrl", "").ifEmpty { null },
                isEnabled = json.optBoolean("isEnabled", false),
                supportPlatforms = platforms,
                importedAt = json.optLong("importedAt", System.currentTimeMillis())
            )
        }
    }
}
