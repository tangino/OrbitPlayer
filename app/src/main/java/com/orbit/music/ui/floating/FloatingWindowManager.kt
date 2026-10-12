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
import android.os.Handler
import android.os.Looper
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

        /** 用户可自由缩放范围：最小 60%，最大 220%（会再按屏幕尺寸二次收敛） */
        private const val MIN_USER_SCALE = 0.6f
        private const val MAX_USER_SCALE = 2.2f
        /** 尺寸调整框长时间无操作自动收起，避免误触后一直停留在调整状态 */
        private const val RESIZE_IDLE_TIMEOUT_MS = 7000L

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
     * 用户自定义缩放因子 (默认 1.0f，可长按悬浮窗后拖动四角圆点自由调节)
     * 所有控件尺寸与字体大小均按该因子等比缩放
     */
    var userScaleFactor: Float = prefs.getFloat(KEY_USER_SCALE, 1.0f)
            .coerceIn(MIN_USER_SCALE, MAX_USER_SCALE)
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
    private var lastLyricStateKey: String = ""

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

    // 尺寸调整模式 (长按唤起) 相关控件与状态
    private var resizeOverlay: FrameLayout? = null
    private var resizeFrame: View? = null
    private var resizeHint: TextView? = null
    private val resizeHandles = mutableListOf<ResizeHandle>()

    /** 单个拖动圆点：dirX/dirY 表示向外拖动时该角对应的缩放增长方向 */
    private data class ResizeHandle(val view: View, val dirX: Int, val dirY: Int)

    private val uiHandler = Handler(Looper.getMainLooper())
    private var resizeIdleRunnable: Runnable? = null

    /** 当前是否处于"拖动四角圆点调整大小"模式 */
    var isResizeMode: Boolean = false
        private set

    private var resizeStartScale = 1f
    private var resizeStartRawX = 0f
    private var resizeStartRawY = 0f
    private var resizeStartCenterX = 0f
    private var resizeStartCenterY = 0f
    /** 长按刚触发唤起调整框时，本次手势尚未抬手，下一次抬手必须吸收忽略，避免一松手就关闭 */
    private var suppressExitOnNextUp = false
    private var longPressTriggered = false
    private var infoLongPressRunnable: Runnable? = null

    /**
     * 尝试响应轻触抬手退出调整模式：
     * 若为长按唤起手势的自然抬手，则安全吸收忽略，保持调整框显示
     */
    private fun tryExitOnTouchUp(): Boolean {
        if (suppressExitOnNextUp) {
            suppressExitOnNextUp = false
            return false
        }
        exitResizeMode()
        return true
    }

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
     * 设置自定义缩放因子 (0.6x ~ 屏幕允许上限)
     */
    fun setUserScale(scale: Float) {
        val clamped = clampScale(scale)
        if (abs(userScaleFactor - clamped) > 0.01f) {
            userScaleFactor = clamped
            prefs.edit().putFloat(KEY_USER_SCALE, clamped).apply()
            floatingView?.let { view ->
                applyAdaptiveDimensions(view)
                windowParams?.let { params ->
                    val density = context.resources.displayMetrics.density
                    val (baseW, baseH) = baseCapsuleSizeDp()
                    val padPx = (2 * density).toInt() * 2
                    params.width = (baseW * clamped * density + padPx).toInt()
                    params.height = (baseH * clamped * density + padPx).toInt()
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
     * 各档位胶囊基准尺寸 (dp) —— 全链路唯一数据源
     * 缩放、屏幕边界钳制与尺寸调整框均以它为准，避免多处硬编码造成尺寸计算不一致
     */
    private fun baseCapsuleSizeDp(tier: DeviceTier = getDeviceTier()): Pair<Float, Float> = when (tier) {
        DeviceTier.CAR_OR_LARGE -> Pair(360f, 92f)
        DeviceTier.TABLET -> Pair(285f, 74f)
        DeviceTier.PHONE -> Pair(205f, 54f)
    }

    /**
     * 当前屏幕允许的最大缩放因子：既受全局上限约束，也要保证放大后仍完整落在屏幕内
     */
    private fun effectiveMaxScale(): Float {
        val bounds = getScreenBounds()
        val density = context.resources.displayMetrics.density.coerceAtLeast(0.1f)
        val (baseW, baseH) = baseCapsuleSizeDp()
        return FloatingResizeMath.maxScaleForBounds(
            screenWidthPx = bounds.width(),
            screenHeightPx = bounds.height(),
            baseWidthPx = baseW * density,
            baseHeightPx = baseH * density,
            hardMaxScale = MAX_USER_SCALE,
            minScale = MIN_USER_SCALE
        )
    }

    /**
     * 统一缩放因子钳制：0.6x ~ 屏幕允许上限
     */
    private fun clampScale(scale: Float): Float =
        FloatingResizeMath.clampScale(scale, MIN_USER_SCALE, effectiveMaxScale())

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
    private fun clampCoordinates(
        params: WindowManager.LayoutParams,
        targetView: View? = floatingView,
        expectedWidthPx: Int = 0,
        expectedHeightPx: Int = 0
    ) {
        val bounds = getScreenBounds()
        val density = context.resources.displayMetrics.density

        val (baseW, baseH) = baseCapsuleSizeDp()
        val expectedWidth = if (expectedWidthPx > 0) expectedWidthPx
        else (baseW * userScaleFactor * density).toInt()
        val expectedHeight = if (expectedHeightPx > 0) expectedHeightPx
        else (baseH * userScaleFactor * density).toInt()

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

            val density = context.resources.displayMetrics.density
            val (baseW, baseH) = baseCapsuleSizeDp()
            val padPx = (2 * density).toInt() * 2
            val initW = (baseW * userScaleFactor * density + padPx).toInt()
            val initH = (baseH * userScaleFactor * density + padPx).toInt()

            val params = WindowManager.LayoutParams(
                initW,
                initH,
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
            // 恢复用户上次自由调整的尺寸 (并保证在当前屏幕范围内)
            userScaleFactor = clampScale(userScaleFactor)
            applyAdaptiveDimensions(view)
            clampCoordinates(params, view)
            setupTouchAndDrag(view, params)
            setupResizeHandles()
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

        // 关闭悬浮窗前先收起尺寸调整状态并清理未触发的长按/超时计时
        if (isResizeMode) exitResizeMode()
        cancelInfoLongPress()
        cancelResizeIdleExit()

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

        // 尺寸调整层 (长按唤起)
        resizeOverlay = root.findViewById(R.id.floating_resize_overlay)
        resizeFrame = root.findViewById(R.id.floating_resize_frame)
        resizeHint = root.findViewById(R.id.floating_resize_hint)
        resizeHandles.clear()
        listOf(
            Triple(R.id.floating_resize_handle_tl, -1, -1),
            Triple(R.id.floating_resize_handle_tr, 1, -1),
            Triple(R.id.floating_resize_handle_bl, -1, 1),
            Triple(R.id.floating_resize_handle_br, 1, 1)
        ).forEach { (id, dirX, dirY) ->
            root.findViewById<View>(id)?.let { resizeHandles.add(ResizeHandle(it, dirX, dirY)) }
        }

        // 虚线选择框：圆角恒等于胶囊半高，随缩放自适应贴合
        val density = context.resources.displayMetrics.density
        resizeFrame?.background = PillDashFrameDrawable(
            strokeWidthPx = 1.6f * density,
            dashLengthPx = 4.5f * density,
            dashColor = Color.argb(215, 255, 255, 255)
        )

        // 调整层触摸拦截：点击非圆点区域退出调整模式，并彻底防止点击穿透到下层播放/关闭按钮
        var overlayStartX = 0f
        var overlayStartY = 0f
        var overlayMoved = false
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        resizeOverlay?.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cancelResizeIdleExit()
                    overlayStartX = event.rawX
                    overlayStartY = event.rawY
                    overlayMoved = false
                    suppressExitOnNextUp = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.rawX - overlayStartX) > touchSlop || abs(event.rawY - overlayStartY) > touchSlop) {
                        overlayMoved = true
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!overlayMoved) {
                        tryExitOnTouchUp()
                    } else {
                        scheduleResizeIdleExit()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    scheduleResizeIdleExit()
                    true
                }
                else -> true
            }
        }

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
            // 屏幕尺寸可能已变化 (横竖屏切换)，重新收敛缩放因子后再刷新尺寸
            userScaleFactor = clampScale(userScaleFactor)
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
        val (baseCapsuleWidthDp, baseCapsuleHeightDp) = baseCapsuleSizeDp(tier)

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

        // 6. 动态更新胶囊背景圆角与半圆全圆角裁切 (彻底解决固定 27dp 变形与根容器错位裁切)
        val capsuleRadius = capsuleH / 2f
        (capsuleContainer?.background?.mutate() as? android.graphics.drawable.GradientDrawable)?.let { gd ->
            gd.cornerRadius = capsuleRadius
        }

        capsuleContainer?.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = if (view.height > 0) view.height / 2f else capsuleRadius
                outline.setRoundRect(0, 0, view.width, view.height, r.coerceAtLeast(1f))
            }
        }
        capsuleContainer?.clipToOutline = true
        capsuleContainer?.invalidateOutline()

        // 根卡片作为包裹容器不启用 outline 裁切，杜绝尺寸不一时边缘被削平
        rootCard?.clipToOutline = false

        // 7. 同步尺寸调整框几何 (虚线框与四角圆点始终贴合胶囊轮廓)
        updateResizeOverlayGeometry(root)
    }

    // ==================== 长按自由调整大小 (Resize Mode) ====================

    /**
     * 进入尺寸调整模式：弹出虚线选择框、四角拖动圆点与实时尺寸提示
     */
    private fun enterResizeMode() {
        if (isResizeMode) return
        val view = floatingView ?: return
        isResizeMode = true
        // 关键保护：长按触发时手指仍在屏幕上，吸收接下来长按手势的自然抬手，不准立刻退出
        suppressExitOnNextUp = true

        // 触觉反馈：明确告知用户已进入可调整状态
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) {}

        // 进入调整模式时，临时关闭根卡片 clipToOutline，确保四角圆点与高亮外边完整展示不被截断
        rootCard?.clipToOutline = false

        resizeOverlay?.visibility = View.VISIBLE
        updateResizeOverlayGeometry(view)
        updateResizeHintText(dragging = false)

        // 淡入动效：明确告知用户已进入可调整状态
        val fadeViews = listOfNotNull(resizeFrame) + resizeHandles.map { it.view }
        fadeViews.forEach { target ->
            target.alpha = 0f
            target.animate().alpha(1f).setDuration(180).start()
        }
        scheduleResizeIdleExit()
        val (baseW, baseH) = baseCapsuleSizeDp()
        Log.i(
            TAG,
            "进入尺寸调整模式: scale=$userScaleFactor " +
                    "size=${(baseW * userScaleFactor).toInt()}x${(baseH * userScaleFactor).toInt()}dp"
        )
    }

    /**
     * 退出尺寸调整模式，并持久化用户调整后的尺寸
     */
    private fun exitResizeMode() {
        if (!isResizeMode) return
        isResizeMode = false
        suppressExitOnNextUp = false
        longPressTriggered = false
        cancelResizeIdleExit()
        infoContainer?.animate()?.translationX(0f)?.setDuration(100)?.start()
        resizeOverlay?.visibility = View.GONE

        // 刷新胶囊裁切
        capsuleContainer?.invalidateOutline()

        prefs.edit().putFloat(KEY_USER_SCALE, userScaleFactor).apply()
        Log.i(TAG, "退出尺寸调整模式: scale=$userScaleFactor")
    }

    /** 长时间无操作自动收起调整框，避免停留在调整状态影响正常操作 */
    private fun scheduleResizeIdleExit() {
        cancelResizeIdleExit()
        val runnable = Runnable { exitResizeMode() }
        resizeIdleRunnable = runnable
        uiHandler.postDelayed(runnable, RESIZE_IDLE_TIMEOUT_MS)
    }

    private fun cancelResizeIdleExit() {
        resizeIdleRunnable?.let { uiHandler.removeCallbacks(it) }
        resizeIdleRunnable = null
    }

    /**
     * 信息区长按计时：长按信息区同样唤起尺寸调整框
     * (信息区被独立触摸监听器接管，需单独做长按检测)
     */
    private fun scheduleInfoLongPress() {
        cancelInfoLongPress()
        longPressTriggered = false
        val runnable = Runnable {
            if (!isResizeMode) {
                // 标记本次手势已被长按消费，抬手时不再触发进播放器/切歌
                longPressTriggered = true
                try {
                    infoContainer?.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } catch (_: Exception) {}
                // 长按触发瞬间，平滑归位轻微位移，避免文字偏移歪斜
                infoContainer?.animate()?.translationX(0f)?.setDuration(100)?.start()
                enterResizeMode()
            }
        }
        infoLongPressRunnable = runnable
        uiHandler.postDelayed(runnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun cancelInfoLongPress() {
        infoLongPressRunnable?.let { uiHandler.removeCallbacks(it) }
        infoLongPressRunnable = null
    }

    /**
     * 同步调整框几何：严格以胶囊尺寸为准排布 Overlay 与四角圆点，杜绝撑大根 FrameLayout
     */
    private fun updateResizeOverlayGeometry(root: View) {
        val container = capsuleContainer ?: return
        val overlay = resizeOverlay ?: return
        val density = context.resources.displayMetrics.density

        // 显式指定 Overlay 宽高严格等于胶囊主体尺寸并居中，从源头杜绝 match_parent 撑爆父布局全屏
        val capsuleW = (container.layoutParams?.width ?: 0).takeIf { it > 0 } ?: root.width
        val capsuleH = (container.layoutParams?.height ?: 0).takeIf { it > 0 } ?: root.height
        if (capsuleW <= 0 || capsuleH <= 0) return

        val overlayLp = overlay.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(capsuleW, capsuleH)
        overlayLp.width = capsuleW
        overlayLp.height = capsuleH
        overlayLp.gravity = Gravity.CENTER
        overlay.layoutParams = overlayLp

        resizeFrame?.let { frame ->
            val lp = frame.layoutParams as? FrameLayout.LayoutParams ?: FrameLayout.LayoutParams(capsuleW, capsuleH)
            lp.width = capsuleW
            lp.height = capsuleH
            frame.layoutParams = lp
        }

        // 胶囊为半高全圆角，圆点沿 45° 落在圆弧上：偏移 = r * (1 - √2/2) ≈ 0.293r
        val radius = capsuleH / 2f
        val offset = radius * (1f - 0.70710678f)
        // 圆点触控尺寸：保证 18dp~26dp，大拇指极易按中
        val handleSize = (capsuleH * 0.30f).coerceIn(18f * density, 26f * density)
        val sizePx = handleSize.toInt().coerceAtLeast(1)
        val margin = (offset - handleSize / 2f).coerceAtLeast(0f).toInt()

        resizeHandles.forEach { handle ->
            val lp = handle.view.layoutParams as? FrameLayout.LayoutParams
                ?: FrameLayout.LayoutParams(sizePx, sizePx)
            lp.width = sizePx
            lp.height = sizePx
            lp.leftMargin = if (handle.dirX < 0) margin else 0
            lp.rightMargin = if (handle.dirX > 0) margin else 0
            lp.topMargin = if (handle.dirY < 0) margin else 0
            lp.bottomMargin = if (handle.dirY > 0) margin else 0
            handle.view.layoutParams = lp
        }
    }

    /**
     * 刷新尺寸提示文案：拖动中显示百分比与 dp 尺寸，静止时提示操作方式
     */
    private fun updateResizeHintText(dragging: Boolean) {
        val hint = resizeHint ?: return
        val (baseW, baseH) = baseCapsuleSizeDp()
        hint.text = if (dragging) {
            val percent = (userScaleFactor * 100f).toInt()
            val wDp = (baseW * userScaleFactor).toInt()
            val hDp = (baseH * userScaleFactor).toInt()
            "$percent% · ${wDp}×${hDp}dp"
        } else {
            "拖动圆点调整大小"
        }
        // 提示文字随尺寸轻微跟随，小尺寸下仍保证可读
        hint.setTextSize(
            TypedValue.COMPLEX_UNIT_SP,
            (10.5f * userScaleFactor).coerceIn(9f, 14f)
        )
    }

    /**
     * 以按下时锁定的中心点为锚点应用新的缩放因子：
     * 杜绝差分累加舍入误差与贴边漂移，保证缩放过程视觉中心绝对平稳
     */
    private fun applyScaleAroundCenter(targetScale: Float) {
        val view = floatingView ?: return
        val params = windowParams ?: return
        val newScale = clampScale(targetScale)

        if (abs(newScale - userScaleFactor) < 0.002f) {
            updateResizeHintText(dragging = true)
            return
        }

        val density = context.resources.displayMetrics.density
        val (baseW, baseH) = baseCapsuleSizeDp()

        userScaleFactor = newScale
        val newW = baseW * density * newScale
        val newH = baseH * density * newScale

        applyAdaptiveDimensions(view)

        // 严格基于按下时锁定的窗口中心计算左上角位置，避免多次 MOVE 累积舍入误差
        params.x = (resizeStartCenterX - newW / 2f).toInt()
        params.y = (resizeStartCenterY - newH / 2f).toInt()
        val padPx = (2 * density).toInt() * 2
        params.width = (newW + padPx).toInt()
        params.height = (newH + padPx).toInt()
        clampCoordinates(params, view, newW.toInt(), newH.toInt())
        try {
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update layout while resizing failed: ${e.message}")
        }
        updateResizeHintText(dragging = true)
    }

    /**
     * 四角圆点拖动手势：拖得越远越大，反向拖回则缩小
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeHandles() {
        val density = context.resources.displayMetrics.density
        val (baseW, baseH) = baseCapsuleSizeDp()
        val baseWpx = (baseW * density).coerceAtLeast(1f)
        val baseHpx = (baseH * density).coerceAtLeast(1f)

        resizeHandles.forEach { handle ->
            handle.view.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        handle.view.parent?.requestDisallowInterceptTouchEvent(true)
                        cancelResizeIdleExit()
                        suppressExitOnNextUp = false
                        resizeStartScale = userScaleFactor
                        resizeStartRawX = event.rawX
                        resizeStartRawY = event.rawY
                        val curW = baseW * density * userScaleFactor
                        val curH = baseH * density * userScaleFactor
                        windowParams?.let { p ->
                            resizeStartCenterX = p.x + curW / 2f
                            resizeStartCenterY = p.y + curH / 2f
                        }
                        handle.view.animate().scaleX(1.35f).scaleY(1.35f).setDuration(90).start()
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - resizeStartRawX
                        val dy = event.rawY - resizeStartRawY
                        // 位移投影到"等比放大"的对角方向换算缩放增量 (已含中心展开补偿因子)
                        val delta = FloatingResizeMath.scaleDelta(
                            dxPx = dx,
                            dyPx = dy,
                            dirX = handle.dirX,
                            dirY = handle.dirY,
                            baseWidthPx = baseWpx,
                            baseHeightPx = baseHpx
                        )
                        applyScaleAroundCenter(resizeStartScale + delta)
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handle.view.animate().scaleX(1f).scaleY(1f).setDuration(90).start()
                        prefs.edit().putFloat(KEY_USER_SCALE, userScaleFactor).apply()
                        Log.i(TAG, "拖动圆点结束: scale=$userScaleFactor")
                        scheduleResizeIdleExit()
                        true
                    }
                    else -> false
                }
            }
        }
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
                userScaleFactor = clampScale(userScaleFactor * factor)
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
                if (isResizeMode) return true
                val targetScale = if (userScaleFactor < 1.2f) 1.35f else 1.0f
                setUserScale(targetScale)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                // 长按唤起尺寸调整框：拖动四角圆点即可自由改变悬浮窗大小
                if (!isResizeMode) {
                    enterResizeMode()
                }
            }
        })

        // 3. 中部标题信息流手势：滑动切歌 (左滑下一首，右滑上一首) + 轻触点击进入播放器
        infoContainer?.let { container ->
            setupInfoTouchListener(container)
        }

        // 4. 卡片整体拖拽与双指捏合
        view.setOnTouchListener { _, event ->
            // 4.1 尺寸调整模式：单指轻触外围空白处安全退出调整模式，长按自然抬手不退出
            if (isResizeMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> suppressExitOnNextUp = false
                    MotionEvent.ACTION_UP -> tryExitOnTouchUp()
                }
                return@setOnTouchListener true
            }

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
            // 尺寸调整模式下：信息区不响应切歌/进播放器，轻触抬手安全退出，长按自然抬手不退出
            if (isResizeMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> suppressExitOnNextUp = false
                    MotionEvent.ACTION_UP -> tryExitOnTouchUp()
                }
                return@setOnTouchListener true
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startY = event.rawY
                    isHorizontalSwiping = false
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    // 长按信息区同样可唤起尺寸调整框
                    scheduleInfoLongPress()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        cancelInfoLongPress()
                    }
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
                    cancelInfoLongPress()
                    // 长按已进入尺寸调整模式：本次抬手不再触发点击/切歌
                    if (longPressTriggered) {
                        longPressTriggered = false
                        v.animate().translationX(0f).alpha(1f).setDuration(120).start()
                        isHorizontalSwiping = false
                        return@setOnTouchListener true
                    }
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
                lastLyricStateKey = ""

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
            lastLyricStateKey = ""
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

    private fun isSequentialLyricState(prev: String, curr: String): Boolean {
        if (prev.isEmpty() || prev == "empty" || prev == "none") return false
        if (prev == "intro" && curr.startsWith("line_")) return true

        val prevIdx = parseStateIndex(prev)
        val currIdx = parseStateIndex(curr)
        if (prevIdx == null || currIdx == null) return false

        // 同一行人声演唱结束，转入间奏展示: line_X -> interlude_X
        if (prev == "line_$prevIdx" && curr == "interlude_$prevIdx") return true
        // 间奏结束，平滑进入下一行歌词: interlude_X -> line_${prevIdx + 1}
        if (prev == "interlude_$prevIdx" && curr == "line_${prevIdx + 1}") return true
        // 紧凑快速连唱，未插播间奏，直接从当前行切到下一行: line_X -> line_${prevIdx + 1}
        if (prev == "line_$prevIdx" && curr == "line_${prevIdx + 1}") return true
        // 歌曲最后一句唱完，转入尾奏伴奏: line_X -> outro
        if (prev == "line_$prevIdx" && curr == "outro") return true

        return false
    }

    private fun parseStateIndex(key: String): Int? {
        return when {
            key.startsWith("line_") -> key.removePrefix("line_").toIntOrNull()
            key.startsWith("interlude_") -> key.removePrefix("interlude_").toIntOrNull()
            key == "outro" -> 999999
            else -> null
        }
    }

    private fun updateLyricLine(currentPositionMs: Long) {
        if (currentLyricLines.isEmpty()) {
            if (lastLyricStateKey != "empty") {
                lastLyricStateKey = "empty"
                lastLyricIndex = -1
                setLyricDirectly("♪ 享受音乐旋律 ♪", "", Color.parseColor("#80FFFFFF"))
            }
            return
        }

        // 智能人声识别与歌词状态解析：
        // 1. 开唱歌词快唱完剩约 2 个字时自适应提前上浮预滚动
        // 2. 人声唱完而伴奏仍在继续（间奏/Solo/尾奏）时及时向上滚动过渡，杜绝字幕死滞
        val state = FloatingLyricHelper.resolveLyricState(currentLyricLines, currentPositionMs)
        if (state.stateKey != lastLyricStateKey) {
            val prevStateKey = lastLyricStateKey
            lastLyricStateKey = state.stateKey

            val targetText1: String
            val targetText2: String
            val targetColor2: Int

            if (state.isInstrumental) {
                // 伴奏段落 (前奏 / 间奏 / 尾奏)：第一行突出伴奏标识，第二行作为下一句预告
                targetText1 = state.text1
                targetText2 = state.text2
                targetColor2 = Color.parseColor("#80FFFFFF")
            } else {
                // 人声演唱阶段：测量宽度，超长时拆分为两行高亮显示
                val currentText = state.text1
                val nextText = state.text2
                val paint = tvLyric?.paint
                val availableWidth = if ((tvLyric?.width ?: 0) > 0) {
                    tvLyric!!.width.toFloat()
                } else {
                    val density = context.resources.displayMetrics.density
                    102f * userScaleFactor * density
                }
                val textWidth = paint?.measureText(currentText) ?: 0f

                if (textWidth > availableWidth && currentText.length >= 7) {
                    val (part1, part2) = splitLongLyric(currentText)
                    targetText1 = part1
                    targetText2 = part2
                    targetColor2 = Color.parseColor("#F1F5F9")
                } else {
                    targetText1 = currentText
                    targetText2 = nextText
                    targetColor2 = Color.parseColor("#80FFFFFF")
                }
            }

            // 顺次推进时应用平滑上浮过渡动效；大幅拖动跳跃或快退时直接瞬时更新，避免动画错位跳闪
            if (isSequentialLyricState(prevStateKey, state.stateKey) && isShowing) {
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
