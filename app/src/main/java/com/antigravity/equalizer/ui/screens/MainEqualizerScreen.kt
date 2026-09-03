package com.antigravity.equalizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.R
import com.antigravity.equalizer.ui.components.*
import com.antigravity.equalizer.data.model.AppScreen
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
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToLibrary) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
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
                            contentDescription = "参数均衡器",
                            tint = OrbitTheme.colors.textPrimary
                        )
                    }

                    // 保存配置按钮
                    IconButton(onClick = {
                        val count = uiState.presets.count { it.isCustom }
                        presetNameInput = "我的调音 ${count + 1}"
                        showSaveDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "保存配置",
                            tint = OrbitTheme.colors.primary
                        )
                    }

                    // 设置入口 (包含主题切换、导入导出等)
                    IconButton(onClick = { viewModel.navigateTo(AppScreen.SETTINGS) }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置",
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
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. 预设选择器 (Presets Row，支持 + 保存自定义配置 与 删除)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "+ 保存" 快捷芯片按钮
                SuggestionChip(
                    onClick = {
                        val count = uiState.presets.count { it.isCustom }
                        presetNameInput = "我的调音 ${count + 1}"
                        showSaveDialog = true
                    },
                    label = {
                        Text(
                            text = "+ 保存配置",
                            fontWeight = FontWeight.Bold,
                            color = OrbitTheme.colors.primary,
                            fontSize = 12.sp
                        )
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = OrbitTheme.colors.primary.copy(alpha = 0.15f)
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = OrbitTheme.colors.primary.copy(alpha = 0.5f)
                    )
                )

                uiState.presets.forEach { preset ->
                    val isSelected = uiState.selectedPresetId == preset.id
                    val displayName = getLocalizedPresetName(preset.id, preset.name)
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectPreset(preset.id) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) (if (OrbitTheme.colors.isDark) DarkBackground else Color.White) else OrbitTheme.colors.textPrimary
                                )
                                if (preset.isCustom) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除",
                                        tint = if (isSelected) (if (OrbitTheme.colors.isDark) DarkBackground.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.7f)) else OrbitTheme.colors.textSecondary,
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.deleteCustomPreset(preset.id) }
                                    )
                                }
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OrbitTheme.colors.primary,
                            containerColor = OrbitTheme.colors.surfaceCard
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.gridLine
                        )
                    )
                }
            }

            // 2. 核心大空间：加长全高均衡器推子区域 (Professional Long-Throw Equalizer Faders)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(OrbitTheme.colors.surfaceCard)
                    .padding(vertical = 16.dp, horizontal = 8.dp)
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
                                viewModel.setBandGain(index, newGain)
                            }
                        )
                    }
                }
            }

            // 3. 前级增益 (Preamp) 独立控制条
            PreampAndLimiterControl(
                preampGainDb = uiState.preampGainDb,
                onPreampGainChanged = { viewModel.setPreampGain(it) },
                limiterEnabled = uiState.isLimiterEnabled,
                onLimiterToggle = { viewModel.toggleLimiter(it) },
                isClipping = uiState.isClipping,
                isEnabled = uiState.isEnabled
            )

            // 4. Dynamic Bass Boost 与 Treble Boost 旋钮控制区 + Compressor
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

        // 保存自定义配置弹窗
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = {
                    Text(
                        text = "保存当前均衡器配置",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "将当前的 10 频段增益与前级增益参数保存为专属预设：",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            label = { Text("预设名称") },
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
                        Text(text = "保存", color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(text = "取消", color = OrbitTheme.colors.textSecondary)
                    }
                },
                containerColor = OrbitTheme.colors.surfaceCard,
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
