package com.antigravity.equalizer.data.repository

import com.antigravity.equalizer.data.model.FilterType
import com.antigravity.equalizer.data.model.Preset
import org.json.JSONArray
import org.json.JSONObject

/**
 * 负责 EQ 预设的 JSON 序列化、反序列化以及 AutoEQ 格式兼容导入导出
 */
object PresetJsonSerializer {

    /**
     * 将单个预设导出为标准 JSON 字符串
     */
    fun exportPresetToJson(preset: Preset): String {
        val json = JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("isCustom", preset.isCustom)
            put("preampGainDb", preset.preampGainDb.toDouble())
            put("limiterEnabled", preset.limiterEnabled)
            put("limiterThresholdDb", preset.limiterThresholdDb.toDouble())

            val bands10 = JSONArray()
            preset.bands10Gain.forEach { bands10.put(it.toDouble()) }
            put("bands10Gain", bands10)

            preset.bands15Gain?.let { list ->
                val arr = JSONArray()
                list.forEach { arr.put(it.toDouble()) }
                put("bands15Gain", arr)
            }

            preset.bands20Gain?.let { list ->
                val arr = JSONArray()
                list.forEach { arr.put(it.toDouble()) }
                put("bands20Gain", arr)
            }
        }
        return json.toString(2)
    }

    /**
     * 将预设列表批量导出为 JSON 数组
     */
    fun exportPresetsListToJson(presets: List<Preset>): String {
        val arr = JSONArray()
        presets.forEach { preset ->
            arr.put(JSONObject(exportPresetToJson(preset)))
        }
        return arr.toString(2)
    }

    /**
     * 从 JSON 字符串解析单个预设
     */
    fun importPresetFromJson(jsonStr: String): Preset? {
        return try {
            val json = JSONObject(jsonStr)
            val id = json.optString("id", "imported_${System.currentTimeMillis()}")
            val name = json.optString("name", "Imported Preset")
            val isCustom = json.optBoolean("isCustom", true)
            val preampGainDb = json.optDouble("preampGainDb", 0.0).toFloat()
            val limiterEnabled = json.optBoolean("limiterEnabled", true)
            val limiterThresholdDb = json.optDouble("limiterThresholdDb", -0.2).toFloat()

            val bands10List = mutableListOf<Float>()
            val bands10Arr = json.optJSONArray("bands10Gain")
            if (bands10Arr != null) {
                for (i in 0 until bands10Arr.length()) {
                    bands10List.add(bands10Arr.getDouble(i).toFloat())
                }
            } else {
                repeat(10) { bands10List.add(0f) }
            }

            Preset(
                id = id,
                name = name,
                isCustom = isCustom,
                preampGainDb = preampGainDb,
                bands10Gain = bands10List,
                limiterEnabled = limiterEnabled,
                limiterThresholdDb = limiterThresholdDb
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析 AutoEQ 格式文本 (例如: "GraphicEQ: 31.25 0; 62.5 1.5; 125 3.0; ...")
     */
    fun parseAutoEqText(name: String, autoEqText: String): Preset? {
        try {
            val clean = autoEqText.replace("GraphicEQ:", "").trim()
            val pairs = clean.split(";").map { it.trim() }.filter { it.isNotEmpty() }
            if (pairs.size < 5) return null

            // 10 Band 标准频点
            val standardFreqs = listOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
            val gains = FloatArray(10) { 0f }

            val parsedMap = mutableMapOf<Float, Float>()
            for (p in pairs) {
                val parts = p.split(" ").map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 2) {
                    val f = parts[0].toFloatOrNull() ?: continue
                    val g = parts[1].toFloatOrNull() ?: continue
                    parsedMap[f] = g
                }
            }

            for (i in standardFreqs.indices) {
                val targetF = standardFreqs[i]
                // 查找最接近的频率点
                val nearest = parsedMap.minByOrNull { kotlin.math.abs(it.key - targetF) }
                if (nearest != null) {
                    gains[i] = nearest.value
                }
            }

            return Preset(
                id = "autoeq_${System.currentTimeMillis()}",
                name = name.ifBlank { "AutoEQ Preset" },
                isCustom = true,
                preampGainDb = 0f,
                bands10Gain = gains.toList()
            )
        } catch (e: Exception) {
            return null
        }
    }
}
