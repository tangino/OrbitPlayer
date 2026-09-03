package com.antigravity.equalizer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.equalizer.R
import com.antigravity.equalizer.data.repository.AppProfile
import com.antigravity.equalizer.data.repository.AppProfileRepository
import com.antigravity.equalizer.ui.theme.*
import com.antigravity.equalizer.ui.viewmodel.EqualizerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: EqualizerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showAppProfileDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }

    val appProfiles by AppProfileRepository.instance.appProfiles.collectAsState()

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
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                    SettingsInfoItem(icon = Icons.Default.Memory, title = stringResource(R.string.dsp_engine_title), value = stringResource(R.string.dsp_engine_value))
                    HorizontalDivider(color = OrbitTheme.colors.gridLine)
                    SettingsInfoItem(icon = Icons.Default.Speed, title = stringResource(R.string.dsp_latency_title), value = stringResource(R.string.dsp_latency_value))
                    HorizontalDivider(color = OrbitTheme.colors.gridLine)
                    SettingsInfoItem(icon = Icons.Default.Info, title = stringResource(R.string.version_title), value = stringResource(R.string.version_value))
                }
                Spacer(modifier = Modifier.height(24.dp))
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
                                    modifier = Modifier.background(OrbitTheme.colors.surfaceCard)
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
            containerColor = OrbitTheme.colors.surfaceCard
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
            containerColor = OrbitTheme.colors.surfaceCard
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
            containerColor = OrbitTheme.colors.surfaceCard
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
