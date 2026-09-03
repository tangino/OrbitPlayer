package com.antigravity.equalizer.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.equalizer.audio.AudioEffectManager
import com.antigravity.equalizer.data.model.*
import com.antigravity.equalizer.data.repository.DeviceProfileRepository
import com.antigravity.equalizer.data.repository.PresetRepository
import com.antigravity.equalizer.native.NativeDSP
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray

data class EqualizerUiState(
    val isEnabled: Boolean = true,
    val currentScreen: AppScreen = AppScreen.LIBRARY,
    val selectedLanguage: String = "system",
    val themeMode: String = "system",
    val sampleRate: Float = 44100f,
    val autoGainEnabled: Boolean = true,
    val autoDeviceProfileEnabled: Boolean = true,
    val numBands: Int = 10,
    val frequencies: FloatArray = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f),
    val bandGains: FloatArray = FloatArray(10) { 0f },
    val parametricBands: List<BandConfig> = listOf(
        BandConfig(0, true, FilterType.LOW_SHELF, 80f, 0f, 0.7f),
        BandConfig(1, true, FilterType.PEAKING, 250f, 0f, 1.0f),
        BandConfig(2, true, FilterType.PEAKING, 1000f, 0f, 1.0f),
        BandConfig(3, true, FilterType.PEAKING, 4000f, 0f, 1.0f),
        BandConfig(4, true, FilterType.HIGH_SHELF, 12000f, 0f, 0.7f)
    ),
    val preampGainDb: Float = 0f,
    val isBassBoostEnabled: Boolean = false,
    val bassBoostStrength: Float = 0.5f,
    val isTrebleBoostEnabled: Boolean = false,
    val trebleBoostStrength: Float = 0.5f,
    val isCompressorEnabled: Boolean = false,
    val isLimiterEnabled: Boolean = true,
    val isClipping: Boolean = false,
    val selectedPresetId: String = "flat",
    val presets: List<Preset> = emptyList(),
    val spectrumBars: FloatArray = FloatArray(32) { 0f },
    val peakLeftDb: Float = -60f,
    val peakRightDb: Float = -60f,
    val activeDeviceName: String = "Phone Speaker",
    val launchAsEqualizerOnly: Boolean = false
)

class EqualizerViewModel(application: Application) : AndroidViewModel(application) {

    private val effectManager = AudioEffectManager.getInstance(application)
    private val nativeDSP = NativeDSP.instance
    private val presetRepository = PresetRepository.getInstance(application)
    private val deviceProfileRepository = DeviceProfileRepository.instance
    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        // 1. 加载已保存的语言与主题配置
        val currentLang = com.antigravity.equalizer.utils.LocaleHelper.getSelectedLanguage(application)
        val savedTheme = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        _uiState.update { it.copy(selectedLanguage = currentLang, themeMode = savedTheme) }

        // 2. 自动恢复上次保存的 UI 状态与均衡器参数
        restoreEqualizerUiState()

        // 3. 加载预设列表
        viewModelScope.launch {
            presetRepository.presets.collect { list ->
                _uiState.update { it.copy(presets = list) }
            }
        }

        // 4. 启动频谱与电平高频采样轮询 (约 30~60 FPS)
        viewModelScope.launch {
            val bars = FloatArray(32)
            while (isActive) {
                if (_uiState.value.isEnabled) {
                    nativeDSP.getSpectrum(bars)
                    val (l, r) = nativeDSP.getPeakLevels()
                    val clipping = nativeDSP.isClipping()

                    _uiState.update { current ->
                        current.copy(
                            spectrumBars = bars.clone(),
                            peakLeftDb = l,
                            peakRightDb = r,
                            isClipping = clipping
                        )
                    }
                }
                delay(33) // ~30 FPS 平滑刷新
            }
        }
    }

    /**
     * 从持久化存储中恢复均衡器全部 UI 状态与参数
     */
    private fun restoreEqualizerUiState() {
        val isEqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, true)
        val selectedPreset = prefs.getString(KEY_SELECTED_PRESET_ID, "flat") ?: "flat"
        val preamp = prefs.getFloat(KEY_PREAMP_GAIN, 0f)
        val bassEnabled = prefs.getBoolean(KEY_BASS_BOOST_ENABLED, false)
        val bassStrength = prefs.getFloat(KEY_BASS_BOOST_STRENGTH, 0.5f)
        val trebleEnabled = prefs.getBoolean(KEY_TREBLE_BOOST_ENABLED, false)
        val trebleStrength = prefs.getFloat(KEY_TREBLE_BOOST_STRENGTH, 0.5f)
        val compressorEnabled = prefs.getBoolean(KEY_COMPRESSOR_ENABLED, false)
        val limiterEnabled = prefs.getBoolean(KEY_LIMITER_ENABLED, true)

        val restoredGains = FloatArray(10) { 0f }
        val gainsJson = prefs.getString(KEY_BAND_GAINS, null)
        if (!gainsJson.isNullOrBlank()) {
            try {
                val array = JSONArray(gainsJson)
                for (i in 0 until minOf(array.length(), 10)) {
                    restoredGains[i] = array.getDouble(i).toFloat()
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        val launchAsEqualizerOnly = prefs.getBoolean(KEY_LAUNCH_AS_EQUALIZER_ONLY, false)
        val initialScreen = if (launchAsEqualizerOnly) AppScreen.MAIN else AppScreen.LIBRARY

        _uiState.update {
            it.copy(
                isEnabled = isEqEnabled,
                currentScreen = initialScreen,
                launchAsEqualizerOnly = launchAsEqualizerOnly,
                selectedPresetId = selectedPreset,
                preampGainDb = preamp,
                bandGains = restoredGains,
                isBassBoostEnabled = bassEnabled,
                bassBoostStrength = bassStrength,
                isTrebleBoostEnabled = trebleEnabled,
                trebleBoostStrength = trebleStrength,
                isCompressorEnabled = compressorEnabled,
                isLimiterEnabled = limiterEnabled
            )
        }

        // 将恢复的音效参数同步到底层硬件与 DSP 链
        effectManager.setEnabled(isEqEnabled)
        effectManager.setPreampGain(preamp)
        restoredGains.forEachIndexed { index, gain ->
            effectManager.setBandGain(index, gain)
        }
        effectManager.setBassBoost(bassEnabled, bassStrength)
        effectManager.setTrebleBoost(trebleEnabled, trebleStrength)
        effectManager.setLimiterEnabled(limiterEnabled)
        effectManager.setCompressorEnabled(compressorEnabled)
    }

    /**
     * 持久化保存当前均衡器全部状态
     */
    private fun saveEqualizerUiState() {
        val s = _uiState.value
        val gainsArray = JSONArray()
        s.bandGains.forEach { gainsArray.put(it.toDouble()) }

        prefs.edit()
            .putBoolean(KEY_EQ_ENABLED, s.isEnabled)
            .putString(KEY_SELECTED_PRESET_ID, s.selectedPresetId)
            .putFloat(KEY_PREAMP_GAIN, s.preampGainDb)
            .putString(KEY_BAND_GAINS, gainsArray.toString())
            .putBoolean(KEY_BASS_BOOST_ENABLED, s.isBassBoostEnabled)
            .putFloat(KEY_BASS_BOOST_STRENGTH, s.bassBoostStrength)
            .putBoolean(KEY_TREBLE_BOOST_ENABLED, s.isTrebleBoostEnabled)
            .putFloat(KEY_TREBLE_BOOST_STRENGTH, s.trebleBoostStrength)
            .putBoolean(KEY_COMPRESSOR_ENABLED, s.isCompressorEnabled)
            .putBoolean(KEY_LIMITER_ENABLED, s.isLimiterEnabled)
            .apply()
    }

    fun toggleEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        effectManager.setEnabled(enabled)
        saveEqualizerUiState()
    }

    fun navigateTo(screen: AppScreen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }

    fun setLanguage(langCode: String) {
        com.antigravity.equalizer.utils.LocaleHelper.setSelectedLanguage(getApplication(), langCode)
        _uiState.update { it.copy(selectedLanguage = langCode) }
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun setSampleRate(sampleRate: Float) {
        _uiState.update { it.copy(sampleRate = sampleRate) }
        nativeDSP.setSampleRate(sampleRate)
    }

    fun toggleAutoGain(enabled: Boolean) {
        _uiState.update { it.copy(autoGainEnabled = enabled) }
    }

    fun toggleAutoDeviceProfile(enabled: Boolean) {
        _uiState.update { it.copy(autoDeviceProfileEnabled = enabled) }
    }

    fun setBandGain(bandIndex: Int, gainDb: Float) {
        val currentGains = _uiState.value.bandGains.clone()
        if (bandIndex in currentGains.indices) {
            currentGains[bandIndex] = gainDb

            val currentPresetId = _uiState.value.selectedPresetId
            val currentPreset = presetRepository.getPresetById(currentPresetId)

            if (currentPreset != null && currentPreset.isCustom) {
                _uiState.update { it.copy(bandGains = currentGains) }
                presetRepository.updateCustomPresetGainsAndPreamp(
                    currentPresetId,
                    currentGains.toList(),
                    _uiState.value.preampGainDb
                )
            } else {
                _uiState.update { it.copy(bandGains = currentGains, selectedPresetId = "custom") }
            }

            effectManager.setBandGain(bandIndex, gainDb)
            saveEqualizerUiState()
        }
    }

    fun setPreampGain(gainDb: Float) {
        _uiState.update { it.copy(preampGainDb = gainDb) }
        effectManager.setPreampGain(gainDb)

        val currentPresetId = _uiState.value.selectedPresetId
        val currentPreset = presetRepository.getPresetById(currentPresetId)
        if (currentPreset != null && currentPreset.isCustom) {
            presetRepository.updateCustomPresetGainsAndPreamp(
                currentPresetId,
                _uiState.value.bandGains.toList(),
                gainDb
            )
        }
        saveEqualizerUiState()
    }

    fun toggleBassBoost(enabled: Boolean) {
        _uiState.update { it.copy(isBassBoostEnabled = enabled) }
        effectManager.setBassBoost(enabled, _uiState.value.bassBoostStrength)
        saveEqualizerUiState()
    }

    fun setBassBoostStrength(strength: Float) {
        _uiState.update { it.copy(bassBoostStrength = strength) }
        effectManager.setBassBoost(_uiState.value.isBassBoostEnabled, strength)
        saveEqualizerUiState()
    }

    fun toggleTrebleBoost(enabled: Boolean) {
        _uiState.update { it.copy(isTrebleBoostEnabled = enabled) }
        effectManager.setTrebleBoost(enabled, _uiState.value.trebleBoostStrength)
        saveEqualizerUiState()
    }

    fun setTrebleBoostStrength(strength: Float) {
        _uiState.update { it.copy(trebleBoostStrength = strength) }
        effectManager.setTrebleBoost(_uiState.value.isTrebleBoostEnabled, strength)
        saveEqualizerUiState()
    }

    fun setNumBands(bands: Int) {
        _uiState.update { it.copy(numBands = bands) }
    }

    // --- Parametric EQ 管理 ---
    fun addParametricBand() {
        val current = _uiState.value.parametricBands.toMutableList()
        val newId = current.size
        current.add(BandConfig(newId, true, FilterType.PEAKING, 1000f, 0f, 1.0f))
        _uiState.update { it.copy(parametricBands = current) }
    }

    fun updateParametricBand(index: Int, band: BandConfig) {
        val current = _uiState.value.parametricBands.toMutableList()
        if (index in current.indices) {
            current[index] = band
            _uiState.update { it.copy(parametricBands = current) }
        }
    }

    fun removeParametricBand(index: Int) {
        val current = _uiState.value.parametricBands.toMutableList()
        if (index in current.indices && current.size > 1) {
            current.removeAt(index)
            _uiState.update { it.copy(parametricBands = current) }
        }
    }

    // --- 预设导入导出 ---
    fun exportPresetsJson(): String {
        return presetRepository.exportPresetsJson()
    }

    fun importPresetText(text: String): Boolean {
        if (text.trim().startsWith("GraphicEQ:")) {
            return presetRepository.importAutoEq("Imported AutoEQ", text) != null
        }
        return presetRepository.importPreset(text) != null
    }

    fun toggleCompressor(enabled: Boolean) {
        _uiState.update { it.copy(isCompressorEnabled = enabled) }
        effectManager.setCompressorEnabled(enabled)
        saveEqualizerUiState()
    }

    fun toggleLimiter(enabled: Boolean) {
        _uiState.update { it.copy(isLimiterEnabled = enabled) }
        effectManager.setLimiterEnabled(enabled)
        saveEqualizerUiState()
    }

    fun selectPreset(presetId: String) {
        applyPreset(presetId)
        saveEqualizerUiState()
    }

    fun applyPreset(presetId: String) {
        val preset = presetRepository.getPresetById(presetId) ?: return
        _uiState.update {
            it.copy(
                selectedPresetId = presetId,
                preampGainDb = preset.preampGainDb,
                bandGains = preset.bands10Gain.toFloatArray()
            )
        }
        effectManager.setPreampGain(preset.preampGainDb)
        preset.bands10Gain.forEachIndexed { index, gain ->
            effectManager.setBandGain(index, gain)
        }
    }

    fun saveCurrentAsCustomPreset(name: String): Preset {
        val count = _uiState.value.presets.count { it.isCustom }
        val finalName = if (name.isBlank()) "Custom Preset ${count + 1}" else name.trim()
        val newPreset = Preset(
            id = "custom_${System.currentTimeMillis()}",
            name = finalName,
            isCustom = true,
            preampGainDb = _uiState.value.preampGainDb,
            bands10Gain = _uiState.value.bandGains.toList()
        )
        presetRepository.addCustomPreset(newPreset)
        _uiState.update { it.copy(selectedPresetId = newPreset.id) }
        saveEqualizerUiState()
        return newPreset
    }

    fun deleteCustomPreset(presetId: String) {
        if (presetRepository.deleteCustomPreset(presetId)) {
            if (_uiState.value.selectedPresetId == presetId) {
                applyPreset("flat")
            }
            saveEqualizerUiState()
        }
    }

    fun resetToFlat() {
        selectPreset("flat")
    }

    fun toggleLaunchAsEqualizerOnly(enabled: Boolean) {
        _uiState.update { it.copy(launchAsEqualizerOnly = enabled) }
        prefs.edit().putBoolean(KEY_LAUNCH_AS_EQUALIZER_ONLY, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "equalizer_ui_state_prefs"
        private const val KEY_EQ_ENABLED = "key_eq_enabled"
        private const val KEY_SELECTED_PRESET_ID = "key_selected_preset_id"
        private const val KEY_PREAMP_GAIN = "key_preamp_gain"
        private const val KEY_BAND_GAINS = "key_band_gains"
        private const val KEY_BASS_BOOST_ENABLED = "key_bass_boost_enabled"
        private const val KEY_BASS_BOOST_STRENGTH = "key_bass_boost_strength"
        private const val KEY_TREBLE_BOOST_ENABLED = "key_treble_boost_enabled"
        private const val KEY_TREBLE_BOOST_STRENGTH = "key_treble_boost_strength"
        private const val KEY_COMPRESSOR_ENABLED = "key_compressor_enabled"
        private const val KEY_LIMITER_ENABLED = "key_limiter_enabled"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_LAUNCH_AS_EQUALIZER_ONLY = "key_launch_as_equalizer_only"
    }
}
