package com.antigravity.equalizer.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.antigravity.equalizer.data.model.Preset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PresetRepository(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val defaultPresets = listOf(
        Preset(
            id = "flat",
            name = "Flat",
            preampGainDb = 0f,
            bands10Gain = listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        ),
        Preset(
            id = "rock",
            name = "Rock",
            preampGainDb = 0f,
            bands10Gain = listOf(3.5f, 2.5f, 1.5f, 0.5f, -0.5f, 0.0f, 1.0f, 2.0f, 3.0f, 3.5f)
        ),
        Preset(
            id = "pop",
            name = "Pop",
            preampGainDb = 0f,
            bands10Gain = listOf(-0.5f, 1.0f, 2.0f, 2.5f, 2.0f, 1.0f, 0.5f, 1.0f, 1.5f, 2.0f)
        ),
        Preset(
            id = "classical",
            name = "Classical",
            preampGainDb = 0f,
            bands10Gain = listOf(2.5f, 2.0f, 1.5f, 1.0f, -0.5f, -0.5f, 0f, 1.0f, 2.0f, 2.5f)
        ),
        Preset(
            id = "jazz",
            name = "Jazz",
            preampGainDb = 0f,
            bands10Gain = listOf(2.0f, 1.5f, 1.0f, 1.0f, -1.0f, -1.0f, 0f, 1.0f, 2.0f, 2.5f)
        ),
        Preset(
            id = "vocal",
            name = "Vocal",
            preampGainDb = 0f,
            bands10Gain = listOf(-1.5f, -0.5f, 0f, 1.5f, 3.0f, 3.5f, 2.5f, 1.5f, 0.5f, -0.5f)
        ),
        Preset(
            id = "bass_boost",
            name = "Bass Boost",
            preampGainDb = 0f,
            bands10Gain = listOf(5.0f, 4.0f, 3.0f, 1.5f, 0.5f, 0f, 0f, 0f, 0f, 0f)
        ),
        Preset(
            id = "treble_boost",
            name = "Treble Boost",
            preampGainDb = 0f,
            bands10Gain = listOf(0f, 0f, 0f, 0f, 0f, 0.5f, 1.5f, 3.0f, 4.5f, 5.5f)
        )
    )

    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    init {
        loadAllPresets()
    }

    private fun loadAllPresets() {
        val list = defaultPresets.toMutableList()
        val customJson = prefs?.getString(KEY_CUSTOM_PRESETS, null)
        if (!customJson.isNullOrBlank()) {
            try {
                val array = JSONArray(customJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.getString("id")
                    val name = obj.getString("name")
                    val preamp = obj.optDouble("preamp", 0.0).toFloat()
                    val gainsArray = obj.getJSONArray("gains")
                    val gains = mutableListOf<Float>()
                    for (j in 0 until gainsArray.length()) {
                        gains.add(gainsArray.getDouble(j).toFloat())
                    }
                    list.add(
                        Preset(
                            id = id,
                            name = name,
                            isCustom = true,
                            preampGainDb = preamp,
                            bands10Gain = gains
                        )
                    )
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        _presets.value = list
    }

    private fun saveCustomPresets() {
        val customList = _presets.value.filter { it.isCustom }
        try {
            val array = JSONArray()
            for (p in customList) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("preamp", p.preampGainDb.toDouble())
                    val gainsArray = JSONArray()
                    p.bands10Gain.forEach { gainsArray.put(it.toDouble()) }
                    put("gains", gainsArray)
                }
                array.put(obj)
            }
            prefs?.edit()?.putString(KEY_CUSTOM_PRESETS, array.toString())?.apply()
        } catch (e: Exception) {
            // ignore
        }
    }

    fun getPresetById(id: String): Preset? {
        return _presets.value.find { it.id == id }
    }

    fun addCustomPreset(preset: Preset) {
        val current = _presets.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == preset.id }
        if (existingIndex >= 0) {
            current[existingIndex] = preset
        } else {
            current.add(preset)
        }
        _presets.value = current
        saveCustomPresets()
    }

    fun updateCustomPresetGainsAndPreamp(presetId: String, gains: List<Float>, preampGainDb: Float): Boolean {
        val current = _presets.value.toMutableList()
        val index = current.indexOfFirst { it.id == presetId && it.isCustom }
        if (index >= 0) {
            val old = current[index]
            current[index] = old.copy(
                bands10Gain = gains,
                preampGainDb = preampGainDb
            )
            _presets.value = current
            saveCustomPresets()
            return true
        }
        return false
    }

    fun deleteCustomPreset(presetId: String): Boolean {
        val current = _presets.value.toMutableList()
        val target = current.find { it.id == presetId && it.isCustom }
        if (target != null) {
            current.remove(target)
            _presets.value = current
            saveCustomPresets()
            return true
        }
        return false
    }

    fun exportPresetsJson(): String {
        return PresetJsonSerializer.exportPresetsListToJson(_presets.value)
    }

    fun importPreset(jsonStr: String): Preset? {
        val preset = PresetJsonSerializer.importPresetFromJson(jsonStr)
        if (preset != null) {
            addCustomPreset(preset)
        }
        return preset
    }

    fun importAutoEq(name: String, autoEqText: String): Preset? {
        val preset = PresetJsonSerializer.parseAutoEqText(name, autoEqText)
        if (preset != null) {
            addCustomPreset(preset)
        }
        return preset
    }

    companion object {
        private const val PREFS_NAME = "equalizer_custom_presets_prefs"
        private const val KEY_CUSTOM_PRESETS = "key_custom_presets_json"

        @Volatile
        private var INSTANCE: PresetRepository? = null

        fun getInstance(context: Context? = null): PresetRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PresetRepository(context).also { INSTANCE = it }
            }
        }

        val instance: PresetRepository
            get() = getInstance()
    }
}
