package com.orbit.music.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.music.R
import com.orbit.music.data.model.AppScreen
import com.orbit.music.data.model.Preset
import com.orbit.music.ui.components.*
import com.orbit.music.ui.theme.OrbitTheme
import com.orbit.music.ui.viewmodel.EqualizerViewModel

/**
 * 融入 OrbitPlayer 全局设计语言的沉浸式硬件控制台均衡器
 * 1. 顶部：最左侧独立「增益 (Preamp)」推子卡片 + 10 频段全高推子
 * 2. 中间：高保真频响曲线与频谱分析面板 (移除中间繁冗标签)
 * 3. 底部：整洁对称的结构化控制栏 (均衡器/压限器/预设/更多) 与拟物低音/高音旋钮
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainEqualizerScreen(
    viewModel: EqualizerViewModel,
    onBackToLibrary: () -> Unit = {},
    onOpenSettings: () -> Unit = { viewModel.navigateTo(AppScreen.SETTINGS) },
    modifier: Modifier = Modifier
) {
    val colors = OrbitTheme.colors
    val uiState by viewModel.uiState.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var isPresetDropdownExpanded by remember { mutableStateOf(false) }
    var isMoreMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBackToLibrary) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = colors.textPrimary
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
                            tint = if (uiState.isEnabled) colors.primary else colors.textSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                    }
                },
                actions = {
                    // 全局电源开关
                    Switch(
                        checked = uiState.isEnabled,
                        onCheckedChange = { viewModel.toggleEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.surfaceCard,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textSecondary,
                            uncheckedTrackColor = colors.surface
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background
                )
            )
        },
        containerColor = colors.background
    ) { innerPadding ->
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE ||
                configuration.screenWidthDp > configuration.screenHeightDp

        if (isLandscape) {
            // 横屏布局
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 左侧控制区
                Column(
                    modifier = Modifier
                        .weight(0.40f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PowerampVisualizerBar(
                        frequencies = uiState.frequencies,
                        gainsDb = uiState.bandGains,
                        spectrumBars = uiState.spectrumBars,
                        spectrumPeaks = uiState.spectrumPeaks,
                        visualizerEnabled = uiState.visualizerEnabled
                    )

                    // 旋钮区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        DspRotaryKnob(
                            title = "低音",
                            strength = uiState.bassBoostStrength,
                            enabled = uiState.isEnabled,
                            onStrengthChanged = {
                                viewModel.toggleBassBoost(it > 0.001f)
                                viewModel.setBassBoostStrength(it)
                            }
                        )

                        DspRotaryKnob(
                            title = "高音",
                            strength = uiState.trebleBoostStrength,
                            enabled = uiState.isEnabled,
                            onStrengthChanged = {
                                viewModel.toggleTrebleBoost(it > 0.001f)
                                viewModel.setTrebleBoostStrength(it)
                            }
                        )
                    }
                }

                // 右侧推子区
                Row(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PreampFader(
                        preampGainDb = uiState.preampGainDb,
                        isEnabled = uiState.isEnabled,
                        onGainChanged = { viewModel.setPreampGain(it) }
                    )

                    EqualizerFadersRow(
                        uiState = uiState,
                        onGainChanged = { index, gain -> viewModel.setBandGain(index, gain) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            // 优雅整洁的竖屏控制台界面
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. 顶部推子区域：最左侧「增益」+ 右侧 10 频段全景推子
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PreampFader(
                        preampGainDb = uiState.preampGainDb,
                        isEnabled = uiState.isEnabled,
                        onGainChanged = { viewModel.setPreampGain(it) }
                    )

                    EqualizerFadersRow(
                        uiState = uiState,
                        onGainChanged = { index, gain -> viewModel.setBandGain(index, gain) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // 2. 中间纯净频响可视化卡片 (带跳动频谱与幅频曲线)
                PowerampVisualizerBar(
                    frequencies = uiState.frequencies,
                    gainsDb = uiState.bandGains,
                    spectrumBars = uiState.spectrumBars,
                    spectrumPeaks = uiState.spectrumPeaks,
                    visualizerEnabled = uiState.visualizerEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. 底部结构化操作栏 (对齐、对称、统一高度的秩序感布局)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：均衡器与压限器独立控制按钮 (各自拥有完整圆角与边框，彻底消除中间缝隙暗线)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 【均衡器】独立胶囊按钮
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (uiState.isEnabled) colors.primary.copy(alpha = 0.16f) else colors.surfaceCard)
                                .border(
                                    0.8.dp,
                                    if (uiState.isEnabled) colors.primary.copy(alpha = 0.40f) else colors.surfaceBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.toggleEnabled(!uiState.isEnabled) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (uiState.isEnabled) colors.primary else colors.textSecondary.copy(alpha = 0.4f))
                                )
                                Text(
                                    text = "均衡器",
                                    fontSize = 12.sp,
                                    fontWeight = if (uiState.isEnabled) FontWeight.Bold else FontWeight.Medium,
                                    color = if (uiState.isEnabled) colors.primary else colors.textSecondary
                                )
                            }
                        }

                        // 【压限器】独立胶囊按钮
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (uiState.isLimiterEnabled) colors.primary.copy(alpha = 0.16f) else colors.surfaceCard)
                                .border(
                                    0.8.dp,
                                    if (uiState.isLimiterEnabled) colors.primary.copy(alpha = 0.40f) else colors.surfaceBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewModel.toggleLimiter(!uiState.isLimiterEnabled) }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (uiState.isLimiterEnabled) colors.primary else colors.textSecondary.copy(alpha = 0.4f))
                                )
                                Text(
                                    text = "压限器",
                                    fontSize = 12.sp,
                                    fontWeight = if (uiState.isLimiterEnabled) FontWeight.Bold else FontWeight.Medium,
                                    color = if (uiState.isLimiterEnabled) colors.primary else colors.textSecondary
                                )
                            }
                        }
                    }

                    // 中间：当前预设选择胶囊卡片
                    val currentPreset = uiState.presets.find { it.id == uiState.selectedPresetId }
                    val currentName = currentPreset?.name ?: "平直"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surfaceCard)
                            .border(0.8.dp, colors.surfaceBorder, RoundedCornerShape(12.dp))
                            .clickable { isPresetDropdownExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = currentName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                        }

                        // 预设选择菜单
                        DropdownMenu(
                            expanded = isPresetDropdownExpanded,
                            onDismissRequest = { isPresetDropdownExpanded = false },
                            modifier = Modifier
                                .widthIn(min = 220.dp, max = 280.dp)
                                .background(colors.surfaceCard)
                        ) {
                            val builtinPresets = uiState.presets.filter { !it.isCustom }
                            Text(
                                text = "系统预设",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )

                            builtinPresets.forEach { preset ->
                                val isSelected = preset.id == uiState.selectedPresetId
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = preset.name,
                                            color = if (isSelected) colors.primary else colors.textPrimary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, null, tint = colors.primary, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    onClick = {
                                        viewModel.selectPreset(preset.id)
                                        isPresetDropdownExpanded = false
                                    }
                                )
                            }

                            val customPresets = uiState.presets.filter { it.isCustom }
                            if (customPresets.isNotEmpty()) {
                                HorizontalDivider(color = colors.surfaceBorder)
                                Text(
                                    text = "自定义预设",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.secondary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                customPresets.forEach { preset ->
                                    val isSelected = preset.id == uiState.selectedPresetId
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = preset.name,
                                                color = if (isSelected) colors.secondary else colors.textPrimary
                                            )
                                        },
                                        onClick = {
                                            viewModel.selectPreset(preset.id)
                                            isPresetDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 右侧：辅助功能按钮 (声波动效切换 + 更多菜单)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 动效频谱按钮
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (uiState.visualizerEnabled) colors.primary.copy(alpha = 0.18f) else colors.surfaceCard)
                                .border(0.8.dp, colors.surfaceBorder, RoundedCornerShape(10.dp))
                                .clickable { viewModel.toggleVisualizerEnabled(!uiState.visualizerEnabled) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = if (uiState.visualizerEnabled) colors.primary else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // 更多功能菜单
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceCard)
                                    .border(0.8.dp, colors.surfaceBorder, RoundedCornerShape(10.dp))
                                    .clickable { isMoreMenuExpanded = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = null,
                                    tint = colors.textPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = isMoreMenuExpanded,
                                onDismissRequest = { isMoreMenuExpanded = false },
                                modifier = Modifier.background(colors.surfaceCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("保存当前配置为预设", color = colors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Save, null, tint = colors.primary) },
                                    onClick = {
                                        val count = uiState.presets.count { it.isCustom }
                                        presetNameInput = "我的调音 ${count + 1}"
                                        showSaveDialog = true
                                        isMoreMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("参数均衡器 (PEQ)", color = colors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.AutoGraph, null, tint = colors.secondary) },
                                    onClick = {
                                        viewModel.navigateTo(AppScreen.PARAMETRIC)
                                        isMoreMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("重置当前增益", color = colors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = colors.textPrimary) },
                                    onClick = {
                                        for (i in uiState.bandGains.indices) {
                                            viewModel.setBandGain(i, 0f)
                                        }
                                        viewModel.setPreampGain(0f)
                                        isMoreMenuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("音效与系统设置", color = colors.textPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Settings, null, tint = colors.textSecondary) },
                                    onClick = {
                                        onOpenSettings()
                                        isMoreMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. 最底部：整洁对称的双旋钮音调调谐区 (低音 / 高音)，留足 MiniPlayer 避让间距
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 86.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DspRotaryKnob(
                        title = "低音",
                        strength = uiState.bassBoostStrength,
                        enabled = uiState.isEnabled,
                        onStrengthChanged = {
                            viewModel.toggleBassBoost(it > 0.001f)
                            viewModel.setBassBoostStrength(it)
                        }
                    )

                    DspRotaryKnob(
                        title = "高音",
                        strength = uiState.trebleBoostStrength,
                        enabled = uiState.isEnabled,
                        onStrengthChanged = {
                            viewModel.toggleTrebleBoost(it > 0.001f)
                            viewModel.setTrebleBoostStrength(it)
                        }
                    )
                }
            }
        }

        // 保存自定义配置弹窗
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = {
                    Text(
                        text = stringResource(R.string.preset_save_dialog_title),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.preset_save_dialog_desc),
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                        OutlinedTextField(
                            value = presetNameInput,
                            onValueChange = { presetNameInput = it },
                            label = { Text("预设名称") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                focusedLabelColor = colors.primary,
                                cursorColor = colors.primary
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
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Text(text = "保存", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(text = "取消", color = colors.textSecondary)
                    }
                },
                containerColor = colors.surfaceDialog,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

/**
 * 10 频段全景推子横向滚动列
 */
@Composable
private fun EqualizerFadersRow(
    uiState: com.orbit.music.ui.viewmodel.EqualizerUiState,
    onGainChanged: (Int, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val count = minOf(uiState.frequencies.size, uiState.bandGains.size)
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxHeight()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (index in 0 until count) {
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
