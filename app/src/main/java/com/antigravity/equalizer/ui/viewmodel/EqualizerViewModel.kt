package com.antigravity.equalizer.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.equalizer.audio.AudioEffectManager
import com.antigravity.equalizer.data.model.*
import java.io.File
import java.io.FileOutputStream
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
    val launchAsEqualizerOnly: Boolean = false,
    val persistentMiniPlayer: Boolean = true,
    val progressTrailStyle: String = ProgressTrailStyle.NEON_PULSE.id,
    val trailStartWidth: Float = 3.8f,
    val trailEndWidth: Float = 1.2f,
    val trailOrbitRadius: Float = 9.5f,
    val trailColor1: Long = 0xFF00FFFFL,
    val trailColor2: Long = 0xFF5E72E4L,
    val visualizerEnabled: Boolean = true,
    val visualizerStyle: VisualizerStyle = VisualizerStyle.BARS_WITH_PEAKS,
    val visualizerPeakDecayEnabled: Boolean = true,
    val visualizerColorScheme: VisualizerColorScheme = VisualizerColorScheme.FOLLOW_THEME,
    val showNowPlayingVisualizer: Boolean = false,
    val visualizerBarWidthDp: Float = 5.0f,
    val visualizerCustomColor: Long = 0xFF00E5FFL,
    val visualizerCustomColor2: Long = 0xFF7C4DFFL,
    val customVisualizerColors: List<Long> = listOf(
        0xFF00E5FFL, 0xFF00F5D4L, 0xFF7C4DFFL, 0xFFFF007FL, 0xFFFF9100L, 0xFF00E676L
    ),
    val isVisualizerMaximized: Boolean = false,
    val maximizedShowCover: Boolean = true,
    val maximizedCoverOnRight: Boolean = false,
    val maximizedShowControls: Boolean = true,
    val maximizedCoverAlpha: Float = 0.85f,
    val showCoverInQueue: Boolean = true,
    val visualizerBarAlpha: Float = 1.0f,
    val customBackgroundPath: String? = null,
    val backgroundBlurRadius: Float = 20f,
    val backgroundBlurStyle: String = "frosted_glass",
    val backgroundDimAlpha: Float = 0.35f,
    val visualizerSingleColor: Boolean = false
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
        // 1. 从持久化配置中恢复全局通用设置
        val savedLang = prefs.getString(KEY_LANGUAGE, "system") ?: "system"
        val savedTheme = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        val savedPersistentMiniPlayer = prefs.getBoolean(KEY_PERSISTENT_MINI_PLAYER, true)
        val savedTrailStyle = prefs.getString(KEY_PROGRESS_TRAIL_STYLE, ProgressTrailStyle.NEON_PULSE.id) ?: ProgressTrailStyle.NEON_PULSE.id
        val savedTrailStartWidth = prefs.getFloat(KEY_TRAIL_START_WIDTH, 3.8f)
        val savedTrailEndWidth = prefs.getFloat(KEY_TRAIL_END_WIDTH, 1.2f)
        val savedTrailOrbitRadius = prefs.getFloat(KEY_TRAIL_ORBIT_RADIUS, 9.5f)
        val savedTrailColor1 = prefs.getLong(KEY_TRAIL_COLOR1, 0xFF00FFFFL)
        val savedTrailColor2 = prefs.getLong(KEY_TRAIL_COLOR2, 0xFF5E72E4L)
        val savedVizEnabled = prefs.getBoolean(KEY_VIZ_ENABLED, true)
        val savedVizStyle = VisualizerStyle.fromId(prefs.getString(KEY_VIZ_STYLE, VisualizerStyle.BARS_WITH_PEAKS.id))
        val savedVizPeakDecay = prefs.getBoolean(KEY_VIZ_PEAK_DECAY, true)
        val savedVizColor = VisualizerColorScheme.fromId(prefs.getString(KEY_VIZ_COLOR, VisualizerColorScheme.FOLLOW_THEME.id))
        val savedShowNowPlayingVisualizer = prefs.getBoolean(KEY_SHOW_NOW_PLAYING_VISUALIZER, false)
        val savedBarWidthDp = prefs.getFloat(KEY_VIZ_BAR_WIDTH_DP, 5.0f)
        val savedCustomColor = prefs.getLong(KEY_VIZ_CUSTOM_COLOR, 0xFF00E5FFL)
        val savedCustomColor2 = prefs.getLong(KEY_VIZ_CUSTOM_COLOR2, 0xFF7C4DFFL)
        val savedCustomColorsJson = prefs.getString(KEY_CUSTOM_VIZ_COLORS, null)
        val savedCustomColors = if (!savedCustomColorsJson.isNullOrBlank()) {
            try {
                val jsonArr = org.json.JSONArray(savedCustomColorsJson)
                List(jsonArr.length()) { idx -> jsonArr.getLong(idx) }
            } catch (e: Exception) {
                listOf(0xFF00E5FFL, 0xFF00F5D4L, 0xFF7C4DFFL, 0xFFFF007FL, 0xFFFF9100L, 0xFF00E676L)
            }
        } else {
            listOf(0xFF00E5FFL, 0xFF00F5D4L, 0xFF7C4DFFL, 0xFFFF007FL, 0xFFFF9100L, 0xFF00E676L)
        }
        val savedIsVisualizerMaximized = prefs.getBoolean(KEY_IS_VISUALIZER_MAXIMIZED, false)
        val savedMaximizedShowCover = prefs.getBoolean(KEY_MAXIMIZED_SHOW_COVER, true)
        val savedMaximizedCoverOnRight = prefs.getBoolean(KEY_MAXIMIZED_COVER_ON_RIGHT, false)
        val savedMaximizedShowControls = prefs.getBoolean(KEY_MAXIMIZED_SHOW_CONTROLS, true)
        val savedMaximizedCoverAlpha = prefs.getFloat(KEY_MAXIMIZED_COVER_ALPHA, 0.85f)
        val savedShowCoverInQueue = prefs.getBoolean(KEY_SHOW_COVER_IN_QUEUE, true)
        val savedVizBarAlpha = prefs.getFloat(KEY_VIZ_BAR_ALPHA, 1.0f)
        val savedCustomBgPath = prefs.getString(KEY_CUSTOM_BG_PATH, null)?.let { path ->
            if (File(path).exists()) path else null
        }
        val savedBgBlurRadius = prefs.getFloat(KEY_BG_BLUR_RADIUS, 20f)
        val savedBgBlurStyle = prefs.getString(KEY_BG_BLUR_STYLE, "frosted_glass") ?: "frosted_glass"
        val savedBgDimAlpha = prefs.getFloat(KEY_BG_DIM_ALPHA, 0.35f)
        val savedVizSingleColor = prefs.getBoolean(KEY_VIZ_SINGLE_COLOR, false)

        _uiState.update {
            it.copy(
                selectedLanguage = savedLang,
                themeMode = savedTheme,
                persistentMiniPlayer = savedPersistentMiniPlayer,
                progressTrailStyle = savedTrailStyle,
                trailStartWidth = savedTrailStartWidth,
                trailEndWidth = savedTrailEndWidth,
                trailOrbitRadius = savedTrailOrbitRadius,
                trailColor1 = savedTrailColor1,
                trailColor2 = savedTrailColor2,
                visualizerEnabled = savedVizEnabled,
                visualizerStyle = savedVizStyle,
                visualizerPeakDecayEnabled = savedVizPeakDecay,
                visualizerColorScheme = savedVizColor,
                showNowPlayingVisualizer = savedShowNowPlayingVisualizer,
                visualizerBarWidthDp = savedBarWidthDp,
                visualizerCustomColor = savedCustomColor,
                visualizerCustomColor2 = savedCustomColor2,
                customVisualizerColors = savedCustomColors,
                isVisualizerMaximized = savedIsVisualizerMaximized,
                maximizedShowCover = savedMaximizedShowCover,
                maximizedCoverOnRight = savedMaximizedCoverOnRight,
                maximizedShowControls = savedMaximizedShowControls,
                maximizedCoverAlpha = savedMaximizedCoverAlpha,
                showCoverInQueue = savedShowCoverInQueue,
                visualizerBarAlpha = savedVizBarAlpha,
                customBackgroundPath = savedCustomBgPath,
                backgroundBlurRadius = savedBgBlurRadius,
                backgroundBlurStyle = savedBgBlurStyle,
                backgroundDimAlpha = savedBgDimAlpha,
                visualizerSingleColor = savedVizSingleColor
            )
        }

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

    fun setProgressTrailStyle(style: String) {
        prefs.edit().putString(KEY_PROGRESS_TRAIL_STYLE, style).apply()
        _uiState.update { it.copy(progressTrailStyle = style) }
    }

    fun setTrailStartWidth(width: Float) {
        prefs.edit().putFloat(KEY_TRAIL_START_WIDTH, width).apply()
        _uiState.update { it.copy(trailStartWidth = width) }
    }

    fun setTrailEndWidth(width: Float) {
        prefs.edit().putFloat(KEY_TRAIL_END_WIDTH, width).apply()
        _uiState.update { it.copy(trailEndWidth = width) }
    }

    fun setTrailOrbitRadius(radius: Float) {
        prefs.edit().putFloat(KEY_TRAIL_ORBIT_RADIUS, radius).apply()
        _uiState.update { it.copy(trailOrbitRadius = radius) }
    }

    fun setTrailColor1(color: Long) {
        prefs.edit().putLong(KEY_TRAIL_COLOR1, color).apply()
        _uiState.update { it.copy(trailColor1 = color) }
    }

    fun setTrailColor2(color: Long) {
        prefs.edit().putLong(KEY_TRAIL_COLOR2, color).apply()
        _uiState.update { it.copy(trailColor2 = color) }
    }

    fun resetTrailSettings() {
        prefs.edit()
            .putFloat(KEY_TRAIL_START_WIDTH, 3.8f)
            .putFloat(KEY_TRAIL_END_WIDTH, 1.2f)
            .putFloat(KEY_TRAIL_ORBIT_RADIUS, 9.5f)
            .putLong(KEY_TRAIL_COLOR1, 0xFF00FFFFL)
            .putLong(KEY_TRAIL_COLOR2, 0xFF5E72E4L)
            .apply()
        _uiState.update {
            it.copy(
                trailStartWidth = 3.8f,
                trailEndWidth = 1.2f,
                trailOrbitRadius = 9.5f,
                trailColor1 = 0xFF00FFFFL,
                trailColor2 = 0xFF5E72E4L
            )
        }
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

    fun togglePersistentMiniPlayer(enabled: Boolean) {
        _uiState.update { it.copy(persistentMiniPlayer = enabled) }
        prefs.edit().putBoolean(KEY_PERSISTENT_MINI_PLAYER, enabled).apply()
    }

    fun toggleVisualizerEnabled(enabled: Boolean) {
        _uiState.update { it.copy(visualizerEnabled = enabled) }
        prefs.edit().putBoolean(KEY_VIZ_ENABLED, enabled).apply()
    }

    fun setVisualizerStyle(style: VisualizerStyle) {
        _uiState.update { it.copy(visualizerStyle = style) }
        prefs.edit().putString(KEY_VIZ_STYLE, style.id).apply()
    }

    fun cycleVisualizerStyle() {
        val styles = VisualizerStyle.values()
        val currentIndex = styles.indexOf(_uiState.value.visualizerStyle)
        val nextStyle = styles[(currentIndex + 1) % styles.size]
        setVisualizerStyle(nextStyle)
    }

    fun toggleVisualizerPeakDecay(enabled: Boolean) {
        _uiState.update { it.copy(visualizerPeakDecayEnabled = enabled) }
        prefs.edit().putBoolean(KEY_VIZ_PEAK_DECAY, enabled).apply()
    }

    fun setVisualizerColorScheme(scheme: VisualizerColorScheme) {
        _uiState.update { it.copy(visualizerColorScheme = scheme) }
        prefs.edit().putString(KEY_VIZ_COLOR, scheme.id).apply()
    }

    fun setShowNowPlayingVisualizer(show: Boolean) {
        _uiState.update { it.copy(showNowPlayingVisualizer = show) }
        prefs.edit().putBoolean(KEY_SHOW_NOW_PLAYING_VISUALIZER, show).apply()
    }

    fun setVisualizerBarWidth(widthDp: Float) {
        _uiState.update { it.copy(visualizerBarWidthDp = widthDp) }
        prefs.edit().putFloat(KEY_VIZ_BAR_WIDTH_DP, widthDp).apply()
    }

    fun setVisualizerCustomColor(colorLong: Long) {
        _uiState.update { it.copy(visualizerCustomColor = colorLong, visualizerColorScheme = VisualizerColorScheme.CUSTOM) }
        prefs.edit()
            .putLong(KEY_VIZ_CUSTOM_COLOR, colorLong)
            .putString(KEY_VIZ_COLOR, VisualizerColorScheme.CUSTOM.id)
            .apply()
    }

    fun setVisualizerCustomColor2(colorLong: Long) {
        _uiState.update { it.copy(visualizerCustomColor2 = colorLong, visualizerColorScheme = VisualizerColorScheme.CUSTOM) }
        prefs.edit()
            .putLong(KEY_VIZ_CUSTOM_COLOR2, colorLong)
            .putString(KEY_VIZ_COLOR, VisualizerColorScheme.CUSTOM.id)
            .apply()
    }

    fun addCustomVisualizerColor(colorLong: Long) {
        val current = _uiState.value.customVisualizerColors.toMutableList()
        if (!current.contains(colorLong)) {
            current.add(0, colorLong)
            val jsonArr = org.json.JSONArray()
            current.forEach { jsonArr.put(it) }
            prefs.edit().putString(KEY_CUSTOM_VIZ_COLORS, jsonArr.toString()).apply()
            _uiState.update { it.copy(customVisualizerColors = current) }
        }
    }

    fun removeCustomVisualizerColor(colorLong: Long) {
        val current = _uiState.value.customVisualizerColors.toMutableList()
        if (current.remove(colorLong)) {
            val jsonArr = org.json.JSONArray()
            current.forEach { jsonArr.put(it) }
            prefs.edit().putString(KEY_CUSTOM_VIZ_COLORS, jsonArr.toString()).apply()
            _uiState.update { it.copy(customVisualizerColors = current) }
        }
    }

    fun setVisualizerMaximized(maximized: Boolean) {
        _uiState.update { it.copy(isVisualizerMaximized = maximized) }
        prefs.edit().putBoolean(KEY_IS_VISUALIZER_MAXIMIZED, maximized).apply()
    }

    fun setMaximizedShowCover(show: Boolean) {
        _uiState.update { it.copy(maximizedShowCover = show) }
        prefs.edit().putBoolean(KEY_MAXIMIZED_SHOW_COVER, show).apply()
    }

    fun setMaximizedCoverOnRight(onRight: Boolean) {
        _uiState.update { it.copy(maximizedCoverOnRight = onRight) }
        prefs.edit().putBoolean(KEY_MAXIMIZED_COVER_ON_RIGHT, onRight).apply()
    }

    fun setMaximizedShowControls(show: Boolean) {
        _uiState.update { it.copy(maximizedShowControls = show) }
        prefs.edit().putBoolean(KEY_MAXIMIZED_SHOW_CONTROLS, show).apply()
    }

    fun setMaximizedCoverAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(maximizedCoverAlpha = clamped) }
        prefs.edit().putFloat(KEY_MAXIMIZED_COVER_ALPHA, clamped).apply()
    }

    fun setShowCoverInQueue(show: Boolean) {
        _uiState.update { it.copy(showCoverInQueue = show) }
        prefs.edit().putBoolean(KEY_SHOW_COVER_IN_QUEUE, show).apply()
    }

    fun setVisualizerBarAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.1f, 1.0f)
        _uiState.update { it.copy(visualizerBarAlpha = clamped) }
        prefs.edit().putFloat(KEY_VIZ_BAR_ALPHA, clamped).apply()
    }

    fun setCustomBackgroundFromUri(uri: Uri, context: Context): Boolean {
        return try {
            val destFile = File(context.filesDir, "custom_app_background.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            val absPath = destFile.absolutePath
            _uiState.update { it.copy(customBackgroundPath = absPath) }
            prefs.edit().putString(KEY_CUSTOM_BG_PATH, absPath).apply()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearCustomBackground(context: Context) {
        try {
            val destFile = File(context.filesDir, "custom_app_background.jpg")
            if (destFile.exists()) {
                destFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _uiState.update { it.copy(customBackgroundPath = null) }
        prefs.edit().remove(KEY_CUSTOM_BG_PATH).apply()
    }

    fun setBackgroundBlurRadius(radius: Float) {
        val clamped = radius.coerceIn(0f, 50f)
        _uiState.update { it.copy(backgroundBlurRadius = clamped) }
        prefs.edit().putFloat(KEY_BG_BLUR_RADIUS, clamped).apply()
    }

    fun setBackgroundBlurStyle(style: String) {
        _uiState.update { it.copy(backgroundBlurStyle = style) }
        prefs.edit().putString(KEY_BG_BLUR_STYLE, style).apply()
    }

    fun setBackgroundDimAlpha(dim: Float) {
        val clamped = dim.coerceIn(0.0f, 0.85f)
        _uiState.update { it.copy(backgroundDimAlpha = clamped) }
        prefs.edit().putFloat(KEY_BG_DIM_ALPHA, clamped).apply()
    }

    fun setVisualizerSingleColor(enabled: Boolean) {
        _uiState.update { it.copy(visualizerSingleColor = enabled) }
        prefs.edit().putBoolean(KEY_VIZ_SINGLE_COLOR, enabled).apply()
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
        private const val KEY_LANGUAGE = "key_language"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_LAUNCH_AS_EQUALIZER_ONLY = "key_launch_as_equalizer_only"
        private const val KEY_PERSISTENT_MINI_PLAYER = "key_persistent_mini_player"
        private const val KEY_PROGRESS_TRAIL_STYLE = "key_progress_trail_style"
        private const val KEY_TRAIL_START_WIDTH = "key_trail_start_width"
        private const val KEY_TRAIL_END_WIDTH = "key_trail_end_width"
        private const val KEY_TRAIL_ORBIT_RADIUS = "key_trail_orbit_radius"
        private const val KEY_TRAIL_COLOR1 = "key_trail_color1"
        private const val KEY_TRAIL_COLOR2 = "key_trail_color2"
        private const val KEY_VIZ_ENABLED = "key_viz_enabled"
        private const val KEY_VIZ_STYLE = "key_viz_style"
        private const val KEY_VIZ_PEAK_DECAY = "key_viz_peak_decay"
        private const val KEY_VIZ_COLOR = "key_viz_color"
        private const val KEY_SHOW_NOW_PLAYING_VISUALIZER = "key_show_now_playing_visualizer"
        private const val KEY_VIZ_BAR_WIDTH_DP = "key_viz_bar_width_dp"
        private const val KEY_VIZ_CUSTOM_COLOR = "key_viz_custom_color"
        private const val KEY_VIZ_CUSTOM_COLOR2 = "key_viz_custom_color2"
        private const val KEY_CUSTOM_VIZ_COLORS = "key_custom_viz_colors"
        private const val KEY_MAXIMIZED_SHOW_COVER = "key_maximized_show_cover"
        private const val KEY_MAXIMIZED_COVER_ON_RIGHT = "key_maximized_cover_on_right"
        private const val KEY_MAXIMIZED_SHOW_CONTROLS = "key_maximized_show_controls"
        private const val KEY_MAXIMIZED_COVER_ALPHA = "key_maximized_cover_alpha"
        private const val KEY_IS_VISUALIZER_MAXIMIZED = "key_is_visualizer_maximized"
        private const val KEY_SHOW_COVER_IN_QUEUE = "key_show_cover_in_queue"
        private const val KEY_VIZ_BAR_ALPHA = "key_viz_bar_alpha"
        private const val KEY_CUSTOM_BG_PATH = "key_custom_bg_path"
        private const val KEY_BG_BLUR_RADIUS = "key_bg_blur_radius"
        private const val KEY_BG_BLUR_STYLE = "key_bg_blur_style"
        private const val KEY_BG_DIM_ALPHA = "key_bg_dim_alpha"
        private const val KEY_VIZ_SINGLE_COLOR = "key_viz_single_color"
    }
}
