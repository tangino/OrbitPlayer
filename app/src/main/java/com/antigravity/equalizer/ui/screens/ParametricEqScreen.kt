package com.antigravity.equalizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import com.antigravity.equalizer.data.model.BandConfig
import com.antigravity.equalizer.data.model.FilterType
import com.antigravity.equalizer.ui.components.FrequencyCurveCanvas
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.viewmodel.EqualizerViewModel

import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParametricEqScreen(
    viewModel: EqualizerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val freqs = remember(uiState.parametricBands) {
        FloatArray(uiState.parametricBands.size) { i -> uiState.parametricBands[i].frequency }
    }
    val gains = remember(uiState.parametricBands) {
        FloatArray(uiState.parametricBands.size) { i -> 
            if (uiState.parametricBands[i].enabled) uiState.parametricBands[i].gainDb else 0f 
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.parametric_eq), fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel), tint = OrbitTheme.colors.textPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.addParametricBand() }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_band), tint = OrbitTheme.colors.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitTheme.colors.background)
            )
        },
        containerColor = OrbitTheme.colors.background
    ) { innerPadding ->
        if (isLandscape) {
            // 专业音频插件横屏双栏视图 (Pro Audio Plugin Dual-Pane)
            Row(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧大视界频响曲线分析仪
                FrequencyCurveCanvas(
                    frequencies = freqs,
                    gainsDb = gains,
                    isEnabled = uiState.isEnabled,
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                )

                // 右侧频段卡片列表
                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${stringResource(R.string.filter_bands)} (${uiState.parametricBands.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.primary,
                        letterSpacing = 1.sp
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.parametricBands.size) { index ->
                            val band = uiState.parametricBands[index]
                            ParametricBandCard(
                                band = band,
                                bandIndex = index,
                                onUpdate = { updated -> viewModel.updateParametricBand(index, updated) },
                                onDelete = { viewModel.removeParametricBand(index) }
                            )
                        }
                    }
                }
            }
        } else {
            // 标准竖屏布局
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FrequencyCurveCanvas(
                    frequencies = freqs,
                    gainsDb = gains,
                    isEnabled = uiState.isEnabled
                )

                Text(
                    text = "${stringResource(R.string.filter_bands)} (${uiState.parametricBands.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.primary,
                    letterSpacing = 1.sp
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.parametricBands.size) { index ->
                        val band = uiState.parametricBands[index]
                        ParametricBandCard(
                            band = band,
                            bandIndex = index,
                            onUpdate = { updated -> viewModel.updateParametricBand(index, updated) },
                            onDelete = { viewModel.removeParametricBand(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParametricBandCard(
    band: BandConfig,
    bandIndex: Int,
    onUpdate: (BandConfig) -> Unit,
    onDelete: () -> Unit
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(OrbitTheme.colors.surfaceCard)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = band.enabled,
                    onCheckedChange = { onUpdate(band.copy(enabled = it)) },
                    colors = CheckboxDefaults.colors(checkedColor = OrbitTheme.colors.primary, uncheckedColor = OrbitTheme.colors.textSecondary)
                )
                Text(
                    text = stringResource(R.string.band_number, bandIndex + 1),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (band.enabled) OrbitTheme.colors.textPrimary else OrbitTheme.colors.textSecondary
                )
            }

            // 滤波器类型选择下拉
            Box {
                Text(
                    text = band.type.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OrbitTheme.colors.tertiary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(OrbitTheme.colors.surface)
                        .clickable { typeMenuExpanded = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )

                DropdownMenu(
                    expanded = typeMenuExpanded,
                    onDismissRequest = { typeMenuExpanded = false },
                    modifier = Modifier.background(OrbitTheme.colors.surfaceCard)
                ) {
                    FilterType.values().forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.displayName, color = OrbitTheme.colors.textPrimary) },
                            onClick = {
                                onUpdate(band.copy(type = t))
                                typeMenuExpanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = OrbitTheme.colors.danger)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 频率 Frequency Slider (20Hz ~ 20kHz)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.freq_label, band.frequency.toInt()), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
            Slider(
                value = band.frequency,
                onValueChange = { onUpdate(band.copy(frequency = it)) },
                valueRange = 20f..20000f,
                colors = SliderDefaults.colors(thumbColor = PrimaryNeonCyan, activeTrackColor = PrimaryNeonCyan),
                modifier = Modifier.weight(1f)
            )
        }

        // 增益 Gain Slider (-7dB ~ +7dB)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.gain_label, band.gainDb), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
            Slider(
                value = band.gainDb,
                onValueChange = { onUpdate(band.copy(gainDb = it)) },
                valueRange = -7f..7f,
                colors = SliderDefaults.colors(thumbColor = AccentPurple, activeTrackColor = AccentPurple),
                modifier = Modifier.weight(1f)
            )
        }

        // Q 值 Slider (0.1 ~ 8.0)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.q_label, band.q), fontSize = 11.sp, color = TextSecondary, modifier = Modifier.width(90.dp))
            Slider(
                value = band.q,
                onValueChange = { onUpdate(band.copy(q = it)) },
                valueRange = 0.1f..8.0f,
                colors = SliderDefaults.colors(thumbColor = AccentOrange, activeTrackColor = AccentOrange),
                modifier = Modifier.weight(1f)
            )
        }
    }
}
