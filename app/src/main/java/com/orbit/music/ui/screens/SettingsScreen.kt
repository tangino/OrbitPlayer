package com.orbit.music.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orbit.music.R
import com.orbit.music.audio.ShuffleStrategy
import com.orbit.music.ui.components.ColorPickerDialog
import com.orbit.music.ui.components.MeshGradientBackground
import com.orbit.music.ui.theme.*
import com.orbit.music.ui.viewmodel.EqualizerViewModel
import com.orbit.music.ui.viewmodel.MusicPlayerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    viewModel: EqualizerViewModel,
    musicViewModel: MusicPlayerViewModel? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAudioSourceManager by remember { mutableStateOf(false) }
    var showCloudPlaylistSyncDialog by remember { mutableStateOf(false) }

    if (showAudioSourceManager) {
        AudioSourceManagementScreen(
            onBack = { showAudioSourceManager = false }
        )
        return
    }

    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var editingColorIndex by remember { mutableIntStateOf(1) }
    var importText by remember { mutableStateOf("") }
    var exportedJson by remember { mutableStateOf("") }

    var showAddFolderDialog by remember { mutableStateOf(false) }
    var showClearPlayCountsDialog by remember { mutableStateOf(false) }
    var isAddingIncludedFolder by remember { mutableStateOf(true) }
    var customFolderPath by remember { mutableStateOf("") }
    var customSolidHexInput by remember { mutableStateOf("") }
    var showSolidColorPickerDialog by remember { mutableStateOf(false) }
    var solidColorToDelete by remember { mutableStateOf<Long?>(null) }

    var showGradientColorPickerDialog by remember { mutableStateOf(false) }
    var editingGradientColorIndex by remember { mutableIntStateOf(0) }
    var isAddingNewGradientColor by remember { mutableStateOf(false) }

    // 折叠展开状态管理 (支持记住状态与一键全部展开/折叠)
    var isThemeExpanded by rememberSaveable { mutableStateOf(true) }
    var isOnlineExpanded by rememberSaveable { mutableStateOf(true) }
    var isBgExpanded by rememberSaveable { mutableStateOf(true) }
    var isTrailExpanded by rememberSaveable { mutableStateOf(false) }
    var isVisualizerExpanded by rememberSaveable { mutableStateOf(true) }
    var isLangStartupExpanded by rememberSaveable { mutableStateOf(false) }
    var isAudioLibExpanded by rememberSaveable { mutableStateOf(true) }
    var isBackupExpanded by rememberSaveable { mutableStateOf(false) }
    var isDeviceInfoExpanded by rememberSaveable { mutableStateOf(false) }

    val includedFolders by musicViewModel?.includedFolders?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val excludedFolders by musicViewModel?.excludedFolders?.collectAsState() ?: remember { mutableStateOf(emptySet()) }
    val isScanning by musicViewModel?.isScanning?.collectAsState() ?: remember { mutableStateOf(false) }
    val playlists by musicViewModel?.playlists?.collectAsState() ?: remember { mutableStateOf(emptyList()) }

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
                actions = {
                    val anyExpanded = isThemeExpanded || isOnlineExpanded || isBgExpanded || isTrailExpanded || isVisualizerExpanded || isLangStartupExpanded || isAudioLibExpanded || isBackupExpanded || isDeviceInfoExpanded
                    TextButton(
                        onClick = {
                            val target = !anyExpanded
                            isThemeExpanded = target
                            isOnlineExpanded = target
                            isBgExpanded = target
                            isTrailExpanded = target
                            isVisualizerExpanded = target
                            isLangStartupExpanded = target
                            isAudioLibExpanded = target
                            isBackupExpanded = target
                            isDeviceInfoExpanded = target
                        }
                    ) {
                        Icon(
                            imageVector = if (anyExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                            contentDescription = null,
                            tint = OrbitTheme.colors.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (anyExpanded) "全部折叠" else "全部展开",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrbitTheme.colors.primary
                        )
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
            // 1. Theme 外观与显示布局设置
            item {
                val themeOptions = listOf(
                    "system" to stringResource(R.string.theme_system),
                    "dark" to stringResource(R.string.theme_dark),
                    "light" to stringResource(R.string.theme_light)
                )
                val currentThemeLabel = themeOptions.find { it.first == uiState.themeMode }?.second
                    ?: stringResource(R.string.theme_system)

                CollapsibleSettingsCard(
                    icon = Icons.Default.Brightness4,
                    title = stringResource(R.string.theme_title),
                    subtitle = "当前主题: $currentThemeLabel · 缩放 ${uiState.uiScaleMode}",
                    isExpanded = isThemeExpanded,
                    onToggleExpand = { isThemeExpanded = !isThemeExpanded }
                ) {
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

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    val uiScaleOptions = listOf(
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_AUTO to stringResource(R.string.ui_scale_auto),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_080 to stringResource(R.string.ui_scale_080),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_090 to stringResource(R.string.ui_scale_090),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_100 to stringResource(R.string.ui_scale_100),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_110 to stringResource(R.string.ui_scale_110),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_125 to stringResource(R.string.ui_scale_125),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_150 to stringResource(R.string.ui_scale_150),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_175 to stringResource(R.string.ui_scale_175),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_200 to stringResource(R.string.ui_scale_200),
                        com.orbit.music.ui.utils.UiScaleHelper.MODE_225 to stringResource(R.string.ui_scale_225)
                    )
                    val currentScaleLabel = uiScaleOptions.find { it.first == uiState.uiScaleMode }?.second
                        ?: stringResource(R.string.ui_scale_auto)

                    SettingsDropdownItem(
                        icon = Icons.Default.FitScreen,
                        title = stringResource(R.string.ui_scale_title),
                        subtitle = stringResource(R.string.ui_scale_subtitle),
                        currentValue = currentScaleLabel,
                        options = uiScaleOptions.map { it.second },
                        onOptionSelected = { selectedLabel ->
                            val mode = uiScaleOptions.find { it.second == selectedLabel }?.first ?: "auto"
                            viewModel.setUiScaleMode(mode)
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    SettingsSwitchItem(
                        icon = Icons.Default.TabletAndroid,
                        title = "平板与车机大屏专属 UI",
                        subtitle = "启用左侧分栏导航与大屏网格自适应，并锁定横屏优化车机显示与触控",
                        checked = uiState.isTabletLandscapeModeEnabled,
                        onCheckedChange = { viewModel.setTabletLandscapeModeEnabled(it) }
                    )
                }
            }

            // 2. 在线音源管理
            item {
                val sourceManager = remember { com.orbit.music.data.online.engine.SourceScriptManager.getInstance(context) }
                val activeScript by sourceManager.activeScript.collectAsState()
                val scripts by sourceManager.scripts.collectAsState()
                val preferredQuality by sourceManager.preferredQuality.collectAsState()

                val qualityLabel = when (preferredQuality) {
                    "flac24bit" -> "母带 Hi-Res"
                    "flac" -> "无损 FLAC"
                    "320k" -> "高品 320K"
                    else -> "标准 128K"
                }

                CollapsibleSettingsCard(
                    icon = Icons.Default.CloudDownload,
                    title = "在线音乐与音源管理",
                    subtitle = if (activeScript != null) "活动音源: ${activeScript?.name} · $qualityLabel" else "官方直链模式 (共 ${scripts.size} 个音源) · $qualityLabel",
                    badgeText = if (activeScript != null) "已启用音源" else null,
                    isExpanded = isOnlineExpanded,
                    onToggleExpand = { isOnlineExpanded = !isOnlineExpanded }
                ) {
                    SettingsActionItem(
                        icon = Icons.Default.CloudDownload,
                        title = "在线音源与脚本管理",
                        subtitle = if (activeScript != null) "活动音源: ${activeScript?.name} (v${activeScript?.version}) · $qualityLabel" else "官方直链兜底模式 (共 ${scripts.size} 个音源) · $qualityLabel",
                        onClick = { showAudioSourceManager = true }
                    )
                }
            }

            // 3. 个性化背景与视觉效果 (纯色、多色流光渐变、壁纸毛玻璃)
            item {
                val bgSummary = when {
                    uiState.customBackgroundPath != null -> "自定义壁纸 (${if (uiState.backgroundBlurStyle == "frosted_glass") "毛玻璃" else "高斯模糊"})"
                    uiState.isGradientEnabled -> "多色流光渐变 (${uiState.customGradientColors.size}色)"
                    uiState.customSolidBackgroundColor != null -> "自定义纯色背景"
                    else -> "默认背景底色"
                }

                CollapsibleSettingsCard(
                    icon = Icons.Default.Wallpaper,
                    title = "个性化背景与视觉效果",
                    subtitle = bgSummary,
                    badgeText = if (uiState.isGradientEnabled || uiState.customBackgroundPath != null) "已自定义" else null,
                    isExpanded = isBgExpanded,
                    onToggleExpand = { isBgExpanded = !isBgExpanded }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 子分类 A: 自定义纯色背景
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OrbitTheme.colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.custom_solid_background_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrbitTheme.colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.custom_solid_background_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                        }

                        // 精选经典预设色块排布
                        val isEnLocale = remember {
                            context.resources.configuration.locales[0].language.lowercase().startsWith("en")
                        }
                        val solidColorPresets = remember(isEnLocale) {
                            listOf(
                                0xFF000000L to if (isEnLocale) "AMOLED Black" else "AMOLED 纯黑",
                                0xFF131D2EL to if (isEnLocale) "Deep Navy" else "深邃深蓝",
                                0xFF1A1B26L to if (isEnLocale) "Tokyo Night" else "东京暗夜",
                                0xFF1E1E2EL to if (isEnLocale) "Dark Mocha" else "摩卡深紫",
                                0xFF15221BL to if (isEnLocale) "Dark Forest" else "暗夜苍绿",
                                0xFF261924L to if (isEnLocale) "Plum Wine" else "暗梅深绛",
                                0xFF2E3440L to if (isEnLocale) "Nord Frost" else "极地灰蓝",
                                0xFF384959L to if (isEnLocale) "Slate Blue" else "雾霾石蓝",
                                0xFF3B4D3EL to if (isEnLocale) "Sage Green" else "松石灰绿",
                                0xFF4E3D35L to if (isEnLocale) "Dark Walnut" else "复古胡桃",
                                0xFF4A3C52L to if (isEnLocale) "Smoky Lilac" else "烟熏丁香",
                                0xFF5C3B3CL to if (isEnLocale) "Muted Rouge" else "干枯玫瑰",
                                0xFFF5F5F7L to if (isEnLocale) "Pure Ivory" else "极简象牙",
                                0xFFE8ECEFL to if (isEnLocale) "Glacier Mist" else "冰川晨雾",
                                0xFFF4EDE4L to if (isEnLocale) "Warm Cream" else "暖阳米杏",
                                0xFFEBF2EBL to if (isEnLocale) "Mint Dew" else "薄荷柔露"
                            )
                        }

                        // 预设与自选色块横向滑动列表
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            // 1. 调色盘快速添加入口卡片
                            item {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { showSolidColorPickerDialog = true }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        OrbitTheme.colors.primary.copy(alpha = 0.18f),
                                                        OrbitTheme.colors.secondary.copy(alpha = 0.18f),
                                                        OrbitTheme.colors.tertiary.copy(alpha = 0.18f)
                                                    )
                                                )
                                            )
                                            .border(
                                                width = 1.2.dp,
                                                brush = Brush.linearGradient(
                                                    listOf(
                                                        OrbitTheme.colors.primary,
                                                        OrbitTheme.colors.secondary,
                                                        OrbitTheme.colors.tertiary
                                                    )
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = stringResource(R.string.custom_solid_color_picker),
                                            tint = OrbitTheme.colors.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = stringResource(R.string.custom_solid_color_picker),
                                        fontSize = 10.sp,
                                        color = OrbitTheme.colors.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // 2. 用户自选颜色
                            items(uiState.customUserSolidColors) { userColor ->
                                val isSelected = uiState.customSolidBackgroundColor == userColor
                                val isDarkPreset = remember(userColor) {
                                    val r = ((userColor shr 16) and 0xFF) / 255f
                                    val g = ((userColor shr 8) and 0xFF) / 255f
                                    val b = (userColor and 0xFF) / 255f
                                    (0.299f * r + 0.587f * g + 0.114f * b) < 0.5f
                                }
                                val hexLabel = remember(userColor) {
                                    String.format("#%06X", (userColor and 0x00FFFFFFL))
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.combinedClickable(
                                        onClick = {
                                            viewModel.setCustomSolidBackgroundColor(userColor)
                                            customSolidHexInput = hexLabel
                                            Toast.makeText(context, context.getString(R.string.custom_solid_bg_success), Toast.LENGTH_SHORT).show()
                                        },
                                        onLongClick = {
                                            solidColorToDelete = userColor
                                        }
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(userColor))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) OrbitTheme.colors.primary else Color.White.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (isDarkPreset) Color.White else Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = hexLabel,
                                        fontSize = 9.5.sp,
                                        color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }

                            // 3. 经典系统预设色块
                            items(solidColorPresets) { (presetColor, label) ->
                                val isSelected = uiState.customSolidBackgroundColor == presetColor
                                val isDarkPreset = remember(presetColor) {
                                    val r = ((presetColor shr 16) and 0xFF) / 255f
                                    val g = ((presetColor shr 8) and 0xFF) / 255f
                                    val b = (presetColor and 0xFF) / 255f
                                    (0.299f * r + 0.587f * g + 0.114f * b) < 0.5f
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable {
                                        viewModel.setCustomSolidBackgroundColor(presetColor)
                                        Toast.makeText(context, context.getString(R.string.custom_solid_bg_success), Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(presetColor))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) OrbitTheme.colors.primary else Color.White.copy(alpha = 0.25f),
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = if (isDarkPreset) Color.White else Color.Black,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = label,
                                        fontSize = 10.sp,
                                        color = if (isSelected) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        // 颜色代码自定义输入栏 (HEX)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val cleanInput = customSolidHexInput.trim().removePrefix("#")
                            val liveColor = remember(cleanInput) {
                                try {
                                    when (cleanInput.length) {
                                        6 -> Color(0xFF000000L or cleanInput.toLong(16))
                                        8 -> Color(cleanInput.toLong(16))
                                        else -> null
                                    }
                                } catch (e: Exception) { null }
                            }

                            OutlinedTextField(
                                value = customSolidHexInput,
                                onValueChange = { customSolidHexInput = it },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.custom_solid_bg_input_hint),
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(liveColor ?: (uiState.customSolidBackgroundColor?.let { Color(it) } ?: OrbitTheme.colors.surfaceCard))
                                            .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                            .clickable { showSolidColorPickerDialog = true }
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { showSolidColorPickerDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = stringResource(R.string.custom_solid_color_picker),
                                            tint = OrbitTheme.colors.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = OrbitTheme.colors.surfaceCard,
                                    unfocusedContainerColor = OrbitTheme.colors.surfaceCard,
                                    focusedBorderColor = OrbitTheme.colors.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    cursorColor = OrbitTheme.colors.primary,
                                    focusedTextColor = OrbitTheme.colors.textPrimary,
                                    unfocusedTextColor = OrbitTheme.colors.textPrimary
                                ),
                                modifier = Modifier.weight(1f).height(48.dp)
                            )

                            Button(
                                onClick = {
                                    if (liveColor != null) {
                                        val argbLong = when (cleanInput.length) {
                                            6 -> 0xFF000000L or cleanInput.toLong(16)
                                            8 -> cleanInput.toLong(16)
                                            else -> 0L
                                        }
                                        viewModel.setCustomSolidBackgroundColor(argbLong)
                                        Toast.makeText(context, context.getString(R.string.custom_solid_bg_success), Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, context.getString(R.string.custom_solid_bg_invalid), Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(stringResource(R.string.custom_solid_bg_apply), fontSize = 12.sp)
                            }
                        }

                        if (uiState.customSolidBackgroundColor != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.clearCustomSolidBackgroundColor()
                                        customSolidHexInput = ""
                                        Toast.makeText(context, context.getString(R.string.custom_solid_bg_cleared), Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = OrbitTheme.colors.textSecondary
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.custom_solid_bg_reset),
                                        fontSize = 12.sp,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 子分类 B: 多颜色混合渐变背景 (Mesh Gradient)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OrbitTheme.colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Gradient,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.custom_gradient_background_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrbitTheme.colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.custom_gradient_background_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = uiState.isGradientEnabled,
                                onCheckedChange = { isEnabled ->
                                    viewModel.setCustomGradientEnabled(isEnabled)
                                    if (isEnabled) {
                                        viewModel.clearCustomSolidBackgroundColor()
                                    }
                                }
                            )
                        }

                        if (uiState.isGradientEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.custom_gradient_dynamic_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = OrbitTheme.colors.textPrimary
                                    )
                                    Text(
                                        text = stringResource(R.string.custom_gradient_dynamic_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = OrbitTheme.colors.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Switch(
                                    checked = uiState.isGradientDynamic,
                                    onCheckedChange = { viewModel.setGradientDynamic(it) }
                                )
                            }

                            // 渐变视窗预览
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(1.2.dp, OrbitTheme.colors.primary.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                            ) {
                                MeshGradientBackground(
                                    colors = uiState.customGradientColors,
                                    isDynamic = uiState.isGradientDynamic,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 10.dp, bottom = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.45f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (uiState.isGradientDynamic) "实时流光预览" else "静态渐变预览",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }

                            // 渐变颜色列表
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                contentPadding = PaddingValues(vertical = 2.dp)
                            ) {
                                items(uiState.customGradientColors.size) { index ->
                                    val colorVal = uiState.customGradientColors[index]
                                    val hex = remember(colorVal) { String.format("#%06X", (colorVal and 0x00FFFFFFL)) }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.combinedClickable(
                                            onClick = {
                                                editingGradientColorIndex = index
                                                isAddingNewGradientColor = false
                                                showGradientColorPickerDialog = true
                                            },
                                            onLongClick = {
                                                if (uiState.customGradientColors.size > 2) {
                                                    viewModel.removeGradientColor(index)
                                                    Toast.makeText(context, "已移除该颜色", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "至少需保留 2 种颜色", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(Color(colorVal))
                                                .border(2.dp, OrbitTheme.colors.primary.copy(alpha = 0.6f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = hex,
                                            fontSize = 9.5.sp,
                                            color = OrbitTheme.colors.textSecondary
                                        )
                                    }
                                }

                                if (uiState.customGradientColors.size < 4) {
                                    item {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable {
                                                isAddingNewGradientColor = true
                                                showGradientColorPickerDialog = true
                                            }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                                    .background(OrbitTheme.colors.surface)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = OrbitTheme.colors.primary.copy(alpha = 0.5f),
                                                        shape = CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Add,
                                                    contentDescription = stringResource(R.string.custom_gradient_add_color),
                                                    tint = OrbitTheme.colors.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(R.string.custom_gradient_add_color),
                                                fontSize = 9.5.sp,
                                                color = OrbitTheme.colors.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )

                        // 子分类 C: 自定义壁纸与毛玻璃模糊
                        val hasBg = uiState.customBackgroundPath != null && File(uiState.customBackgroundPath!!).exists()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(OrbitTheme.colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wallpaper,
                                    contentDescription = null,
                                    tint = OrbitTheme.colors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.custom_background_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OrbitTheme.colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.custom_background_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { backgroundPickerLauncher.launch("image/*") },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(stringResource(R.string.btn_choose_background), fontSize = 12.sp)
                            }
                        }

                        if (hasBg) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                            .size(40.dp)
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
                                    Text(stringResource(R.string.btn_reset_background), fontSize = 12.sp, color = OrbitTheme.colors.danger)
                                }
                            }

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

                            SettingsSliderItem(
                                icon = Icons.Default.BlurCircular,
                                title = stringResource(R.string.background_blur_radius_title),
                                valueText = String.format(java.util.Locale.US, "%.0f dp", uiState.backgroundBlurRadius),
                                value = uiState.backgroundBlurRadius,
                                valueRange = 0f..50f,
                                onValueChange = { viewModel.setBackgroundBlurRadius(it) }
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
            }

            // 4. 播放进度条拖尾样式设置与个性化调节
            item {
                val trailOptions = listOf(
                    com.orbit.music.data.model.ProgressTrailStyle.NEON_PULSE.id to stringResource(R.string.trail_style_neon_pulse),
                    com.orbit.music.data.model.ProgressTrailStyle.COMET_HELIX.id to stringResource(R.string.trail_style_comet_helix),
                    com.orbit.music.data.model.ProgressTrailStyle.MINIMAL.id to stringResource(R.string.trail_style_minimal)
                )
                val currentTrailLabel = trailOptions.find { it.first == uiState.progressTrailStyle }?.second
                    ?: stringResource(R.string.trail_style_neon_pulse)

                CollapsibleSettingsCard(
                    icon = Icons.Default.Timeline,
                    title = stringResource(R.string.progress_trail_style_title),
                    subtitle = "当前样式: $currentTrailLabel · 个性化双色调色与粗细调节",
                    badgeText = if (uiState.progressTrailStyle != com.orbit.music.data.model.ProgressTrailStyle.MINIMAL.id) "动态光效" else null,
                    isExpanded = isTrailExpanded,
                    onToggleExpand = { isTrailExpanded = !isTrailExpanded }
                ) {
                    SettingsDropdownItem(
                        icon = Icons.Default.Timeline,
                        title = stringResource(R.string.progress_trail_style_title),
                        subtitle = stringResource(R.string.progress_trail_style_subtitle),
                        currentValue = currentTrailLabel,
                        options = trailOptions.map { it.second },
                        onOptionSelected = { selectedLabel ->
                            val styleId = trailOptions.find { it.second == selectedLabel }?.first
                                ?: com.orbit.music.data.model.ProgressTrailStyle.NEON_PULSE.id
                            viewModel.setProgressTrailStyle(styleId)
                        }
                    )

                    if (uiState.progressTrailStyle != com.orbit.music.data.model.ProgressTrailStyle.MINIMAL.id) {
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

            // 5. 频谱律动可视化 (Poweramp 风格) 设置
            item {
                val styleOptions = remember {
                    com.orbit.music.data.model.VisualizerStyle.values().map { style ->
                        style to style.titleRes
                    }
                }
                val currentStyleName = styleOptions.find { it.first == uiState.visualizerStyle }?.second?.let { stringResource(it) }
                    ?: stringResource(R.string.visualizer_style_bars_with_peaks)

                val visualizerSummary = if (uiState.visualizerEnabled) {
                    "已开启 · 形态: $currentStyleName · ${if (uiState.visualizerSingleColor) "单色模式" else "双色渐变"}"
                } else {
                    "已关闭 (开启后在播放界面实时展示频谱律动)"
                }

                CollapsibleSettingsCard(
                    icon = Icons.Default.GraphicEq,
                    title = stringResource(R.string.section_spectrum_visualizer),
                    subtitle = visualizerSummary,
                    badgeText = if (uiState.visualizerEnabled) "开启中" else null,
                    isExpanded = isVisualizerExpanded,
                    onToggleExpand = { isVisualizerExpanded = !isVisualizerExpanded }
                ) {
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
                        val currentStyleDropdownLabel = styleOptions.find { it.first == uiState.visualizerStyle }?.second?.let { stringResource(it) }
                            ?: stringResource(R.string.visualizer_style_bars_with_peaks)

                        SettingsDropdownItem(
                            icon = Icons.Default.AutoGraph,
                            title = stringResource(R.string.visualizer_style_title),
                            subtitle = stringResource(R.string.visualizer_style_subtitle),
                            currentValue = currentStyleDropdownLabel,
                            options = styleOptions.map { stringResource(it.second) },
                            onOptionSelected = { selectedLabel ->
                                val target = styleOptions.find { context.getString(it.second) == selectedLabel }?.first
                                    ?: com.orbit.music.data.model.VisualizerStyle.BARS_WITH_PEAKS
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
                        val colorOptions = com.orbit.music.data.model.VisualizerColorScheme.values().map { scheme ->
                            scheme to stringResource(scheme.titleRes)
                        }
                        val currentColorLabel = colorOptions.find { it.first == uiState.visualizerColorScheme }?.second
                            ?: stringResource(R.string.visualizer_color_follow_background)

                        SettingsDropdownItem(
                            icon = Icons.Default.Palette,
                            title = stringResource(R.string.visualizer_color_title),
                            subtitle = stringResource(R.string.visualizer_color_subtitle),
                            currentValue = currentColorLabel,
                            options = colorOptions.map { it.second },
                            onOptionSelected = { selectedLabel ->
                                val target = colorOptions.find { it.second == selectedLabel }?.first
                                    ?: com.orbit.music.data.model.VisualizerColorScheme.FOLLOW_BACKGROUND
                                viewModel.setVisualizerColorScheme(target)
                                if (target == com.orbit.music.data.model.VisualizerColorScheme.CUSTOM) {
                                    editingColorIndex = 1
                                    showColorPickerDialog = true
                                }
                            }
                        )

                        // 5. 自定义颜色与色卡快速点选区
                        val isCustomScheme = uiState.visualizerColorScheme == com.orbit.music.data.model.VisualizerColorScheme.CUSTOM
                        val activeColor1 = when (uiState.visualizerColorScheme) {
                            com.orbit.music.data.model.VisualizerColorScheme.FOLLOW_BACKGROUND -> uiState.backgroundExtractedLightColor ?: (OrbitTheme.colors.primary.toArgb().toLong() and 0xFFFFFFFFL)
                            com.orbit.music.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor
                            else -> uiState.visualizerColorScheme.primaryColor.toArgb().toLong() and 0xFFFFFFFFL
                        }
                        val activeColor2 = when (uiState.visualizerColorScheme) {
                            com.orbit.music.data.model.VisualizerColorScheme.FOLLOW_BACKGROUND -> uiState.backgroundExtractedDarkColor ?: (OrbitTheme.colors.secondary.toArgb().toLong() and 0xFFFFFFFFL)
                            com.orbit.music.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor2
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

            // 6. 语言与启动偏好
            item {
                val langOptions = listOf(
                    "system" to stringResource(R.string.language_system),
                    "zh" to stringResource(R.string.language_chinese),
                    "en" to stringResource(R.string.language_english)
                )
                val currentLangLabel = langOptions.find { it.first == uiState.selectedLanguage }?.second
                    ?: stringResource(R.string.language_system)

                val langStartupSummary = "语言: $currentLangLabel · ${if (uiState.launchAsEqualizerOnly) "仅EQ启动" else "完整播放器"} · ${if (uiState.persistentMiniPlayer) "迷你条常驻" else "自动折叠"}"

                CollapsibleSettingsCard(
                    icon = Icons.Default.Language,
                    title = "语言与启动偏好",
                    subtitle = langStartupSummary,
                    isExpanded = isLangStartupExpanded,
                    onToggleExpand = { isLangStartupExpanded = !isLangStartupExpanded }
                ) {
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

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

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

            // 7. 音频引擎与曲库管理
            item {
                val audioSummary = "采样率: ${uiState.sampleRate.toInt()} Hz · 包含文件夹: ${includedFolders.size} · 排除文件夹: ${excludedFolders.size}"

                CollapsibleSettingsCard(
                    icon = Icons.Default.LibraryMusic,
                    title = "音频引擎与曲库管理",
                    subtitle = audioSummary,
                    badgeText = if (isScanning) "曲库同步中..." else null,
                    isExpanded = isAudioLibExpanded,
                    onToggleExpand = { isAudioLibExpanded = !isAudioLibExpanded }
                ) {
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

                    if (musicViewModel != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                        // 0. Cover Flow 封面滑动惯性设置
                        val libraryUiState by musicViewModel.libraryUiState.collectAsState()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    musicViewModel.setCoverFlowInertiaEnabled(!libraryUiState.isCoverFlowInertiaEnabled)
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    text = stringResource(R.string.cover_flow_inertia_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = OrbitTheme.colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.cover_flow_inertia_desc),
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                            Switch(
                                checked = libraryUiState.isCoverFlowInertiaEnabled,
                                onCheckedChange = { musicViewModel.setCoverFlowInertiaEnabled(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = OrbitTheme.colors.primary,
                                    checkedTrackColor = OrbitTheme.colors.primary.copy(alpha = 0.35f)
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = OrbitTheme.colors.surfaceCard.copy(alpha = 0.5f)
                        )

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

                        // 4. 清除播放次数记录
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showClearPlayCountsDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.clear_play_counts_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = OrbitTheme.colors.textPrimary
                                )
                                Text(
                                    text = stringResource(R.string.clear_play_counts_desc),
                                    fontSize = 11.sp,
                                    color = OrbitTheme.colors.textSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = stringResource(R.string.clear_play_counts_title),
                                tint = OrbitTheme.colors.textSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // 8. 数据备份与多端云同步
            item {
                CollapsibleSettingsCard(
                    icon = Icons.Default.CloudSync,
                    title = "数据备份与多端云同步",
                    subtitle = "均衡器预设导入/导出 · 歌单多端云同步",
                    isExpanded = isBackupExpanded,
                    onToggleExpand = { isBackupExpanded = !isBackupExpanded }
                ) {
                    SettingsActionItem(
                        icon = Icons.Default.FileDownload,
                        title = stringResource(R.string.import_preset_title),
                        subtitle = stringResource(R.string.import_preset_subtitle),
                        onClick = {
                            importText = ""
                            showImportDialog = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    SettingsActionItem(
                        icon = Icons.Default.FileUpload,
                        title = stringResource(R.string.export_preset_title),
                        subtitle = stringResource(R.string.export_preset_subtitle),
                        onClick = {
                            exportedJson = viewModel.exportPresetsJson()
                            showExportDialog = true
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )

                    SettingsActionItem(
                        icon = Icons.Default.CloudSync,
                        title = "歌单多端云同步 (选择性备份 / 恢复)",
                        subtitle = "支持手机与车机自由勾选自建歌单及在线收藏歌单进行上传和下载",
                        onClick = { showCloudPlaylistSyncDialog = true }
                    )
                }
            }

            // 9. 设备规格与系统诊断
            item {
                val deviceModel = remember {
                    val manufacturer = android.os.Build.MANUFACTURER.replaceFirstChar {
                        if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
                    }
                    val model = android.os.Build.MODEL
                    if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
                }

                val osVersion = remember {
                    "Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
                }

                val appVersionName = remember {
                    try {
                        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                        pInfo.versionName ?: "0.2.1"
                    } catch (_: Exception) {
                        "0.2.1"
                    }
                }

                val effectiveDensity = androidx.compose.ui.platform.LocalDensity.current
                val currentConfiguration = androidx.compose.ui.platform.LocalConfiguration.current
                val (resolutionText, densityText) = remember(context, effectiveDensity, currentConfiguration) {
                    val metrics = com.orbit.music.ui.utils.UiScaleHelper.getRealDisplayMetrics(context)
                    var refreshRate = 60
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        try {
                            refreshRate = context.display?.refreshRate?.toInt() ?: 60
                        } catch (_: Exception) {}
                    }
                    val res = "${metrics.widthPixels} × ${metrics.heightPixels} px @ ${refreshRate}Hz"
                    val systemScaleFactor = String.format(java.util.Locale.US, "%.1f", metrics.density)
                    val effectiveScaleFactor = String.format(java.util.Locale.US, "%.2f", effectiveDensity.density)
                    val density = if (kotlin.math.abs(effectiveDensity.density - metrics.density) > 0.05f) {
                        "${metrics.densityDpi} DPI (${systemScaleFactor}x) · 生效: ${(effectiveDensity.density * 160).toInt()} DPI (${effectiveScaleFactor}x) · ${currentConfiguration.screenWidthDp} × ${currentConfiguration.screenHeightDp} dp"
                    } else {
                        "${metrics.densityDpi} DPI (${systemScaleFactor}x) · ${currentConfiguration.screenWidthDp} × ${currentConfiguration.screenHeightDp} dp"
                    }
                    res to density
                }

                val cpuAbi = remember {
                    android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "未知"
                }

                val copyDeviceInfo = remember(deviceModel, osVersion, resolutionText, densityText, cpuAbi) {
                    {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                        val infoText = buildString {
                            appendLine("[Orbit Player 设备规格识别]")
                            appendLine("- 设备型号: $deviceModel")
                            appendLine("- 系统版本: $osVersion")
                            appendLine("- 物理分辨率: $resolutionText")
                            appendLine("- 屏幕密度与视口: $densityText")
                            appendLine("- 处理器架构: $cpuAbi")
                        }
                        val clip = android.content.ClipData.newPlainText("Orbit Device Info", infoText.trimEnd())
                        clipboard?.setPrimaryClip(clip)
                        Toast.makeText(context, context.getString(R.string.device_info_copied), Toast.LENGTH_SHORT).show()
                    }
                }

                CollapsibleSettingsCard(
                    icon = Icons.Default.Devices,
                    title = "设备规格与系统诊断",
                    subtitle = "$deviceModel · $osVersion · v$appVersionName",
                    isExpanded = isDeviceInfoExpanded,
                    onToggleExpand = { isDeviceInfoExpanded = !isDeviceInfoExpanded }
                ) {
                    SettingsInfoItem(
                        icon = Icons.Default.Devices,
                        title = stringResource(R.string.device_model_title),
                        value = deviceModel,
                        onClick = copyDeviceInfo
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(
                        icon = Icons.Default.Android,
                        title = stringResource(R.string.system_version_title),
                        value = osVersion,
                        onClick = copyDeviceInfo
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(
                        icon = Icons.Default.AspectRatio,
                        title = stringResource(R.string.screen_resolution_title),
                        value = resolutionText,
                        onClick = copyDeviceInfo
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(
                        icon = Icons.Default.FitScreen,
                        title = stringResource(R.string.screen_density_title),
                        value = densityText,
                        onClick = copyDeviceInfo
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(
                        icon = Icons.Default.DeveloperBoard,
                        title = stringResource(R.string.cpu_architecture_title),
                        value = cpuAbi,
                        onClick = copyDeviceInfo
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
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
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(icon = Icons.Default.Memory, title = stringResource(R.string.dsp_engine_title), value = stringResource(R.string.dsp_engine_value))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(icon = Icons.Default.Speed, title = stringResource(R.string.dsp_latency_title), value = stringResource(R.string.dsp_latency_value))
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    SettingsInfoItem(icon = Icons.Default.Info, title = stringResource(R.string.version_title), value = appVersionName)
                }

                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
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
        val isCustomScheme = uiState.visualizerColorScheme == com.orbit.music.data.model.VisualizerColorScheme.CUSTOM
        val activeColor1 = when (uiState.visualizerColorScheme) {
            com.orbit.music.data.model.VisualizerColorScheme.FOLLOW_BACKGROUND -> uiState.backgroundExtractedLightColor ?: (OrbitTheme.colors.primary.toArgb().toLong() and 0xFFFFFFFFL)
            com.orbit.music.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor
            else -> uiState.visualizerColorScheme.primaryColor.toArgb().toLong() and 0xFFFFFFFFL
        }
        val activeColor2 = when (uiState.visualizerColorScheme) {
            com.orbit.music.data.model.VisualizerColorScheme.FOLLOW_BACKGROUND -> uiState.backgroundExtractedDarkColor ?: (OrbitTheme.colors.secondary.toArgb().toLong() and 0xFFFFFFFFL)
            com.orbit.music.data.model.VisualizerColorScheme.CUSTOM -> uiState.visualizerCustomColor2
            else -> uiState.visualizerColorScheme.secondaryColor.toArgb().toLong() and 0xFFFFFFFFL
        }
        val currentInitialColor = if (editingColorIndex == 1) activeColor1 else activeColor2
        val dialogTitle = if (editingColorIndex == 1) {
            stringResource(R.string.visualizer_color1_title)
        } else {
            stringResource(R.string.visualizer_color2_title)
        }

        key(editingColorIndex) {
            com.orbit.music.ui.components.ColorPickerDialog(
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

    // 自定义纯色背景 HSV 调色盘对话框
    if (showSolidColorPickerDialog) {
        val initialSolidColor = remember(customSolidHexInput, uiState.customSolidBackgroundColor) {
            val clean = customSolidHexInput.trim().removePrefix("#")
            try {
                when (clean.length) {
                    6 -> 0xFF000000L or clean.toLong(16)
                    8 -> clean.toLong(16)
                    else -> uiState.customSolidBackgroundColor ?: 0xFF121212L
                }
            } catch (e: Exception) {
                uiState.customSolidBackgroundColor ?: 0xFF121212L
            }
        }

        ColorPickerDialog(
            initialColor = initialSolidColor,
            customColors = uiState.customUserSolidColors,
            title = stringResource(R.string.custom_solid_color_picker),
            onColorConfirmed = { chosenColor ->
                viewModel.setCustomSolidBackgroundColor(chosenColor)
                customSolidHexInput = String.format("#%06X", (chosenColor and 0x00FFFFFFL))
                Toast.makeText(context, context.getString(R.string.custom_solid_bg_success), Toast.LENGTH_SHORT).show()
            },
            onSaveToCustomColors = { colorToAdd ->
                viewModel.addCustomUserSolidColor(colorToAdd)
                customSolidHexInput = String.format("#%06X", (colorToAdd and 0x00FFFFFFL))
                Toast.makeText(context, context.getString(R.string.custom_solid_bg_success), Toast.LENGTH_SHORT).show()
            },
            onRemoveCustomColor = { colorToRemove ->
                viewModel.removeCustomUserSolidColor(colorToRemove)
            },
            onDismissRequest = { showSolidColorPickerDialog = false }
        )
    }

    // 自定义渐变色 HSV 调色盘对话框 (2~4色选择与添加，完全独立于纯色色板)
    if (showGradientColorPickerDialog) {
        val initialGradientColor = remember(isAddingNewGradientColor, editingGradientColorIndex, uiState.customGradientColors) {
            if (isAddingNewGradientColor) {
                0xFF38BDF8L // 默认明亮青蓝
            } else {
                uiState.customGradientColors.getOrElse(editingGradientColorIndex) { 0xFF1E284AL }
            }
        }

        ColorPickerDialog(
            initialColor = initialGradientColor,
            customColors = emptyList(), // 渐变色选择器保持独立，不显示也不污染纯色背景色板
            title = if (isAddingNewGradientColor) "添加渐变颜色" else "修改渐变色 #${editingGradientColorIndex + 1}",
            onColorConfirmed = { chosenColor ->
                if (isAddingNewGradientColor) {
                    viewModel.addGradientColor(chosenColor)
                    Toast.makeText(context, "已添加新渐变色", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.updateGradientColor(editingGradientColorIndex, chosenColor)
                    Toast.makeText(context, "渐变色已更新", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveToCustomColors = null, // 禁用向纯色色板添加按钮，彻底隔绝两者的相互影响
            onRemoveCustomColor = null,
            onDismissRequest = { showGradientColorPickerDialog = false }
        )
    }

    // 移除自选颜色确认弹窗
    if (solidColorToDelete != null) {
        AlertDialog(
            onDismissRequest = { solidColorToDelete = null },
            title = { Text(stringResource(R.string.custom_solid_delete_color_title)) },
            text = { Text(stringResource(R.string.custom_solid_delete_color_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        solidColorToDelete?.let { viewModel.removeCustomUserSolidColor(it) }
                        solidColorToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete), color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { solidColorToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
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

    // 清除播放次数记录二次确认弹窗
    if (showClearPlayCountsDialog && musicViewModel != null) {
        AlertDialog(
            onDismissRequest = { showClearPlayCountsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.clear_play_counts_dialog_title),
                    fontWeight = FontWeight.Bold,
                    color = OrbitTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.clear_play_counts_dialog_msg),
                    color = OrbitTheme.colors.textSecondary,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearPlayCountsDialog = false
                        musicViewModel.clearAllPlayCounts()
                        Toast.makeText(
                            context,
                            context.getString(R.string.clear_play_counts_success),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrbitTheme.colors.danger)
                ) {
                    Text(
                        text = stringResource(R.string.clear_play_counts_confirm),
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearPlayCountsDialog = false }) {
                    Text(stringResource(R.string.cancel), color = OrbitTheme.colors.textSecondary)
                }
            },
            containerColor = OrbitTheme.colors.surfaceDialog
        )
    }

    if (showCloudPlaylistSyncDialog && musicViewModel != null) {
        com.orbit.music.ui.components.CloudPlaylistSyncDialog(
            playlists = playlists,
            onDismiss = { showCloudPlaylistSyncDialog = false },
            onSyncCompleted = {
                musicViewModel.refreshPlaylists()
            }
        )
    }
}

@Composable
private fun CollapsibleSettingsCard(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "arrow_rotation"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(OrbitTheme.colors.surfaceCard)
            .border(
                width = 1.dp,
                color = if (isExpanded) OrbitTheme.colors.primary.copy(alpha = 0.25f) else OrbitTheme.colors.surfaceBorder.copy(alpha = 0.35f),
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        // 卡片头部（点击可折叠/展开）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isExpanded) OrbitTheme.colors.primary.copy(alpha = 0.16f) else OrbitTheme.colors.surface
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isExpanded) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrbitTheme.colors.textPrimary
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(OrbitTheme.colors.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OrbitTheme.colors.primary
                            )
                        }
                    }
                }
                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        color = OrbitTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 折叠箭头指示器
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(OrbitTheme.colors.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = if (isExpanded) OrbitTheme.colors.primary else OrbitTheme.colors.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(rotationZ = rotation)
                )
            }
        }

        // 折叠展开内容区 (带平滑高度与淡入淡出动画)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(200))
        ) {
            Column {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = OrbitTheme.colors.primary,
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
    value: String,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = OrbitTheme.colors.textSecondary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = OrbitTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OrbitTheme.colors.textSecondary)
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
