package com.antigravity.equalizer.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.R
import com.antigravity.equalizer.audio.ShuffleStrategy
import com.antigravity.equalizer.data.repository.AppProfile
import com.antigravity.equalizer.data.repository.AppProfileRepository
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.viewmodel.EqualizerViewModel
import com.antigravity.equalizer.ui.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: EqualizerViewModel,
    musicViewModel: MusicPlayerViewModel? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAppProfileDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var editingColorIndex by remember { mutableIntStateOf(1) }
    var importText by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var isAddingIncludedFolder by remember { mutableStateOf(true) }
    var customFolderPath by remember { mutableStateOf("") }

    val includedFolders by musicViewModel?.includedFolders?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val excludedFolders by musicViewModel?.excludedFolders?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val isScanning by musicViewModel?.isScanning?.collectAsState() ?: remember { mutableStateOf(false) }

    val appProfiles by AppProfileRepository.instance.appProfiles.collectAsState()

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val success = viewModel.setCustomBackgroundFromUri(uri, context)
            if (success) {
                Toast.makeText(context, context.getString(R.string.background_select_success), Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cancel), tint = OrbitTheme.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OrbitTheme.colors.background)
            )
        },
        containerColor = OrbitTheme.colors.background
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // 0.1 Theme 外观主题设置
            item {
                SettingsSectionHeader(stringResource(R.string.theme_title))
                SettingsCard {
                    val themeOptions = listOf(
                        "system" to stringResource(R.string.theme_system),
                        "dark" to stringResource(R.string.theme_dark),
                        "light" to stringResource(R.string.theme_light)
                    )
                    val currentThemeLabel = themeOptions.find { it.first == uiState.themeMode }?.second
                        ?: stringResource(R.string.theme_system)

                    SettingsDropdownItem(
                        icon = Icons.Default.Brightness4,
                        title = stringResource(R.string.theme_title),
                        subtitle = stringResource(R.string.theme_subtitle),
                        currentValue = currentThemeLabel,
                        options = themeOptions.map { it.second },
                        onOptionSelected = { selectedLabel ->
                            val mode = themeOptions.find { it.second == selectedLabel }?.first ?: "system"
                            viewModel.setThemeMode(mode)
                        }
                    )
                }
            }

            // 0.1.0 自定义程序背景与磨砂玻璃模糊 (沉浸透光背景)
            item {
                SettingsSectionHeader(stringResource(R.string.section_custom_background))
                SettingsCard {
                    val hasBg = uiState.customBackgroundPath != null && File(uiState.customBackgroundPath!!).exists()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrbitTheme.colors.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wallpaper,
                                contentDescription = null,
                                tint = OrbitTheme.colors.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.custom_background_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = OrbitTheme.colors.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.custom_background_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { backgroundPickerLauncher.launch("image/*") },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.btn_choose_background),
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (hasBg) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(uiState.customBackgroundPath!!))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, OrbitTheme.colors.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                                Text(
                                    text = if (uiState.backgroundBlurStyle == "frosted_glass") {
                                        stringResource(R.string.background_blur_style_frosted)
                                    } else {
                                        stringResource(R.string.background_blur_style_gaussian)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrbitTheme.colors.primary
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    viewModel.clearCustomBackground(context)
                                    Toast.makeText(context, context.getString(R.string.background_reset_success), Toast.LENGTH_SHORT).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = OrbitTheme.colors.danger
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.btn_reset_background),
                                    fontSize = 12.sp,
                                    color = OrbitTheme.colors.danger
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        val blurStyleOptions = listOf(
                            "frosted_glass" to stringResource(R.string.background_blur_style_frosted),
                            "gaussian" to stringResource(R.string.background_blur_style_gaussian)
                        )
                        val currentBlurStyleLabel = blurStyleOptions.find { it.first == uiState.backgroundBlurStyle }?.second
                            ?: stringResource(R.string.background_blur_style_frosted)

                        SettingsDropdownItem(
                            icon = Icons.Default.BlurOn,
                            title = stringResource(R.string.background_blur_style_title),
                            subtitle = stringResource(R.string.background_blur_style_subtitle),
                            currentValue = currentBlurStyleLabel,
                            options = blurStyleOptions.map { it.second },
                            onOptionSelected = { selectedLabel ->
                                val target = blurStyleOptions.find { it.second == selectedLabel }?.first ?: "frosted_glass"
                                viewModel.setBackgroundBlurStyle(target)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        SettingsSliderItem(
                            icon = Icons.Default.BlurCircular,
                            title = stringResource(R.string.background_blur_radius_title),
                            valueText = String.format(java.util.Locale.US, "%.0f dp", uiState.backgroundBlurRadius),
                            value = uiState.backgroundBlurRadius,
                            valueRange = 0f..50f,
                            onValueChange = { viewModel.setBackgroundBlurRadius(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        SettingsSliderItem(
                            icon = Icons.Default.BrightnessMedium,
                            title = stringResource(R.string.background_dim_alpha_title),
                            valueText = "${(uiState.backgroundDimAlpha * 100).toInt()}%",
                            value = uiState.backgroundDimAlpha,
                            valueRange = 0.0f..0.80f,
                            onValueChange = { viewModel.setBackgroundDimAlpha(it) }
                        )
                    }
                }
            }

            // 0.1.1 播放进度条拖尾样式设置与个性化调节
            item {
                SettingsSectionHeader(stringResource(R.string.progress_trail_style_title))
                SettingsCard {
                    val trailOptions = listOf(
                        com.antigravity.equalizer.data.model.ProgressTrailStyle.NEON_PULSE.id to stringResource(R.string.trail_style_neon_pulse),
                        com.antigravity.equalizer.data.model.ProgressTrailStyle.COMET_HELIX.id to stringResource(R.string.trail_style_comet_helix),
                        com.antigravity.equalizer.data.model.ProgressTrailStyle.MINIMAL.id to stringResource(R.string.trail_style_minimal)
                    )
                    val currentTrailLabel = trailOptions.find { it.first == uiState.progressTrailStyle }?.second
                        ?: stringResource(R.string.trail_style_neon_pulse)

                    SettingsDropdownItem(
                        icon = Icons.Default.Timeline,
                        title = stringResource(R.string.progress_trail_style_title),
                        subtitle = stringResource(R.string.progress_trail_style_subtitle),
                        currentValue = currentTrailLabel,
                        options = trailOptions.map { it.second },
                        onOptionSelected = { selectedLabel ->
                            val styleId = trailOptions.find { it.second == selectedLabel }?.first
                                ?: com.antigravity.equalizer.data.model.ProgressTrailStyle.NEON_PULSE.id
                            viewModel.setProgressTrailStyle(styleId)
                        }
                    )

                    if (uiState.progressTrailStyle != com.antigravity.equalizer.data.model.ProgressTrailStyle.MINIMAL.id) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.6.dp,
                            color = GridLineColor
                        )

                        // 起点粗细 (1.0dp ~ 10.0dp)
                        SettingsSliderItem(
                            icon = Icons.Default.Tune,
                            title = stringResource(R.string.trail_start_width_title),
                            valueText = String.format(java.util.Locale.US, "%.1f dp", uiState.trailStartWidth),
                            value = uiState.trailStartWidth,
                            valueRange = 1.0f..10.0f,
                            onValueChange = { viewModel.setTrailStartWidth(it) }
                        )

                        // 终点粗细 (0.5dp ~ 8.0dp)
                        SettingsSliderItem(
                            icon = Icons.Default.HorizontalRule,
                            title = stringResource(R.string.trail_end_width_title),
                            valueText = String.format(java.util.Locale.US, "%.1f dp", uiState.trailEndWidth),
                            value = uiState.trailEndWidth,
                            valueRange = 0.5f..8.0f,
                            onValueChange = { viewModel.setTrailEndWidth(it) }
                        )

                        // 拖尾环绕半径 (3.0dp ~ 18.0dp)
                        SettingsSliderItem(
                            icon = Icons.Default.Adjust,
                            title = stringResource(R.string.trail_radius_title),
                            valueText = String.format(java.util.Locale.US, "%.1f dp", uiState.trailOrbitRadius),
                            value = uiState.trailOrbitRadius,
                            valueRange = 3.0f..18.0f,
                            onValueChange = { viewModel.setTrailOrbitRadius(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.6.dp,
                            color = GridLineColor
                        )

                        // 拖尾 1 颜色 (主线条)
                        SettingsColorPickerItem(
                            icon = Icons.Default.Palette,
                            title = stringResource(R.string.trail_color1_title),
                            selectedColor = uiState.trailColor1,
                            onColorSelected = { viewModel.setTrailColor1(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.6.dp,
                            color = GridLineColor
                        )

                        // 拖尾 2 颜色 (副线条)
                        SettingsColorPickerItem(
                            icon = Icons.Default.Brush,
                            title = stringResource(R.string.trail_color2_title),
                            selectedColor = uiState.trailColor2,
                            onColorSelected = { viewModel.setTrailColor2(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            thickness = 0.6.dp,
                            color = GridLineColor
                        )

                        // 恢复默认按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.resetTrailSettings() }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.trail_reset_defaults),
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.trail_reset_defaults),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = OrbitTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }

            // 0.1.2 频谱可视化 (Poweramp 风格) 设置
            item {
                SettingsSectionHeader(stringResource(R.string.section_spectrum_visualizer))
                SettingsCard {
                    // 1. 启用开关
                    SettingsSwitchItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.visualizer_enabled_title),
                        subtitle = stringResource(R.string.visualizer_enabled_subtitle),
                        checked = uiState.visualizerEnabled,
                        onCheckedChange = { viewModel.toggleVisualizerEnabled(it) }
                    )

                    if (uiState.visualizerEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 2. 频谱形态选择
                        val styleOptions = com.antigravity.equalizer.data.model.VisualizerStyle.values().map { style ->
                            style to stringResource(style.titleRes)
                        }
                        val currentStyleLabel = styleOptions.find { it.first == uiState.visualizerStyle }?.second
                            ?: stringResource(R.string.visualizer_style_bars_with_peaks)

                        SettingsDropdownItem(
                            icon = Icons.Default.AutoGraph,
                            title = stringResource(R.string.visualizer_style_title),
                            subtitle = stringResource(R.string.visualizer_style_subtitle),
                            currentValue = currentStyleLabel,
                            options = styleOptions.map { it.second },
                            onOptionSelected = { selectedLabel ->
                                val target = styleOptions.find { it.second == selectedLabel }?.first
                                    ?: com.antigravity.equalizer.data.model.VisualizerStyle.BARS_WITH_PEAKS
                                viewModel.setVisualizerStyle(target)
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 2.1 柱状频谱单条宽度调节 (2.0dp ~ 14.0dp，频谱越窄绘制越密，越宽越少)
                        SettingsSliderItem(
                            icon = Icons.Default.ViewColumn,
                            title = stringResource(R.string.visualizer_bar_width_title),
                            valueText = String.format(java.util.Locale.US, "%.1f dp", uiState.visualizerBarWidthDp),
                            value = uiState.visualizerBarWidthDp,
                            valueRange = 2.0f..14.0f,
                            onValueChange = { viewModel.setVisualizerBarWidth(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 2.2 频谱条透明度调节 (10% ~ 100%)
                        SettingsSliderItem(
                            icon = Icons.Default.Opacity,
                            title = stringResource(R.string.visualizer_bar_alpha_title),
                            valueText = "${(uiState.visualizerBarAlpha * 100).toInt()}%",
                            value = uiState.visualizerBarAlpha,
                            valueRange = 0.10f..1.0f,
                            onValueChange = { viewModel.setVisualizerBarAlpha(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 2.3 仅绘制柱状边框开关 (线框镂空模式)
                        SettingsSwitchItem(
                            icon = Icons.Default.CheckBoxOutlineBlank,
                            title = stringResource(R.string.visualizer_bar_border_only_title),
                            subtitle = stringResource(R.string.visualizer_bar_border_only_subtitle),
                            checked = uiState.visualizerBarBorderOnly,
                            onCheckedChange = { viewModel.setVisualizerBarBorderOnly(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 2.4 柱状频谱边框宽度调节 (0.0dp ~ 4.0dp)
                        SettingsSliderItem(
                            icon = Icons.Default.CropSquare,
                            title = stringResource(R.string.visualizer_bar_border_width_title),
                            valueText = if (uiState.visualizerBarBorderWidthDp <= 0.05f) {
                                stringResource(R.string.visualizer_style_off)
                            } else {
                                String.format(java.util.Locale.US, "%.1f dp", uiState.visualizerBarBorderWidthDp)
                            },
                            value = uiState.visualizerBarBorderWidthDp,
                            valueRange = 0.0f..4.0f,
                            onValueChange = { viewModel.setVisualizerBarBorderWidth(it) }
                        )

                        if (uiState.visualizerBarBorderOnly || uiState.visualizerBarBorderWidthDp > 0.05f) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )

                            // 2.5 柱状频谱边框透明度调节 (0% ~ 100%)
                            SettingsSliderItem(
                                icon = Icons.Default.Tonality,
                                title = stringResource(R.string.visualizer_bar_border_alpha_title),
                                valueText = "${(uiState.visualizerBarBorderAlpha * 100).toInt()}%",
                                value = uiState.visualizerBarBorderAlpha,
                                valueRange = 0.0f..1.0f,
                                onValueChange = { viewModel.setVisualizerBarBorderAlpha(it) }
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )

                            // 2.6 柱状频谱边框颜色选择
                            SettingsColorPickerItem(
                                icon = Icons.Default.BorderColor,
                                title = stringResource(R.string.visualizer_bar_border_color_title),
                                selectedColor = uiState.visualizerBarBorderColor,
                                onColorSelected = { viewModel.setVisualizerBarBorderColor(it) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 3. 悬浮顶峰缓降开关 (Peak Hold)
                        SettingsSwitchItem(
                            icon = Icons.Default.VerticalAlignTop,
                            title = stringResource(R.string.visualizer_peak_decay_title),
                            subtitle = stringResource(R.string.visualizer_peak_decay_subtitle),
                            checked = uiState.visualizerPeakDecayEnabled,
                            onCheckedChange = { viewModel.toggleVisualizerPeakDecay(it) }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 3.1 单色纯色显示开关 (Single Color Mode)
                        SettingsSwitchItem(
                            icon = Icons.Default.FormatColorFill,
                            title = stringResource(R.string.visualizer_single_color_title),
                            subtitle = stringResource(R.string.visualizer_single_color_subtitle),
                            checked = uiState.visualizerSingleColor,
                            onCheckedChange = {
                                viewModel.setVisualizerSingleColor(it)
                                if (it) {
                                    editingColorIndex = 1
                                }
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 4. 配色方案选择
                        val colorOptions = com.antigravity.equalizer.data.model.VisualizerColorScheme.values().map { scheme ->
                            scheme to stringResource(scheme.titleRes)
                        }
                        val currentColorLabel = colorOptions.find { it.first == uiState.visualizerColorScheme }?.second
                            ?: stringResource(R.string.visualizer_color_follow_theme)

                        SettingsDropdownItem(
                            icon = Icons.Default.Palette,
                            title = stringResource(R.string.visualizer_color_title),
                            subtitle = stringResource(R.string.visualizer_color_subtitle),
                            currentValue = currentColorLabel,
                            options = colorOptions.map { it.second },
                            onOptionSelected = { selectedLabel ->
                                val target = colorOptions.find { it.second == selectedLabel }?.first
                                    ?: com.antigravity.equalizer.data.model.VisualizerColorScheme.FOLLOW_THEME
                                viewModel.setVisualizerColorScheme(target)
                                if (target == com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM) {
                                    editingColorIndex = 1
                                    showColorPickerDialog = true
                                }
                            }
                        )

                        // 5. 自定义颜色与色卡快速点选区
                        val isCustomScheme = uiState.visualizerColorScheme == com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM
                        val activeColor1 = when (uiState.visualizerColorScheme) {
                            com.antigravity.equalizer.data.model.VisualizerColorScheme.FOLLOW_THEME -> OrbitTheme.colors.primary.toArgb().toLong() and 0xFFFFFFFFL
                            com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor
                            else -> uiState.visualizerColorScheme.primaryColor.toArgb().toLong() and 0xFFFFFFFFL
                        }
                        val activeColor2 = when (uiState.visualizerColorScheme) {
                            com.antigravity.equalizer.data.model.VisualizerColorScheme.FOLLOW_THEME -> OrbitTheme.colors.secondary.toArgb().toLong() and 0xFFFFFFFFL
                            com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor2
                            else -> uiState.visualizerColorScheme.secondaryColor.toArgb().toLong() and 0xFFFFFFFFL
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            // 标题栏：展示当前颜色（单色模式展示纯色，双色模式展示垂直渐变色球）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (uiState.visualizerSingleColor) {
                                                    Brush.linearGradient(
                                                        listOf(Color(activeColor1), Color(activeColor1))
                                                    )
                                                } else {
                                                    Brush.verticalGradient(
                                                        listOf(Color(activeColor2), Color(activeColor1))
                                                    )
                                                }
                                            )
                                            .border(1.5.dp, OrbitTheme.colors.textPrimary.copy(alpha = 0.35f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = stringResource(R.string.visualizer_color_custom),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // 颜色 1：主色 / 单色（底端）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (editingColorIndex == 1) OrbitTheme.colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable {
                                        editingColorIndex = 1
                                        showColorPickerDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(activeColor1))
                                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (uiState.visualizerSingleColor) {
                                                stringResource(R.string.visualizer_single_color_title)
                                            } else {
                                                stringResource(R.string.visualizer_color1_title)
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = if (editingColorIndex == 1) FontWeight.Bold else FontWeight.Normal,
                                            color = if (editingColorIndex == 1) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = String.format("#%06X", 0xFFFFFF and activeColor1.toInt()),
                                            fontSize = 10.sp,
                                            color = OrbitTheme.colors.textSecondary
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        editingColorIndex = 1
                                        showColorPickerDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = OrbitTheme.colors.surface,
                                        contentColor = OrbitTheme.colors.primary
                                    )
                                ) {
                                    Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.color_picker_title), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 颜色 2：顶端色 (次色，仅在未开启单色时显示)
                            if (!uiState.visualizerSingleColor) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (editingColorIndex == 2) OrbitTheme.colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                                    .clickable {
                                        editingColorIndex = 2
                                        showColorPickerDialog = true
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(activeColor2))
                                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.visualizer_color2_title),
                                            fontSize = 12.sp,
                                            fontWeight = if (editingColorIndex == 2) FontWeight.Bold else FontWeight.Normal,
                                            color = if (editingColorIndex == 2) OrbitTheme.colors.primary else OrbitTheme.colors.textPrimary
                                        )
                                        Text(
                                            text = String.format("#%06X", 0xFFFFFF and activeColor2.toInt()),
                                            fontSize = 10.sp,
                                            color = OrbitTheme.colors.textSecondary
                                        )
                                    }
                                }

                                FilledTonalButton(
                                    onClick = {
                                        editingColorIndex = 2
                                        showColorPickerDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = OrbitTheme.colors.surface,
                                        contentColor = OrbitTheme.colors.primary
                                    )
                                ) {
                                    Icon(Icons.Default.ColorLens, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.color_picker_title), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // 色卡横滑列表 (用户点击色卡直接应用到当前激活选中的颜色项)
                        if (uiState.customVisualizerColors.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (uiState.visualizerSingleColor || editingColorIndex == 1) {
                                        "点选色卡直接应用至：【底端主色】"
                                    } else {
                                        "点选色卡直接应用至：【顶端次色】"
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OrbitTheme.colors.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(uiState.customVisualizerColors) { colorLong ->
                                    val isColor1 = activeColor1 == colorLong
                                    val isColor2 = activeColor2 == colorLong
                                    val isSelected = if (uiState.visualizerSingleColor) isColor1 else (isColor1 || isColor2)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(colorLong))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) OrbitTheme.colors.primary else Color.White.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                if (!isCustomScheme) {
                                                    if (uiState.visualizerSingleColor || editingColorIndex == 1) {
                                                        viewModel.setVisualizerCustomColor2(activeColor2)
                                                        viewModel.setVisualizerCustomColor(colorLong)
                                                    } else {
                                                        viewModel.setVisualizerCustomColor(activeColor1)
                                                        viewModel.setVisualizerCustomColor2(colorLong)
                                                    }
                                                } else {
                                                    if (uiState.visualizerSingleColor || editingColorIndex == 1) {
                                                        viewModel.setVisualizerCustomColor(colorLong)
                                                    } else {
                                                        viewModel.setVisualizerCustomColor2(colorLong)
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            if (uiState.visualizerSingleColor) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = if (isColor1 && isColor2) "1+2" else if (isColor1) "1" else "2",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = OrbitTheme.colors.surface
                        )

                        // 3.4 全屏最大化封面透明度
                        SettingsSliderItem(
                            icon = Icons.Default.Opacity,
                            title = stringResource(R.string.maximized_cover_alpha),
                            valueText = "${(uiState.maximizedCoverAlpha * 100).toInt()}%",
                            value = uiState.maximizedCoverAlpha,
                            valueRange = 0.1f..1.0f,
                            onValueChange = { viewModel.setMaximizedCoverAlpha(it) }
                        )
                    }
                }
            }

            // 0.2 Language 语言设置
            item {
                SettingsSectionHeader(stringResource(R.string.language_title))
                SettingsCard {
                    val langOptions = listOf(
                        "system" to stringResource(R.string.language_system),
                        "zh" to stringResource(R.string.language_chinese),
                        "en" to stringResource(R.string.language_english)
                    )
                    val currentLangLabel = langOptions.find { it.first == uiState.selectedLanguage }?.second
                        ?: stringResource(R.string.language_system)

                    SettingsDropdownItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.language_title),
                        subtitle = stringResource(R.string.language_subtitle),
                        currentValue = currentLangLabel,
                        options = langOptions.map { it.second },
                        onOptionSelected = { selectedLabel ->
                            val code = langOptions.find { it.second == selectedLabel }?.first ?: "system"
                            viewModel.setLanguage(code)
                        }
                    )
                }
            }

            // 0.3 启动偏好设置 (仅作为均衡器启动 & 播放条常驻)
            item {
                SettingsSectionHeader(stringResource(R.string.section_startup_mode))
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.launch_as_equalizer_only_title),
                        subtitle = stringResource(R.string.launch_as_equalizer_only_subtitle),
                        checked = uiState.launchAsEqualizerOnly,
                        onCheckedChange = { viewModel.toggleLaunchAsEqualizerOnly(it) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsSwitchItem(
                        icon = Icons.Default.SmartDisplay,
                        title = stringResource(R.string.persistent_mini_player_title),
                        subtitle = stringResource(R.string.persistent_mini_player_subtitle),
                        checked = uiState.persistentMiniPlayer,
                        onCheckedChange = { viewModel.togglePersistentMiniPlayer(it) }
                    )
                }
            }

            // 1. Audio Engine 选项
            item {
                SettingsSectionHeader(stringResource(R.string.section_audio_engine))
                SettingsCard {
                    // 采样率
                    SettingsDropdownItem(
                        icon = Icons.Default.GraphicEq,
                        title = stringResource(R.string.sample_rate_title),
                        subtitle = stringResource(R.string.sample_rate_subtitle),
                        currentValue = "${uiState.sampleRate.toInt()} Hz",
                        options = listOf("44100 Hz", "48000 Hz", "96000 Hz", "192000 Hz"),
                        onOptionSelected = {
                            val rate = it.replace(" Hz", "").toFloatOrNull() ?: 44100f
                            viewModel.setSampleRate(rate)
                        }
                    )

                    HorizontalDivider(color = GridLineColor)

                    // 频段数量
                    SettingsDropdownItem(
                        icon = Icons.Default.Tune,
                        title = stringResource(R.string.num_bands_title),
                        subtitle = stringResource(R.string.num_bands_subtitle),
                        currentValue = "${uiState.numBands} ${stringResource(R.string.bands)}",
                        options = listOf("10 ${stringResource(R.string.bands)}", "15 ${stringResource(R.string.bands)}", "20 ${stringResource(R.string.bands)}"),
                        onOptionSelected = {
                            val bands = it.split(" ").firstOrNull()?.toIntOrNull() ?: 10
                            viewModel.setNumBands(bands)
                        }
                    )

                    HorizontalDivider(color = GridLineColor)

                    // 自动增益
                    SettingsSwitchItem(
                        icon = Icons.Default.AutoMode,
                        title = stringResource(R.string.auto_gain_title),
                        subtitle = stringResource(R.string.auto_gain_subtitle),
                        checked = uiState.autoGainEnabled,
                        onCheckedChange = { viewModel.toggleAutoGain(it) }
                    )
                }
            }

            // 媒体库文件夹扫描过滤设置 (仅当接入 musicViewModel 时呈现)
            if (musicViewModel != null) {
                // 媒体库文件夹扫描过滤设置
                item {
                    SettingsSectionHeader(stringResource(R.string.scan_settings_title))
                    SettingsCard {
                        // 1. 扫描特定文件夹 (白名单)
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.scan_included_folders),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.scan_included_folders_desc),
                                        fontSize = 11.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                                IconButton(onClick = {
                                    isAddingIncludedFolder = true
                                    customFolderPath = ""
                                    showAddFolderDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = "Add included folder",
                                        tint = OrbitTheme.colors.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (includedFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.scan_all_folders_default),
                                    fontSize = 12.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    includedFolders.forEach { folder ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(OrbitTheme.colors.surface)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = OrbitTheme.colors.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = folder,
                                                fontSize = 12.sp,
                                                color = OrbitTheme.colors.textPrimary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = { musicViewModel.removeIncludedFolder(folder) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = OrbitTheme.colors.textSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = GridLineColor)

                        // 2. 排除特定文件夹 (黑名单)
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.scan_excluded_folders),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.scan_excluded_folders_desc),
                                        fontSize = 11.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                                IconButton(onClick = {
                                    isAddingIncludedFolder = false
                                    customFolderPath = ""
                                    showAddFolderDialog = true
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.AddCircleOutline,
                                        contentDescription = "Add excluded folder",
                                        tint = OrbitTheme.colors.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            if (excludedFolders.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.scan_no_excluded_folders),
                                    fontSize = 12.sp,
                                    color = OrbitTheme.colors.textSecondary.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    excludedFolders.forEach { folder ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(OrbitTheme.colors.surface)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.FolderOff,
                                                contentDescription = null,
                                                tint = Color(0xFFFF5252),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = folder,
                                                fontSize = 12.sp,
                                                color = OrbitTheme.colors.textPrimary,
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            IconButton(
                                                onClick = { musicViewModel.removeExcludedFolder(folder) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Remove",
                                                    tint = OrbitTheme.colors.textSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = GridLineColor)

                        // 3. 立即触发重新扫描
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isScanning) {
                                    musicViewModel.scanMedia()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.rescan_library_now),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = OrbitTheme.colors.primary
                                )
                                Text(
                                    text = if (isScanning) stringResource(R.string.syncing_audio_library) else stringResource(R.string.rescan_library_desc),
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = OrbitTheme.colors.primary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Rescan",
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = GridLineColor)

                        // 4. MusicBrainz / Cover Art Archive 在线大尺寸封面自动匹配开关
                        SettingsSwitchItem(
                            icon = Icons.Default.ImageSearch,
                            title = stringResource(R.string.online_cover_auto_match_title),
                            subtitle = stringResource(R.string.online_cover_auto_match_subtitle),
                            checked = uiState.autoMatchOnlineCover,
                            onCheckedChange = { viewModel.setAutoMatchOnlineCover(it) }
                        )

                        if (uiState.autoMatchOnlineCover) {
                            HorizontalDivider(color = GridLineColor)

                            // 5. 仅在 Wi-Fi 网络下自动匹配
                            SettingsSwitchItem(
                                icon = Icons.Default.Wifi,
                                title = stringResource(R.string.online_cover_wifi_only_title),
                                subtitle = stringResource(R.string.online_cover_wifi_only_subtitle),
                                checked = uiState.onlineCoverWifiOnly,
                                onCheckedChange = { viewModel.setOnlineCoverWifiOnly(it) }
                            )
                        }
                    }
                }
            }

            // 2. 预设备份与导入导出
            item {
                SettingsSectionHeader(stringResource(R.string.section_presets_backup))
                SettingsCard {
                    SettingsActionItem(
                        icon = Icons.Default.FileDownload,
                        title = stringResource(R.string.import_preset_title),
                        subtitle = stringResource(R.string.import_preset_subtitle),
                        onClick = {
                            importText = ""
                            showImportDialog = true
                        }
                    )

                    HorizontalDivider(color = GridLineColor)

                    SettingsActionItem(
                        icon = Icons.Default.FileUpload,
                        title = stringResource(R.string.export_preset_title),
                        subtitle = stringResource(R.string.export_preset_subtitle),
                        onClick = {
                            exportedJson = viewModel.exportPresetsJson()
                            showExportDialog = true
                        }
                    )
                }
            }

            // 3. 设备与应用专属独立配置
            item {
                SettingsSectionHeader(stringResource(R.string.section_per_app_device))
                SettingsCard {
                    SettingsActionItem(
                        icon = Icons.Default.Apps,
                        title = stringResource(R.string.per_app_eq_title),
                        subtitle = stringResource(R.string.per_app_eq_subtitle),
                        onClick = { showAppProfileDialog = true }
                    )

                    HorizontalDivider(color = GridLineColor)

                    SettingsSwitchItem(
                        icon = Icons.Default.Bluetooth,
                        title = stringResource(R.string.auto_device_preset_title),
                        subtitle = stringResource(R.string.auto_device_preset_subtitle),
                        checked = uiState.autoDeviceProfileEnabled,
                        onCheckedChange = { viewModel.toggleAutoDeviceProfile(it) }
                    )

                    HorizontalDivider(color = OrbitTheme.colors.gridLine)

                    SettingsInfoItem(
                        icon = Icons.Default.Headphones,
                        title = stringResource(R.string.current_audio_device),
                        value = uiState.activeDeviceName
                    )
                }
            }

            // 4. 关于与引擎状态
            item {
                SettingsSectionHeader(stringResource(R.string.section_system_diagnostics))
                SettingsCard {
                    SettingsActionItem(
                        icon = Icons.Default.BatteryChargingFull,
                        title = stringResource(R.string.battery_optimization_title),
                        subtitle = stringResource(R.string.battery_optimization_subtitle),
                        onClick = {
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        }
                    )
                    HorizontalDivider(color = OrbitTheme.colors.gridLine)
                    SettingsInfoItem(icon = Icons.Default.Memory, title = stringResource(R.string.dsp_engine_title), value = stringResource(R.string.dsp_engine_value))
                    HorizontalDivider(color = OrbitTheme.colors.gridLine)
                    SettingsInfoItem(icon = Icons.Default.Speed, title = stringResource(R.string.dsp_latency_title), value = stringResource(R.string.dsp_latency_value))
                    HorizontalDivider(color = OrbitTheme.colors.gridLine)
                    SettingsInfoItem(icon = Icons.Default.Info, title = stringResource(R.string.version_title), value = stringResource(R.string.version_value))
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

    // Per-App EQ 管理弹窗
    if (showAppProfileDialog) {
        AlertDialog(
            onDismissRequest = { showAppProfileDialog = false },
            title = { Text(stringResource(R.string.per_app_dialog_title), color = OrbitTheme.colors.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(appProfiles.values.toList()) { profile ->
                        var expanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(OrbitTheme.colors.surface)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.appName, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.textPrimary, fontSize = 13.sp)
                                Text(profile.packageName, color = OrbitTheme.colors.textSecondary, fontSize = 10.sp)
                            }
                            Box {
                                Text(
                                    text = profile.presetId.uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(OrbitTheme.colors.surfaceCard)
                                        .clickable { expanded = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false },
                                    modifier = Modifier.background(OrbitTheme.colors.surfaceDialog)
                                ) {
                                    uiState.presets.forEach { preset ->
                                        DropdownMenuItem(
                                            text = { Text(preset.name, color = OrbitTheme.colors.textPrimary) },
                                            onClick = {
                                                AppProfileRepository.instance.saveAppProfile(profile.copy(presetId = preset.id))
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showAppProfileDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.done), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 导入弹窗
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.import_dialog_title), color = OrbitTheme.colors.textPrimary) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.import_dialog_desc),
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        placeholder = { Text("{\"id\":\"...\"} or GraphicEQ: 31.25 ...", color = OrbitTheme.colors.textSecondary) }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importPresetText(importText)
                        showImportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.import_text), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 导出弹窗
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.export_dialog_title), color = OrbitTheme.colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = exportedJson,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.done), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    // 自定义频谱 HSV 色盘对话框
    if (showColorPickerDialog) {
        val isCustomScheme = uiState.visualizerColorScheme == com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM
        val activeColor1 = when (uiState.visualizerColorScheme) {
            com.antigravity.equalizer.data.model.VisualizerColorScheme.FOLLOW_THEME -> OrbitTheme.colors.primary.toArgb().toLong() and 0xFFFFFFFFL
            com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor
            else -> uiState.visualizerColorScheme.primaryColor.toArgb().toLong() and 0xFFFFFFFFL
        }
        val activeColor2 = when (uiState.visualizerColorScheme) {
            com.antigravity.equalizer.data.model.VisualizerColorScheme.FOLLOW_THEME -> OrbitTheme.colors.secondary.toArgb().toLong() and 0xFFFFFFFFL
            com.antigravity.equalizer.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor2
            else -> uiState.visualizerColorScheme.secondaryColor.toArgb().toLong() and 0xFFFFFFFFL
        }
        val currentInitialColor = if (editingColorIndex == 1) activeColor1 else activeColor2
        val dialogTitle = if (editingColorIndex == 1) {
            stringResource(R.string.visualizer_color1_title)
        } else {
            stringResource(R.string.visualizer_color2_title)
        }

        key(editingColorIndex) {
            com.antigravity.equalizer.ui.components.ColorPickerDialog(
                initialColor = currentInitialColor,
                customColors = uiState.customVisualizerColors,
                title = "${stringResource(R.string.color_picker_title)} - $dialogTitle",
                onColorConfirmed = { chosenColor ->
                    if (!isCustomScheme) {
                        if (editingColorIndex == 1) {
                            viewModel.setVisualizerCustomColor2(activeColor2)
                            viewModel.setVisualizerCustomColor(chosenColor)
                        } else {
                            viewModel.setVisualizerCustomColor(activeColor1)
                            viewModel.setVisualizerCustomColor2(chosenColor)
                        }
                    } else {
                        if (editingColorIndex == 1) {
                            viewModel.setVisualizerCustomColor(chosenColor)
                        } else {
                            viewModel.setVisualizerCustomColor2(chosenColor)
                        }
                    }
                },
                onSaveToCustomColors = { colorToAdd ->
                    viewModel.addCustomVisualizerColor(colorToAdd)
                    if (!isCustomScheme) {
                        if (editingColorIndex == 1) {
                            viewModel.setVisualizerCustomColor2(activeColor2)
                            viewModel.setVisualizerCustomColor(colorToAdd)
                        } else {
                            viewModel.setVisualizerCustomColor(activeColor1)
                            viewModel.setVisualizerCustomColor2(colorToAdd)
                        }
                    } else {
                        if (editingColorIndex == 1) {
                            viewModel.setVisualizerCustomColor(colorToAdd)
                        } else {
                            viewModel.setVisualizerCustomColor2(colorToAdd)
                        }
                    }
                },
                onRemoveCustomColor = { colorToRemove ->
                    viewModel.removeCustomVisualizerColor(colorToRemove)
                },
                onDismissRequest = { showColorPickerDialog = false }
            )
        }
    }

    // 添加扫描/排除文件夹弹窗
    if (showAddFolderDialog && musicViewModel != null) {
        val discoveredFolders by musicViewModel.folders.collectAsState()
        AlertDialog(
            onDismissRequest = { showAddFolderDialog = false },
            title = {
                Text(
                    text = if (isAddingIncludedFolder) stringResource(R.string.add_included_folder) else stringResource(R.string.add_excluded_folder),
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.folder_path_hint),
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customFolderPath,
                        onValueChange = { customFolderPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("/storage/emulated/0/Music...", fontSize = 12.sp, color = OrbitTheme.colors.textSecondary) },
                        singleLine = true
                    )

                    if (discoveredFolders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.quick_select_discovered_folder),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                            items(discoveredFolders) { f ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { customFolderPath = f.folderPath }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = OrbitTheme.colors.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "${f.folderName} (${f.songCount})",
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val path = customFolderPath.trim()
                        if (path.isNotEmpty()) {
                            if (isAddingIncludedFolder) {
                                musicViewModel.addIncludedFolder(path)
                            } else {
                                musicViewModel.addExcludedFolder(path)
                            }
                        }
                        showAddFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.primary)
                ) {
                    Text(stringResource(R.string.done), color = if (OrbitTheme.colors.isDark) DarkBackground else Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFolderDialog = false }) {
                    Text(stringResource(R.string.cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = PrimaryNeonCyan,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OrbitTheme.colors.surfaceCard)
            .padding(vertical = 4.dp),
        content = content
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = OrbitTheme.colors.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OrbitTheme.colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = OrbitTheme.colors.surface,
                checkedTrackColor = OrbitTheme.colors.primary,
                uncheckedThumbColor = OrbitTheme.colors.textSecondary,
                uncheckedTrackColor = OrbitTheme.colors.surface
            )
        )
    }
}

@Composable
private fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = OrbitTheme.colors.secondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OrbitTheme.colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = OrbitTheme.colors.textSecondary)
    }
}

@Composable
private fun SettingsDropdownItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    currentValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = OrbitTheme.colors.tertiary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OrbitTheme.colors.textPrimary)
            Text(subtitle, fontSize = 12.sp, color = OrbitTheme.colors.textSecondary)
        }
        Text(currentValue, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OrbitTheme.colors.primary)
        Icon(Icons.Default.ArrowDropDown, contentDescription = "Expand", tint = OrbitTheme.colors.textSecondary)

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(OrbitTheme.colors.surfaceCard)
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt, color = OrbitTheme.colors.textPrimary) },
                    onClick = {
                        onOptionSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsInfoItem(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = TextSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary)
    }
}

@Composable
private fun SettingsSliderItem(
    icon: ImageVector,
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = OrbitTheme.colors.secondary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OrbitTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = OrbitTheme.colors.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = OrbitTheme.colors.primary,
                activeTrackColor = OrbitTheme.colors.primary,
                inactiveTrackColor = OrbitTheme.colors.surface
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SettingsColorPickerItem(
    icon: ImageVector,
    title: String,
    selectedColor: Long,
    onColorSelected: (Long) -> Unit
) {
    val presetColors = listOf(
        0xFF00FFFFL to "青碧",
        0xFF00F5D4L to "极光",
        0xFF4361EEL to "赛博蓝",
        0xFF5E72E4L to "星际蓝",
        0xFF7C4DFFL to "魅惑紫",
        0xFFFF007FL to "霓虹粉",
        0xFFFF0055L to "烈焰红",
        0xFFFF7700L to "日光橙",
        0xFFFFBE0BL to "闪电金",
        0xFFFFFFFFL to "纯白"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = title, tint = OrbitTheme.colors.tertiary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OrbitTheme.colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            // 当前选中颜色圆球指示器
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color(selectedColor))
                    .border(
                        width = 2.dp,
                        color = OrbitTheme.colors.textPrimary.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 一排高光霓虹色块方便用户快捷点击
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            presetColors.forEach { (colorVal, _) ->
                val isSelected = (selectedColor == colorVal)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(colorVal))
                        .then(
                            if (isSelected) {
                                Modifier.border(2.5.dp, OrbitTheme.colors.primary, CircleShape)
                            } else {
                                Modifier.border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            }
                        )
                        .clickable { onColorSelected(colorVal) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (colorVal == 0xFFFFFFFFL) Color.Black else Color.White)
                        )
                    }
                }
            }
        }
    }
}
