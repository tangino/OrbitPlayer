package com.antigravity.equalizer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.antigravity.equalizer.data.model.AppScreen
import com.antigravity.equalizer.ui.components.AppBackgroundLayer
import com.antigravity.equalizer.ui.components.MiniPlayerBar
import com.antigravity.equalizer.ui.screens.MainEqualizerScreen
import com.antigravity.equalizer.ui.screens.MusicLibraryScreen
import com.antigravity.equalizer.ui.screens.NowPlayingScreen
import com.antigravity.equalizer.ui.screens.ParametricEqScreen
import com.antigravity.equalizer.ui.screens.SettingsScreen
import com.antigravity.equalizer.ui.theme.MusicEqualizerTheme
import com.antigravity.equalizer.ui.viewmodel.EqualizerViewModel
import com.antigravity.equalizer.ui.viewmodel.MusicPlayerViewModel
import com.antigravity.equalizer.utils.LocaleHelper
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val equalizerViewModel: EqualizerViewModel by viewModels()
    private val musicPlayerViewModel: MusicPlayerViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        val savedLang = LocaleHelper.getSelectedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.applyLocale(newBase, savedLang))
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // 只要音频/存储权限授权成功，立即触发扫描
        val isAudioGranted = results[Manifest.permission.READ_MEDIA_AUDIO] == true ||
                results[Manifest.permission.READ_EXTERNAL_STORAGE] == true

        if (isAudioGranted) {
            musicPlayerViewModel.scanMedia()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()

        setContent {
            val uiState by equalizerViewModel.uiState.collectAsState()
            val playbackState by musicPlayerViewModel.playbackState.collectAsState()
            val libraryUiState by musicPlayerViewModel.libraryUiState.collectAsState()

            val targetLocale = when (uiState.selectedLanguage) {
                "zh" -> Locale.SIMPLIFIED_CHINESE
                "en" -> Locale.ENGLISH
                else -> Locale.getDefault()
            }

            val currentConfig = LocalConfiguration.current
            val updatedConfig = remember(targetLocale) {
                Configuration(currentConfig).apply {
                    setLocale(targetLocale)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        setLocales(LocaleList(targetLocale))
                    }
                }
            }

            val currentContext = LocalContext.current
            val localizedContext = remember(targetLocale) {
                currentContext.createConfigurationContext(updatedConfig)
            }

            // 记录页面返回栈轨迹：是否从正在播放页直接打开了均衡器页面
            var openedFromNowPlaying by rememberSaveable { mutableStateOf(false) }
            // 记录打开设置页之前的源页面（曲库还是均衡器主页），确保返回时准确恢复
            var previousScreenBeforeSettings by rememberSaveable { mutableStateOf(AppScreen.LIBRARY) }

            val handleBackFromMain = {
                if (uiState.launchAsEqualizerOnly) {
                    finish()
                } else if (openedFromNowPlaying) {
                    openedFromNowPlaying = false
                    equalizerViewModel.navigateTo(AppScreen.LIBRARY)
                    musicPlayerViewModel.setNowPlayingExpanded(true)
                } else {
                    equalizerViewModel.navigateTo(AppScreen.LIBRARY)
                }
            }

            val handleBackFromSettings = {
                if (uiState.launchAsEqualizerOnly) {
                    equalizerViewModel.navigateTo(AppScreen.MAIN)
                } else {
                    equalizerViewModel.navigateTo(previousScreenBeforeSettings)
                }
            }

            // 拦截全屏频谱最大化返回，优先退出最大化
            BackHandler(enabled = libraryUiState.isNowPlayingExpanded && uiState.isVisualizerMaximized) {
                equalizerViewModel.setVisualizerMaximized(false)
            }

            // 拦截全屏播放页手势返回，优先收起播放页
            BackHandler(enabled = libraryUiState.isNowPlayingExpanded && !uiState.isVisualizerMaximized) {
                musicPlayerViewModel.setNowPlayingExpanded(false)
                openedFromNowPlaying = false
            }

            // 全局各级页面系统返回手势（BackHandler）精准拦截
            BackHandler(enabled = !libraryUiState.isNowPlayingExpanded && uiState.currentScreen == AppScreen.MAIN) {
                handleBackFromMain()
            }
            BackHandler(enabled = !libraryUiState.isNowPlayingExpanded && uiState.currentScreen == AppScreen.PARAMETRIC) {
                equalizerViewModel.navigateTo(AppScreen.MAIN)
            }
            BackHandler(enabled = !libraryUiState.isNowPlayingExpanded && uiState.currentScreen == AppScreen.SETTINGS) {
                handleBackFromSettings()
            }

            val hasCustomBg = uiState.customBackgroundPath != null
            CompositionLocalProvider(
                LocalConfiguration provides updatedConfig,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this@MainActivity
            ) {
                MusicEqualizerTheme(
                    themeMode = uiState.themeMode,
                    hasCustomBackground = hasCustomBg
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppBackgroundLayer(
                            customBackgroundPath = uiState.customBackgroundPath,
                            blurRadius = uiState.backgroundBlurRadius,
                            blurStyle = uiState.backgroundBlurStyle,
                            dimAlpha = uiState.backgroundDimAlpha
                        )

                        when (uiState.currentScreen) {
                            AppScreen.LIBRARY -> {
                                MusicLibraryScreen(
                                    viewModel = musicPlayerViewModel,
                                    onOpenSettings = {
                                        previousScreenBeforeSettings = AppScreen.LIBRARY
                                        equalizerViewModel.navigateTo(AppScreen.SETTINGS)
                                    }
                                )
                            }
                            AppScreen.MAIN -> {
                                MainEqualizerScreen(
                                    viewModel = equalizerViewModel,
                                    onBackToLibrary = handleBackFromMain,
                                    onOpenSettings = {
                                        previousScreenBeforeSettings = AppScreen.MAIN
                                        equalizerViewModel.navigateTo(AppScreen.SETTINGS)
                                    }
                                )
                            }
                            AppScreen.PARAMETRIC -> {
                                ParametricEqScreen(
                                    viewModel = equalizerViewModel,
                                    onBack = { equalizerViewModel.navigateTo(AppScreen.MAIN) }
                                )
                            }
                            AppScreen.SETTINGS -> {
                                SettingsScreen(
                                    viewModel = equalizerViewModel,
                                    musicViewModel = musicPlayerViewModel,
                                    onBack = handleBackFromSettings
                                )
                            }
                        }

                        // 底部常驻播放条：根据用户配置与页面状态智能展示（展开全屏播放页时自动隐藏）
                        val showMiniPlayer = playbackState.currentSong != null &&
                                !libraryUiState.isNowPlayingExpanded &&
                                (uiState.persistentMiniPlayer || uiState.currentScreen == AppScreen.LIBRARY)

                        AnimatedVisibility(
                            visible = showMiniPlayer,
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(240)
                            ) + fadeIn(animationSpec = tween(200)),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(200)
                            ) + fadeOut(animationSpec = tween(160)),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = 680.dp)
                        ) {
                            MiniPlayerBar(
                                playbackState = playbackState,
                                onTogglePlay = { musicPlayerViewModel.togglePlayPause() },
                                onPlayNext = { musicPlayerViewModel.playNext() },
                                onPlayPrevious = { musicPlayerViewModel.playPrevious() },
                                onClick = { musicPlayerViewModel.setNowPlayingExpanded(true) }
                            )
                        }

                        // 全屏正在播放页面展开过渡
                        AnimatedVisibility(
                            visible = libraryUiState.isNowPlayingExpanded,
                            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            NowPlayingScreen(
                                viewModel = musicPlayerViewModel,
                                equalizerUiState = uiState,
                                onBack = {
                                    musicPlayerViewModel.setNowPlayingExpanded(false)
                                    openedFromNowPlaying = false
                                },
                                onOpenEqualizer = {
                                    openedFromNowPlaying = true
                                    musicPlayerViewModel.setNowPlayingExpanded(false)
                                    equalizerViewModel.navigateTo(AppScreen.MAIN)
                                },
                                onCycleVisualizerStyle = {
                                    equalizerViewModel.cycleVisualizerStyle()
                                },
                                onToggleCoverVisualizer = { show ->
                                    equalizerViewModel.setShowNowPlayingVisualizer(show)
                                },
                                onToggleVisualizerMaximized = { max ->
                                    equalizerViewModel.setVisualizerMaximized(max)
                                },
                                onToggleMaximizedShowCover = { show ->
                                    equalizerViewModel.setMaximizedShowCover(show)
                                },
                                onToggleMaximizedCoverPosition = { onRight ->
                                    equalizerViewModel.setMaximizedCoverOnRight(onRight)
                                },
                                onToggleMaximizedShowControls = { show ->
                                    equalizerViewModel.setMaximizedShowControls(show)
                                },
                                onSetMaximizedCoverAlpha = { alpha ->
                                    equalizerViewModel.setMaximizedCoverAlpha(alpha)
                                },
                                onToggleCoverInQueue = { show ->
                                    equalizerViewModel.setShowCoverInQueue(show)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        com.antigravity.equalizer.service.EqualizerService.start(this)

        // 若拥有读取权限且列表为空，自动刷新一次
        val hasAudioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasAudioPermission) {
            musicPlayerViewModel.scanMedia()
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // 媒体读取权限 (Android 13+ READ_MEDIA_AUDIO，低版本 READ_EXTERNAL_STORAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            @Suppress("DEPRECATION")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}
