package com.orbit.music.ui.floating

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Rect
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import coil.Coil
import coil.request.ImageRequest
import com.orbit.music.R
import com.orbit.music.audio.MusicPlayerManager
import com.orbit.music.data.model.Song
import com.orbit.music.data.provider.AudioCoverProvider
import com.orbit.music.ui.MainActivity
import com.orbit.music.utils.CoverHelper
import com.orbit.music.utils.LyricLine
import com.orbit.music.utils.LyricParser
import com.orbit.music.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 桌面与全局悬浮窗管理器：
 * 1. 呈现当前播放歌曲信息（封面、歌名、歌手、实时歌词）
 * 2. 极简播控：播放/暂停按钮居中叠放在黑胶唱片中央，微型关闭键贴右
 * 3. 灵动手势：左右滑动标题文本控件进行上一首/下一首切歌，轻触点击打开播放器主界面
 * 4. 任意拖动：除按钮外的空白区域均可自由拖拽悬浮窗，具备全屏防越界安全保护
 * 5. 全面自适应各种尺寸设备与横竖屏（车机大屏黄金大尺寸 540x90dp，平板 420x72dp，手机 282x50dp）
 * 6. 支持双指捏合无级自由缩放（Pinch-to-Scale）与双击快速切换放大模式，自动记忆缩放比例
 */
class FloatingWindowManager private constructor(private val context: Context) {

    /**
     * 悬浮窗尺寸档位枚举
     */
    enum class DeviceTier {
        PHONE,        // 手机尺寸（精致紧凑灵动胶囊 282x50dp）
        TABLET,       // 平板尺寸（大触控面积，舒适视距 420x72dp）
        CAR_OR_LARGE  // 车机/超大屏幕（驾驶视距黄金大尺寸 540x90dp，超大按钮与清晰歌词）
    }

    companion object {
        private const val TAG = "FloatingWindowManager"
        private const val PREFS_NAME = "floating_window_prefs"
        const val KEY_ENABLED = "key_floating_window_enabled"
        private const val KEY_POS_X = "key_floating_pos_x"
        private const val KEY_POS_Y = "key_floating_pos_y"
        private const val KEY_USER_SCALE = "key_floating_user_scale"

        @Volatile
        private var instance: FloatingWindowManager? = null

        fun getInstance(context: Context): FloatingWindowManager {
            return instance ?: synchronized(this) {
                instance ?: FloatingWindowManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 用户自定义缩放因子 (0.7f ~ 1.8f，默认 1.0f)
     */
    var userScaleFactor: Float = prefs.getFloat(KEY_USER_SCALE, 1.0f).coerceIn(0.7f, 1.8f)
        private set

    /**
     * 当前应用程序是否处于后台
     */
    var isAppInBackground: Boolean = false
        private set

    private var startedActivityCount = 0

    // 屏幕横竖屏与系统配置监听器
    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            handleConfigurationChanged(newConfig)
        }
        override fun onLowMemory() {}
        override fun onTrimMemory(level: Int) {}
    }

    init {
        (context.applicationContext as? Application)?.let { app ->
            app.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityStarted(activity: Activity) {
                        startedActivityCount++
                        if (startedActivityCount == 1) {
                            isAppInBackground = false
                            Log.d(TAG, "Application entered FOREGROUND: auto hiding floating window")
                            hide()
                        }
                    }

                    override fun onActivityStopped(activity: Activity) {
                        startedActivityCount--
                        if (startedActivityCount <= 0) {
                            startedActivityCount = 0
                            isAppInBackground = true
                            Log.d(TAG, "Application entered BACKGROUND: checking auto show floating window")
                            checkAndAutoShow()
                        }
                    }

                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                    override fun onActivityResumed(activity: Activity) {}
                    override fun onActivityPaused(activity: Activity) {}
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                    override fun onActivityDestroyed(activity: Activity) {}
                }
            )
            app.registerComponentCallbacks(componentCallbacks)
        }
    }

    private var floatingView: View? = null
    private var windowParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null
    private var lyricLoadJob: Job? = null
    private var coverLoadJob: Job? = null
    private var coverObserverJob: Job? = null

    private var currentSongId: Long = -1L
    private var currentLyricLines: List<LyricLine> = emptyList()
    private var lastLyricIndex: Int = -2

    // 控件引用
    private var rootCard: View? = null
    private var capsuleContainer: LinearLayout? = null
    private var coverContainer: FrameLayout? = null
    private var ivCover: ImageView? = null
    private var btnPlayPause: ImageButton? = null

    private var infoContainer: LinearLayout? = null
    private var tvTitle: TextView? = null
    private var tvTitleDot: TextView? = null
    private var tvArtist: TextView? = null
    private var tvLyric: TextView? = null
    private var tvLyricNext: TextView? = null

    private var btnClose: ImageButton? = null
    private var coverRotateAnimator: ObjectAnimator? = null

    /**
     * 判断用户是否在设置中开启了悬浮窗功能（默认开启）
     */
    fun isEnabled(): Boolean {
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    /**
     * 更新悬浮窗开关设置
     */
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * 设置自定义缩放因子 (0.7f ~ 1.8f)
     */
    fun setUserScale(scale: Float) {
        val clamped = scale.coerceIn(0.7f, 1.8f)
        if (abs(userScaleFactor - clamped) > 0.01f) {
            userScaleFactor = clamped
            prefs.edit().putFloat(KEY_USER_SCALE, clamped).apply()
            floatingView?.let { view ->
                applyAdaptiveDimensions(view)
                windowParams?.let { params ->
                    clampCoordinates(params, view)
                    try {
                        if (view.isAttachedToWindow) {
                            windowManager.updateViewLayout(view, params)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * 判断当前系统是否属于车载系统环境 (支持原生车机、Automotive系统及各类车机模拟器)
     */
    fun isCarEnvironment(ctx: Context = context): Boolean {
        val uiMode = ctx.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
        val hasAutomotive = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        val isCarBuild = Build.DEVICE.contains("car", ignoreCase = true) ||
                Build.MODEL.contains("car", ignoreCase = true) ||
                Build.PRODUCT.contains("car", ignoreCase = true) ||
                Build.FINGERPRINT.contains("car", ignoreCase = true)
        return uiMode == Configuration.UI_MODE_TYPE_CAR || hasAutomotive || isCarBuild
    }

    /**
     * 基于真实屏幕物理边界与设备特征，智能识别适用的悬浮窗尺寸档位
     */
    fun getDeviceTier(): DeviceTier {
        if (isCarEnvironment()) {
            return DeviceTier.CAR_OR_LARGE
        }

        val bounds = getScreenBounds()
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        val screenWDp = bounds.width() / density
        val screenHDp = bounds.height() / density
        val smallestWidthDp = minOf(screenWDp, screenHDp)
        val isLandscape = screenWDp > screenHDp

        return when {
            // 车机或超大屏幕 (sw >= 700 或宽屏横屏 w >= 800 且 h >= 480，如 2560x1440 等车机/大屏环境)
            smallestWidthDp >= 700 || (isLandscape && screenWDp >= 800 && screenHDp >= 480) -> DeviceTier.CAR_OR_LARGE
            // 平板设备 (sw >= 600 或中大横屏 w >= 660 且 h >= 440)
            smallestWidthDp >= 600 || (isLandscape && screenWDp >= 660 && screenHDp >= 440) -> DeviceTier.TABLET
            // 手机设备：保持紧凑灵动小胶囊
            else -> DeviceTier.PHONE
        }
    }

    /**
     * 根据设置、前后台状态与权限自动判断是否显示悬浮窗
     */
    fun checkAndAutoShow() {
        if (!isEnabled()) {
            return
        }
        if (!isAppInBackground) {
            Log.d(TAG, "Application is in foreground, floating window will stay hidden")
            return
        }
        if (PermissionHelper.hasOverlayPermission(context)) {
            show()
        }
    }

    /**
     * 获取当前屏幕物理可视区域
     */
    private fun getScreenBounds(): Rect {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            Rect(metrics.bounds)
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            Rect(0, 0, dm.widthPixels, dm.heightPixels)
        }
    }

    /**
     * 保证坐标完全处于当前屏幕可视边界内，杜绝旋转或拖拽时飞出屏幕外
     */
    private fun clampCoordinates(params: WindowManager.LayoutParams, targetView: View? = floatingView) {
        val bounds = getScreenBounds()
        val density = context.resources.displayMetrics.density
        val tier = getDeviceTier()

        val expectedWidth = when (tier) {
            DeviceTier.CAR_OR_LARGE -> (380 * userScaleFactor * density).toInt()
            DeviceTier.TABLET -> (305 * userScaleFactor * density).toInt()
            DeviceTier.PHONE -> (210 * userScaleFactor * density).toInt()
        }
        val expectedHeight = when (tier) {
            DeviceTier.CAR_OR_LARGE -> (90 * userScaleFactor * density).toInt()
            DeviceTier.TABLET -> (72 * userScaleFactor * density).toInt()
            DeviceTier.PHONE -> (50 * userScaleFactor * density).toInt()
        }

        val vWidth = targetView?.width?.takeIf { it > 0 }
            ?: capsuleContainer?.width?.takeIf { it > 0 }
            ?: expectedWidth
        val vHeight = targetView?.height?.takeIf { it > 0 }
            ?: capsuleContainer?.height?.takeIf { it > 0 }
            ?: expectedHeight

        val maxX = (bounds.width() - vWidth).coerceAtLeast(0)
        val maxY = (bounds.height() - vHeight).coerceAtLeast(0)

        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    /**
     * 显示悬浮窗
     */
    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    fun show(force: Boolean = false) {
        if (!force && !isAppInBackground) {
            Log.d(TAG, "Skip showing floating window because app is in foreground")
            return
        }
        if (isShowing && floatingView != null) return
        if (!PermissionHelper.hasOverlayPermission(context)) {
            Log.w(TAG, "Cannot show floating window: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val defaultX = (32 * context.resources.displayMetrics.density).toInt()
            val defaultY = (160 * context.resources.displayMetrics.density).toInt()
            val savedX = prefs.getInt(KEY_POS_X, defaultX)
            val savedY = prefs.getInt(KEY_POS_Y, defaultY)

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = savedX
                y = savedY
            }
            windowParams = params

            val view = LayoutInflater.from(context).inflate(R.layout.layout_floating_window, null)
            floatingView = view

            bindViews(view)
            applyAdaptiveDimensions(view)
            clampCoordinates(params, view)
            setupTouchAndDrag(view, params)
            setupClickListeners()

            windowManager.addView(view, params)
            isShowing = true

            // 重置记录 ID，确保新 View 实例必然触发封面与播放信息的完整初次加载绑定
            currentSongId = -1L
            startObservingPlayback()
            Log.i(TAG, "Floating window displayed successfully at (${params.x}, ${params.y}) tier=${getDeviceTier()} scale=$userScaleFactor")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show floating window: ${e.message}", e)
            isShowing = false
            floatingView = null
        }
    }

    /**
     * 隐藏/移除悬浮窗
     */
    fun hide() {
        if (!isShowing && floatingView == null) return

        observeJob?.cancel()
        observeJob = null
        lyricLoadJob?.cancel()
        lyricLoadJob = null
        coverLoadJob?.cancel()
        coverLoadJob = null
        coverObserverJob?.cancel()
        coverObserverJob = null

        coverRotateAnimator?.cancel()
        coverRotateAnimator = null

        try {
            floatingView?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove floating window: ${e.message}", e)
        } finally {
            floatingView = null
            windowParams = null
            isShowing = false
            currentSongId = -1L
            currentLyricLines = emptyList()
            lastLyricIndex = -2
        }
    }

    private fun bindViews(root: View) {
        rootCard = root.findViewById(R.id.floating_root_card)
        capsuleContainer = root.findViewById(R.id.floating_capsule_container)
        coverContainer = root.findViewById(R.id.floating_cover_container)
        ivCover = root.findViewById(R.id.floating_cover)
        btnPlayPause = root.findViewById(R.id.floating_btn_play_pause)

        infoContainer = root.findViewById(R.id.floating_info_container)
        tvTitle = root.findViewById(R.id.floating_title)
        tvTitleDot = root.findViewById(R.id.floating_title_dot)
        tvArtist = root.findViewById(R.id.floating_artist)
        tvLyric = root.findViewById(R.id.floating_lyric)
        tvLyricNext = root.findViewById(R.id.floating_lyric_next)

        btnClose = root.findViewById(R.id.floating_btn_close)

        // 为封面设置正圆唱片裁切
        ivCover?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        ivCover?.clipToOutline = true

        // 启用 TextView 的跑马灯滚动
        tvTitle?.isSelected = true
        tvLyric?.isSelected = true
        tvLyricNext?.isSelected = true
    }

    /**
     * 响应系统屏幕旋转或设备配置变化
     */
    private fun handleConfigurationChanged(newConfig: Configuration) {
        val view = floatingView ?: return
        val params = windowParams ?: return
        if (!isShowing || !view.isAttachedToWindow) return

        Log.d(TAG, "onConfigurationChanged orientation=${newConfig.orientation}, updating adaptive dimensions")

        view.post {
            applyAdaptiveDimensions(view)
            clampCoordinates(params, view)
            try {
                if (view.isAttachedToWindow) {
                    windowManager.updateViewLayout(view, params)
                    prefs.edit()
                        .putInt(KEY_POS_X, params.x)
                        .putInt(KEY_POS_Y, params.y)
                        .apply()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update floating window on config change: ${e.message}")
            }
        }
    }

    /**
     * 动态根据设备档位、缩放系数与横竖屏模式自适应调整各个控件尺寸与圆角
     */
    private fun applyAdaptiveDimensions(root: View) {
        val tier = getDeviceTier()
        val density = context.resources.displayMetrics.density
        val scale = userScaleFactor

        // 1. 各档位基准参数矩阵 (车机紧凑大屏 360x92dp，平板 285x74dp，手机 205x54dp)
        val (baseCapsuleWidthDp, baseCapsuleHeightDp) = when (tier) {
            DeviceTier.CAR_OR_LARGE -> Pair(360f, 92f)
            DeviceTier.TABLET -> Pair(285f, 74f)
            DeviceTier.PHONE -> Pair(205f, 54f)
        }

        val baseCoverSizeDp = when (tier) {
            DeviceTier.CAR_OR_LARGE -> 76f
            DeviceTier.TABLET -> 60f
            DeviceTier.PHONE -> 42f
        }

        // 播放/暂停按键尺寸与右间距 (位于关闭按钮左侧紧凑排布)
        val (basePlaySizeDp, basePlayPadDp, basePlayMarginEndDp) = when (tier) {
            DeviceTier.CAR_OR_LARGE -> Triple(36f, 6f, 8f)
            DeviceTier.TABLET -> Triple(28f, 4f, 6f)
            DeviceTier.PHONE -> Triple(22f, 3f, 4f)
        }

        val (baseTitleSizeSp, baseArtistSizeSp, baseLyricSizeSp) = when (tier) {
            DeviceTier.CAR_OR_LARGE -> Triple(16.5f, 13.5f, 15f)
            DeviceTier.TABLET -> Triple(13.5f, 11.5f, 12.5f)
            DeviceTier.PHONE -> Triple(10.5f, 9f, 9.8f)
        }

        val (baseCloseSizeDp, baseClosePadDp) = when (tier) {
            DeviceTier.CAR_OR_LARGE -> Pair(32f, 5f)
            DeviceTier.TABLET -> Pair(26f, 4f)
            DeviceTier.PHONE -> Pair(18f, 2f)
        }

        val (basePadStartDp, basePadEndDp, basePadVDp) = when (tier) {
            DeviceTier.CAR_OR_LARGE -> Triple(8f, 8f, 8f)
            DeviceTier.TABLET -> Triple(7f, 6f, 6f)
            DeviceTier.PHONE -> Triple(5f, 5f, 4.5f)
        }

        val (baseInfoMarginStartDp, baseInfoMarginEndDp) = when (tier) {
            DeviceTier.CAR_OR_LARGE -> Pair(10f, 6f)
            DeviceTier.TABLET -> Pair(8f, 5f)
            DeviceTier.PHONE -> Pair(6f, 4f)
        }

        val baseTitleMaxWDp = when (tier) {
            DeviceTier.CAR_OR_LARGE -> 150f
            DeviceTier.TABLET -> 115f
            DeviceTier.PHONE -> 80f
        }

        // 2. 结合缩放因子应用到胶囊外壳
        val capsuleW = (baseCapsuleWidthDp * scale * density).toInt()
        val capsuleH = (baseCapsuleHeightDp * scale * density).toInt()

        capsuleContainer?.let { container ->
            val lp = container.layoutParams
            lp.width = capsuleW
            lp.height = capsuleH
            container.layoutParams = lp
            container.setPadding(
                (basePadStartDp * scale * density).toInt(),
                (basePadVDp * scale * density).toInt(),
                (basePadEndDp * scale * density).toInt(),
                (basePadVDp * scale * density).toInt()
            )
        }

        // 3. 更新黑胶封面尺寸
        coverContainer?.let { container ->
            val lp = container.layoutParams
            val cSize = (baseCoverSizeDp * scale * density).toInt()
            lp.width = cSize
            lp.height = cSize
            container.layoutParams = lp
        }

        // 4. 更新中部信息区域边距与字体大小
        infoContainer?.let { info ->
            (info.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.marginStart = (baseInfoMarginStartDp * scale * density).toInt()
                lp.marginEnd = (baseInfoMarginEndDp * scale * density).toInt()
                info.layoutParams = lp
            }
        }

        tvTitle?.let {
            it.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTitleSizeSp * scale)
            it.maxWidth = (baseTitleMaxWDp * scale * density).toInt()
        }
        tvTitleDot?.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseTitleSizeSp * scale * 0.8f)
        tvArtist?.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseArtistSizeSp * scale)
        tvLyric?.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseLyricSizeSp * scale)
        tvLyricNext?.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseLyricSizeSp * scale * 0.92f)

        // 5. 更新右侧控制按键：播放/暂停按键与关闭微键
        btnPlayPause?.let { btn ->
            val lp = btn.layoutParams as? ViewGroup.MarginLayoutParams ?: LinearLayout.LayoutParams(0, 0)
            val pSize = (basePlaySizeDp * scale * density).toInt()
            lp.width = pSize
            lp.height = pSize
            lp.marginEnd = (basePlayMarginEndDp * scale * density).toInt()
            btn.layoutParams = lp
            val p = (basePlayPadDp * scale * density).toInt()
            btn.setPadding(p, p, p, p)
        }

        btnClose?.let {
            val lp = it.layoutParams
            val s = (baseCloseSizeDp * scale * density).toInt()
            lp.width = s
            lp.height = s
            it.layoutParams = lp
            val p = (baseClosePadDp * scale * density).toInt()
            it.setPadding(p, p, p, p)
        }

        // 6. 为卡片设置半高全圆角裁切
        val card = rootCard ?: root.findViewById<View>(R.id.floating_root_card)
        card?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val radius = if (view.height > 0) view.height / 2f else capsuleH / 2f
                outline.setRoundRect(0, 0, view.width, view.height, radius.coerceAtLeast(1f))
            }
        }
        card?.clipToOutline = true
        card?.invalidateOutline()
    }

    private fun setupClickListeners() {
        val playerManager = MusicPlayerManager.getInstance(context)

        // 封面中央播放/暂停按钮
        btnPlayPause?.setOnClickListener {
            playerManager.togglePlayPause()
        }

        // 关闭按钮
        btnClose?.setOnClickListener {
            hide()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchAndDrag(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var isScaling = false

        // 1. 双指捏合手势检测器 (Pinch-to-Scale)
        val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                val factor = detector.scaleFactor
                userScaleFactor = (userScaleFactor * factor).coerceIn(0.7f, 1.8f)
                applyAdaptiveDimensions(view)
                clampCoordinates(params, view)
                try {
                    if (view.isAttachedToWindow) {
                        windowManager.updateViewLayout(view, params)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Update layout on scale failed: ${e.message}")
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                prefs.edit().putFloat(KEY_USER_SCALE, userScaleFactor).apply()
            }
        })

        // 2. 双击手势检测器 (双击切换 1.0x 标准与 1.35x 超大模式)
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetScale = if (userScaleFactor < 1.2f) 1.35f else 1.0f
                setUserScale(targetScale)
                return true
            }
        })

        // 3. 中部标题信息流手势：滑动切歌 (左滑下一首，右滑上一首) + 轻触点击进入播放器
        infoContainer?.let { container ->
            setupInfoTouchListener(container)
        }

        // 4. 卡片整体拖拽与双指捏合
        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            scaleDetector.onTouchEvent(event)

            if (event.pointerCount > 1 || isScaling) {
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    isScaling = false
                    isDragging = false
                }
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    isScaling = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        isDragging = true
                    }

                    if (isDragging && !isScaling) {
                        val bounds = getScreenBounds()
                        val vWidth = view.width.takeIf { it > 0 } ?: 0
                        val vHeight = view.height.takeIf { it > 0 } ?: 0
                        val maxX = (bounds.width() - vWidth).coerceAtLeast(0)
                        val maxY = (bounds.height() - vHeight).coerceAtLeast(0)

                        params.x = (initialX + dx).coerceIn(0, maxX)
                        params.y = (initialY + dy).coerceIn(0, maxY)

                        try {
                            if (view.isAttachedToWindow) {
                                windowManager.updateViewLayout(view, params)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Update floating window position failed: ${e.message}")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        clampCoordinates(params, view)
                        prefs.edit()
                            .putInt(KEY_POS_X, params.x)
                            .putInt(KEY_POS_Y, params.y)
                            .apply()
                    } else if (!isScaling) {
                        // 纯点击卡片边缘空白区也可唤起主界面
                        openMainActivity()
                    }
                    isDragging = false
                    isScaling = false
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 针对标题与歌词信息区域的手势绑定：
     * - 水平滑动：左滑切下一首，右滑切上一首（附带丝滑弹性位移与换曲动效）
     * - 轻触点击：直接进入主播放器
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupInfoTouchListener(container: View) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        val density = context.resources.displayMetrics.density
        val swipeThreshold = 36 * density
        var startX = 0f
        var startY = 0f
        var isHorizontalSwiping = false

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isHorizontalSwiping = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (!isHorizontalSwiping && abs(dx) > touchSlop && abs(dx) > abs(dy) * 1.2f) {
                        isHorizontalSwiping = true
                    }
                    if (isHorizontalSwiping) {
                        // 弹性阻尼微位移跟随，给用户明确的手势切歌反馈
                        v.translationX = dx * 0.45f
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    val totalMove = abs(dx) + abs(dy)
                    val playerManager = MusicPlayerManager.getInstance(context)

                    if (isHorizontalSwiping) {
                        if (dx < -swipeThreshold) {
                            // 向左滑：切换下一首
                            v.animate().translationX(-45f * density).alpha(0.3f).setDuration(120).withEndAction {
                                v.translationX = 45f * density
                                v.animate().translationX(0f).alpha(1f).setDuration(160).start()
                            }.start()
                            playerManager.playNext()
                        } else if (dx > swipeThreshold) {
                            // 向右滑：切换上一首
                            v.animate().translationX(45f * density).alpha(0.3f).setDuration(120).withEndAction {
                                v.translationX = -45f * density
                                v.animate().translationX(0f).alpha(1f).setDuration(160).start()
                            }.start()
                            playerManager.playPrevious()
                        } else {
                            // 未达到切歌阈值，回弹复位
                            v.animate().translationX(0f).alpha(1f).setDuration(150).start()
                        }
                    } else {
                        v.animate().translationX(0f).alpha(1f).setDuration(150).start()
                        if (totalMove <= touchSlop) {
                            // 纯点击事件：点击进入播放器主界面
                            openMainActivity()
                        }
                    }
                    isHorizontalSwiping = false
                    true
                }
                else -> false
            }
        }
    }

    private fun openMainActivity() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch MainActivity: ${e.message}", e)
        }
    }

    private fun startObservingPlayback() {
        observeJob?.cancel()
        coverObserverJob?.cancel()
        val playerManager = MusicPlayerManager.getInstance(context)

        observeJob = scope.launch {
            playerManager.playbackState.collectLatest { state ->
                updatePlaybackInfo(state.currentSong, state.isPlaying, state.currentPositionMs)
            }
        }

        // 监听大尺寸/在线封面更新广播，实时热刷新悬浮窗封面
        coverObserverJob = scope.launch {
            CoverHelper.coverUpdatedFlow.collect { updatedSongId ->
                val currentSong = playerManager.playbackState.value.currentSong
                if (currentSong != null && currentSong.id == updatedSongId && isShowing) {
                    loadSongCover(currentSong)
                }
            }
        }
    }

    private fun updatePlaybackInfo(song: Song?, isPlaying: Boolean, currentPositionMs: Long) {
        if (!isShowing || floatingView == null) return

        // 1. 更新播放/暂停按键图标与黑胶平滑自转动效
        btnPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_float_pause else R.drawable.ic_float_play
        )
        updateCoverRotation(isPlaying)

        // 2. 更新歌曲基本信息与封面
        if (song != null) {
            if (song.id != currentSongId) {
                currentSongId = song.id
                tvTitle?.text = song.title.ifBlank { "未知歌曲" }
                tvArtist?.text = song.artist.ifBlank { "未知艺术家" }
                tvLyric?.text = "正在同步歌词..."
                tvLyricNext?.text = ""
                lastLyricIndex = -2

                // 启动全通道封面异步解析与加载
                loadSongCover(song)

                // 异步检索与解析歌词
                lyricLoadJob?.cancel()
                lyricLoadJob = scope.launch {
                    val lines = withContext(Dispatchers.IO) {
                        LyricParser.loadLyricForSongAsync(context, song)
                    }
                    currentLyricLines = lines
                    updateLyricLine(currentPositionMs)
                }
            } else {
                updateLyricLine(currentPositionMs)
            }
        } else {
            currentSongId = -1L
            currentLyricLines = emptyList()
            lastLyricIndex = -2
            tvTitle?.text = "OrBit Player"
            tvArtist?.text = "未在播放音乐"
            tvLyric?.text = "享受美好旋律"
            tvLyricNext?.text = ""
            ivCover?.setImageResource(R.drawable.ic_default_album_art)
        }
    }

    /**
     * 控制唱片封面自转动效：播放时匀速自转，暂停时优雅定格
     */
    private fun updateCoverRotation(isPlaying: Boolean) {
        val cover = ivCover ?: return
        if (coverRotateAnimator == null) {
            coverRotateAnimator = ObjectAnimator.ofFloat(cover, View.ROTATION, 0f, 360f).apply {
                duration = 18000L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
            }
        }
        val anim = coverRotateAnimator ?: return
        if (isPlaying) {
            if (anim.isPaused) {
                anim.resume()
            } else if (!anim.isStarted) {
                anim.start()
            }
        } else {
            if (anim.isRunning) {
                anim.pause()
            }
        }
    }

    /**
     * 智能异步加载歌曲封面 (支持本地内嵌音频标签、同目录图片、本地缓存及网络在线音源封面 URL)
     */
    private fun loadSongCover(song: Song) {
        coverLoadJob?.cancel()

        // 1. 优先通道：本地已有解码缓存或本地文件提取
        val localBitmap = CoverHelper.getCoverBitmap(context, song.id, song.path, song.album)?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.config == Bitmap.Config.HARDWARE) {
                it.copy(Bitmap.Config.ARGB_8888, false) ?: it
            } else it
        }
        if (localBitmap != null && !localBitmap.isRecycled) {
            ivCover?.setImageBitmap(localBitmap)
            return
        }

        // 2. 多源候选：网络封面 URL (如网易云/QQ/酷狗) > 内容提供者 URI > 本地缓存文件
        val candidateData: Any? = when {
            !song.albumArtUri.isNullOrBlank() -> {
                val rawUri = song.albumArtUri
                if (rawUri.startsWith("//")) "https:$rawUri" else rawUri
            }
            song.path.isNotBlank() && !song.path.startsWith("online://") -> {
                AudioCoverProvider.buildSongCoverUri(song.id, song.path, song.album)
            }
            else -> {
                CoverHelper.getSafeCoverFile(context, song.id).takeIf { it.exists() && it.length() > 64 }
            }
        }

        if (candidateData != null) {
            val imageLoader = Coil.imageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(candidateData)
                .allowHardware(false) // 禁用硬件位图，强制输出为通用 ARGB_8888 软件位图，彻底杜绝 Software rendering doesn't support hardware bitmaps 崩溃
                .crossfade(true)
                .placeholder(R.drawable.ic_default_album_art)
                .error(R.drawable.ic_default_album_art)
                .target(
                    onStart = {
                        if (ivCover?.drawable == null) {
                            ivCover?.setImageResource(R.drawable.ic_default_album_art)
                        }
                    },
                    onSuccess = { result ->
                        if (song.id == currentSongId && isShowing) {
                            ivCover?.setImageDrawable(result)
                        }
                    },
                    onError = {
                        scope.launch {
                            val fallbackBitmap = withContext(Dispatchers.IO) {
                                CoverHelper.getCoverBitmap(context, song.id, song.path, song.album)
                            }?.let {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it.config == Bitmap.Config.HARDWARE) {
                                    it.copy(Bitmap.Config.ARGB_8888, false) ?: it
                                } else it
                            }
                            if (song.id == currentSongId && isShowing) {
                                if (fallbackBitmap != null && !fallbackBitmap.isRecycled) {
                                    ivCover?.setImageBitmap(fallbackBitmap)
                                } else {
                                    ivCover?.setImageResource(R.drawable.ic_default_album_art)
                                }
                            }
                        }
                    }
                )
                .build()

            coverLoadJob = scope.launch {
                imageLoader.execute(request)
            }
        } else {
            ivCover?.setImageResource(R.drawable.ic_default_album_art)
        }
    }

    private val lyricDecelerateInterpolator = DecelerateInterpolator(1.6f)

    private fun updateLyricLine(currentPositionMs: Long) {
        if (currentLyricLines.isEmpty()) {
            if (lastLyricIndex != -1) {
                lastLyricIndex = -1
                setLyricDirectly("♪ 享受音乐旋律 ♪", "", Color.parseColor("#80FFFFFF"))
            }
            return
        }

        val index = currentLyricLines.indexOfLast { it.timeMs <= currentPositionMs }
        if (index != lastLyricIndex) {
            val previousIndex = lastLyricIndex
            lastLyricIndex = index

            // 当前正在唱的歌词 (核心行)
            val currentText = if (index >= 0) {
                currentLyricLines[index].text.trim().ifBlank { "♪ ♪ ♪" }
            } else {
                currentLyricLines.firstOrNull()?.text?.trim() ?: "♪ ♪ ♪"
            }

            // 即将开唱的下一句歌词 (预告行)
            val nextText = if (index >= 0 && index + 1 < currentLyricLines.size) {
                currentLyricLines[index + 1].text.trim()
            } else if (index < 0 && currentLyricLines.size > 1) {
                currentLyricLines[1].text.trim()
            } else {
                ""
            }

            // 动态测量当前歌词宽度，判断是否太长需要占用两行显示
            val paint = tvLyric?.paint
            val availableWidth = if ((tvLyric?.width ?: 0) > 0) {
                tvLyric!!.width.toFloat()
            } else {
                val density = context.resources.displayMetrics.density
                102f * userScaleFactor * density
            }
            val textWidth = paint?.measureText(currentText) ?: 0f

            val (targetText1, targetText2, targetColor2) = if (textWidth > availableWidth && currentText.length >= 7) {
                // 当前字幕太长：拆分为两行显示完整歌词，两行均呈现高亮纯白
                val (part1, part2) = splitLongLyric(currentText)
                Triple(part1, part2, Color.parseColor("#F1F5F9"))
            } else {
                // 普通长度字幕：第一行永远是当前唱的歌词，第二行是下一句预告歌词
                Triple(currentText, nextText, Color.parseColor("#80FFFFFF"))
            }

            // 当歌曲在正常推进过程中发生行切换时应用平滑上移过渡动效，否则瞬时更新避免跳闪
            if (previousIndex >= 0 && isShowing) {
                animateLyricTransition(targetText1, targetText2, targetColor2)
            } else {
                setLyricDirectly(targetText1, targetText2, targetColor2)
            }
        }
    }

    /**
     * 字幕行切换时的垂直平滑推进过渡动画：
     * 原字幕轻盈向上滑出并淡出，新字幕自下方优雅上浮悬停
     */
    private fun animateLyricTransition(
        targetText1: String,
        targetText2: String,
        targetColor2: Int
    ) {
        val l1 = tvLyric ?: return
        val l2 = tvLyricNext ?: return

        l1.animate().cancel()
        l2.animate().cancel()

        val shiftDistance = 9f * context.resources.displayMetrics.density

        // 阶段一：原歌词向上微滑并淡出 (120ms)
        l1.animate()
            .translationY(-shiftDistance)
            .alpha(0f)
            .setDuration(120L)
            .setInterpolator(lyricDecelerateInterpolator)
            .withEndAction {
                l1.text = targetText1
                l2.text = targetText2
                l2.setTextColor(targetColor2)

                // 阶段二：新歌词自下方优雅上浮入场 (180ms)
                l1.translationY = shiftDistance * 0.7f
                l1.alpha = 0f
                l1.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(lyricDecelerateInterpolator)
                    .start()

                l2.translationY = shiftDistance * 0.7f
                l2.alpha = 0f
                l2.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(lyricDecelerateInterpolator)
                    .start()
            }
            .start()

        l2.animate()
            .translationY(-shiftDistance)
            .alpha(0f)
            .setDuration(120L)
            .setInterpolator(lyricDecelerateInterpolator)
            .start()
    }

    private fun setLyricDirectly(
        targetText1: String,
        targetText2: String,
        targetColor2: Int
    ) {
        val l1 = tvLyric ?: return
        val l2 = tvLyricNext ?: return
        l1.animate().cancel()
        l2.animate().cancel()
        l1.translationY = 0f
        l1.alpha = 1f
        l1.text = targetText1
        l2.translationY = 0f
        l2.alpha = 1f
        l2.text = targetText2
        l2.setTextColor(targetColor2)
    }

    /**
     * 将超出单行显示宽度的长歌词智能拆分为两行：
     * 优先寻找中心附近的断句标点，其次按中点自然分割
     */
    private fun splitLongLyric(text: String): Pair<String, String> {
        val length = text.length
        val half = length / 2
        val delims = charArrayOf(' ', '　', ',', '，', '、', '/', '-', '—', ';', '；', '!', '！')
        var bestSplit = -1
        var minDiff = Int.MAX_VALUE

        for (i in text.indices) {
            if (text[i] in delims) {
                val diff = abs(i - half)
                if (diff < minDiff && diff <= (length * 0.35).toInt()) {
                    minDiff = diff
                    bestSplit = i
                }
            }
        }

        return if (bestSplit > 0) {
            val part1 = text.substring(0, bestSplit).trim()
            val part2 = text.substring(bestSplit + 1).trim()
            if (part1.isNotEmpty() && part2.isNotEmpty()) {
                Pair(part1, part2)
            } else {
                Pair(text.substring(0, half).trim(), text.substring(half).trim())
            }
        } else {
            Pair(text.substring(0, half).trim(), text.substring(half).trim())
        }
    }
}
