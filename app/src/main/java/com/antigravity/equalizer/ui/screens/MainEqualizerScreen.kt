package com.antigravity.equalizer.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.R
import com.antigravity.equalizer.ui.components.*
import com.antigravity.equalizer.data.model.AppScreen
import com.antigravity.equalizer.data.model.Preset
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.viewmodel.EqualizerViewModel

/**
 * 混音台风格全高均衡器主界面
 * 1. 预设栏支持「+ 保存自定义配置」与删除用户预设
 * 2. 10 频段全高推子 (-6dB ~ +6dB 工业黄金行程)
 * 3. Preamp 前级独立增益
 * 4. Bass Boost (低音增强) 与 Treble Boost (高音增强) 拟物双旋转旋钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainEqualizerScreen(
    viewModel: EqualizerViewModel,
    onBackToLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = { viewModel.navigateTo(AppScreen.SETTINGS) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var isPresetDropdownExpanded by remember { mutableStateOf(false) }
    var presetToDelete by remember { mutableStateOf<Preset?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToLibrary) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = OrbitTheme.colors.textPrimary
                        )
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = if (uiState.isEnabled) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.textPrimary
                        )
                    }
                },
                actions = {
                    // 参数均衡器入口
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.PARAMETRIC) }) {
                        Icon(
                            imageVector = Icons.Default.AutoGraph,
                            contentDescription = stringResource(R.string.parametric_eq),
                            tint = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 保存配置按钮
                    IconButton(onClick = {
                        val count = uiState.presets.count { it.isCustom }
                        presetNameInput = context.getString(R.string.my_preset_default_name, count + 1)
                        showSaveDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = stringResource(R.string.preset_save_button),
                            tint = OrbitTheme.colors.primary
                        )
                    }

                    // 设置入口 (包含主题切换、导入导出等)
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 全局电源开关
                    Switch(
                        checked = uiState.isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OrbitTheme.colors.surface,
                            checkedTrackColor = OrbitTheme.colors.primary,
                            uncheckedThumbColor = OrbitTheme.colors.textSecondary,
                            uncheckedTrackColor = OrbitTheme.colors.surface
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = OrbitTheme.colors.background
                )
            )
        },
        containerColor = OrbitTheme.colors.background
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // ========== 专业硬件混音台横屏分栏布局 (Studio Console Split Layout) ==========
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧控制面板：预设选择、前级控制、低音与高音增强旋钮、动态压缩器
                val leftScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .verticalScroll(leftScrollState),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PresetSelectorRow(
                        uiState = uiState,
                        isPresetDropdownExpanded = isPresetDropdownExpanded,
                        onDropdownExpandChange = { isPresetDropdownExpanded = it },
                        onSelectPreset = { viewModel.selectPreset(it) },
                        onDeletePreset = { presetToDelete = it },
                        onOpenSaveDialog = {
                            val count = uiState.presets.count { it.isCustom }
                            presetNameInput = context.getString(R.string.my_preset_default_name, count + 1)
                            showSaveDialog = true
                        }
                    )

                    PreampAndLimiterControl(
                        preampGainDb = uiState.preampGainDb,
                        onPreampGainChanged = { viewModel.setPreampGain(it) },
                        limiterEnabled = uiState.isLimiterEnabled,
                        onLimiterToggle = { viewModel.toggleLimiter(it) },
                        isClipping = uiState.isClipping,
                        isEnabled = uiState.isEnabled
                    )

                    AdvancedDspControls(
                        bassBoostEnabled = uiState.isBassBoostEnabled,
                        bassBoostStrength = uiState.bassBoostStrength,
                        onBassBoostToggle = { viewModel.toggleBassBoost(it) },
                        onBassBoostStrengthChange = { viewModel.setBassBoostStrength(it) },
                        trebleBoostEnabled = uiState.isTrebleBoostEnabled,
                        trebleBoostStrength = uiState.trebleBoostStrength,
                        onTrebleBoostToggle = { viewModel.toggleTrebleBoost(it) },
                        onTrebleBoostStrengthChange = { viewModel.setTrebleBoostStrength(it) },
                        compressorEnabled = uiState.isCompressorEnabled,
                        onCompressorToggle = { viewModel.toggleCompressor(it) },
                        isEnabled = uiState.isEnabled
                    )
                }

                // 右侧：全高 10 频段全景长行程推子区
                EqualizerFadersArea(
                    uiState = uiState,
                    onGainChanged = { index, gain -> viewModel.setBandGain(index, gain) },
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                )
            }
        } else {
            // ========== 标准竖屏布局 ==========
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 预设选择器
                PresetSelectorRow(
                    uiState = uiState,
                    isPresetDropdownExpanded = isPresetDropdownExpanded,
                    onDropdownExpandChange = { isPresetDropdownExpanded = it },
                    onSelectPreset = { viewModel.selectPreset(it) },
                    onDeletePreset = { presetToDelete = it },
                    onOpenSaveDialog = {
                        val count = uiState.presets.count { it.isCustom }
                        presetNameInput = context.getString(R.string.my_preset_default_name, count + 1)
                        showSaveDialog = true
                    }
                )

                // 2. 核心大空间：全高推子区域
                EqualizerFadersArea(
                    uiState = uiState,
                    onGainChanged = { index, gain -> viewModel.setBandGain(index, gain) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                // 3. 前级增益 (Preamp) 与 Limiter
                PreampAndLimiterControl(
                    preampGainDb = uiState.preampGainDb,
                    onPreampGainChanged = { viewModel.setPreampGain(it) },
                    limiterEnabled = uiState.isLimiterEnabled,
                    onLimiterToggle = { viewModel.toggleLimiter(it) },
                    isClipping = uiState.isClipping,
                    isEnabled = uiState.isEnabled
                )

                // 4. Dynamic Bass Boost 与 Treble Boost 旋钮 + Compressor
                AdvancedDspControls(
                    bassBoostEnabled = uiState.isBassBoostEnabled,
                    bassBoostStrength = uiState.bassBoostStrength,
                    onBassBoostToggle = { viewModel.toggleBassBoost(it) },
                    onBassBoostStrengthChange = { viewModel.setBassBoostStrength(it) },
                    trebleBoostEnabled = uiState.isTrebleBoostEnabled,
                    trebleBoostStrength = uiState.trebleBoostStrength,
                    onTrebleBoostToggle = { viewModel.toggleTrebleBoost(it) },
                    onTrebleBoostStrengthChange = { viewModel.setTrebleBoostStrength(it) },
                    compressorEnabled = uiState.isCompressorEnabled,
                    onCompressorToggle = { viewModel.toggleCompressor(it) },
                    isEnabled = uiState.isEnabled
                )
            }
        }

        // 保存自定义配置弹窗
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.preset_save_dialog_title),
                        color = OrbitTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.preset_save_dialog_desc),
                            color = OrbitTheme.colors.textSecondary,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            label = { Text(stringResource(R.string.preset_name_label)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = OrbitTheme.colors.primary,
                                focusedLabelColor = OrbitTheme.colors.primary,
                                cursorColor = OrbitTheme.colors.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveCurrentAsCustomPreset(presetNameInput)
                            showSaveDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                    ) {
                        Text(text = stringResource(R.string.btn_save), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(text = stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                    }
                },
                containerColor = OrbitTheme.colors.surfaceDialog,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 删除自定义配置确认弹窗
        if (presetToDelete != null) {
            val deletingPreset = presetToDelete!!
            AlertDialog(
                onDismissRequest = { presetToDelete = null },
                title = {
                    Text(
                        text = stringResource(R.string.preset_delete_dialog_title),
                        color = OrbitTheme.colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Text(
                        text = stringResource(R.string.preset_delete_dialog_desc, deletingPreset.name),
                        color = OrbitTheme.colors.textSecondary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deleteCustomPreset(deletingPreset.id)
                            presetToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.danger)
                    ) {
                        Text(text = stringResource(R.string.btn_delete), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { presetToDelete = null }) {
                        Text(text = stringResource(R.string.btn_cancel), color = OrbitTheme.colors.textSecondary)
                    }
                },
                containerColor = OrbitTheme.colors.surfaceDialog,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
private fun getLocalizedPresetName(presetId: String, defaultName: String): String {
    return when (presetId) {
        "flat" -> stringResource(R.string.preset_flat)
        "rock" -> stringResource(R.string.preset_rock)
        "pop" -> stringResource(R.string.preset_pop)
        "classical" -> stringResource(R.string.preset_classical)
        "jazz" -> stringResource(R.string.preset_jazz)
        "vocal" -> stringResource(R.string.preset_vocal)
        "bass_boost" -> stringResource(R.string.preset_bass_boost)
        "treble_boost" -> stringResource(R.string.preset_treble_boost)
        else -> defaultName
    }
}

@Composable
private fun PresetSelectorRow(
    uiState: com.antigravity.equalizer.ui.viewmodel.EqualizerUiState,
    isPresetDropdownExpanded: Boolean,
    onDropdownExpandChange: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onDeletePreset: (Preset) -> Unit,
    onOpenSaveDialog: () -> Unit
) {
    val currentPreset = uiState.presets.find { it.id == uiState.selectedPresetId }
    val currentDisplayName = currentPreset?.let { getLocalizedPresetName(it.id, it.name) }
        ?: stringResource(R.string.preset_flat)
    val isCurrentCustom = currentPreset?.isCustom == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 下拉选择卡片
        Box(modifier = Modifier.weight(1f)) {
            Surface(
                onClick = { onDropdownExpandChange(true) },
                shape = RoundedCornerShape(12.dp),
                color = OrbitTheme.colors.surfaceCard,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPresetDropdownExpanded) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = if (isCurrentCustom) Icons.Default.Person else Icons.Default.Tune,
                            contentDescription = null,
                            tint = if (isCurrentCustom) OrbitTheme.colors.secondary else OrbitTheme.colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = currentDisplayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isCurrentCustom) OrbitTheme.colors.secondary.copy(alpha = 0.15f) else OrbitTheme.colors.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (isCurrentCustom) stringResource(R.string.preset_group_custom) else stringResource(R.string.preset_group_builtin),
                                fontSize = 10.sp,
                                color = if (isCurrentCustom) OrbitTheme.colors.secondary else OrbitTheme.colors.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // 分组下拉菜单
            DropdownMenu(
                expanded = isPresetDropdownExpanded,
                onDismissRequest = { onDropdownExpandChange(false) },
                modifier = Modifier
                    .widthIn(min = 240.dp, max = 320.dp)
                    .background(OrbitTheme.colors.surfaceCard)
            ) {
                // 第 1 组：内置预设
                val builtinPresets = uiState.presets.filter { !it.isCustom }
                Text(
                    text = stringResource(R.string.preset_group_builtin),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                builtinPresets.forEach { preset ->
                    val isSelected = preset.id == uiState.selectedPresetId
                    val localizedName = getLocalizedPresetName(preset.id, preset.name)
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = localizedName,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            onSelectPreset(preset.id)
                            onDropdownExpandChange(false)
                        }
                    )
                }

                HorizontalDivider(
                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.15f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // 第 2 组：用户保存配置
                val customPresets = uiState.presets.filter { it.isCustom }
                Text(
                    text = stringResource(R.string.preset_group_custom),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.secondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )

                if (customPresets.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_custom_presets_hint),
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                } else {
                    customPresets.forEach { preset ->
                        val isSelected = preset.id == uiState.selectedPresetId
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = preset.name,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) OrbitTheme.colors.secondary else OrbitTheme.colors.textPrimary,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = OrbitTheme.colors.secondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                        }
                                        IconButton(
                                            onClick = {
                                                onDeletePreset(preset)
                                                onDropdownExpandChange(false)
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringResource(R.string.btn_delete),
                                                tint = OrbitTheme.colors.danger.copy(alpha = 0.8f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (isSelected) OrbitTheme.colors.secondary else OrbitTheme.colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                onSelectPreset(preset.id)
                                onDropdownExpandChange(false)
                            }
                        )
                    }
                }
            }
        }

        // "+ 保存" 快捷按钮
        Button(
            onClick = onOpenSaveDialog,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OrbitTheme.colors.primary
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = if (OrbitTheme.colors.isDark) DarkBackground else Color.White,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_save),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White
            )
        }
    }
}

@Composable
private fun EqualizerFadersArea(
    uiState: com.antigravity.equalizer.ui.viewmodel.EqualizerUiState,
    onGainChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(OrbitTheme.colors.surfaceCard)
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val count = minOf(uiState.frequencies.size, uiState.bandGains.size)
            items(count) { index ->
                BandSlider(
                    frequencyHz = uiState.frequencies[index],
                    gainDb = uiState.bandGains[index],
                    isEnabled = uiState.isEnabled,
                    onGainChanged = { newGain ->
                        onGainChanged(index, newGain)
                    }
                )
            }
        }
    }
}

