package com.orbit.music.car

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ecarx.eas.sdk.mediacenter.MusicPlaybackInfo
import com.ecarx.eas.sdk.mediacenter.SourceType
import com.orbit.music.audio.MusicPlayerManager
import com.orbit.music.audio.PlaybackState
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.lyrics.OnlineLyricManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 亿咖通 (ECARX) / 吉利银河 OS / 领克 Flyme Auto / 极氪 车机媒体中心通信桥接核心
 *
 * 核心能力：
 * 1. 动态探测车机 MediaCenter 服务与 SDK 环境，非车机设备安全降级（零性能开销、零崩溃风险）
 * 2. 注册 OrbitCarMusicClient，使 Orbit Player 在车机中控屏顶栏【播放源下拉菜单】中高亮显示
 * 3. 响应车机中控源切换事件（onSourceSelected），智能夺取播放焦点并无缝续播
 * 4. 实时双向同步播放元数据（歌名、歌手、专辑、高保真封面、歌词、播放进度条）至车机桌面 Widget、仪表盘及 HUD
 */
object EcarxMediaBridge {

    private const val TAG = "EcarxMediaBridge"

    @Volatile
    private var inited = false

    @Volatile
    private var isReady = false

    private var apiInstance: Any? = null
    private var clientToken: Any? = null
    private var appContext: Context? = null

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateObserverJob: Job? = null
    private var progressTickerJob: Job? = null
    private var lyricFetchJob: Job? = null

    @Volatile
    private var cachedLyricText: String? = null
    private var lastLyricSongId: Long = -1L

    /**
     * 初始化车机媒体中心桥接器
     */
    fun init(context: Context) {
        if (inited) return
        inited = true
        val app = context.applicationContext
        appContext = app

        try {
            // 1. 动态反射加载车机 MediaCenterAPI
            val apiClass = try {
                Class.forName("com.ecarx.eas.sdk.mediacenter.MediaCenterAPI")
            } catch (e: ClassNotFoundException) {
                Log.d(TAG, "ECARX MediaCenterAPI 不存在于当前系统，当前设备非吉利/领克/银河车机，已安全跳过")
                return
            }

            Log.i(TAG, "检测到车载系统支持 ECARX MediaCenterAPI，开始初始化车载多媒体桥接...")

            // 2. 获取 API 单例 (MediaCenterAPI.get(context))
            val getMethod = try {
                apiClass.getMethod("get", Context::class.java)
            } catch (e: NoSuchMethodException) {
                apiClass.getMethod("createMediaCenterAPI", Context::class.java)
            }
            val apiObj = getMethod.invoke(null, app)
            if (apiObj == null) {
                Log.w(TAG, "MediaCenterAPI 获取实例返回 null")
                return
            }
            apiInstance = apiObj

            // 3. 查找 init(context, callback) 初始化方法
            var initMethod: Method? = null
            for (m in apiObj.javaClass.methods) {
                if (m.name == "init" && m.parameterTypes.size == 2 && m.parameterTypes[0] == Context::class.java) {
                    initMethod = m
                    break
                }
            }

            if (initMethod == null) {
                Log.w(TAG, "未找到 MediaCenterAPI.init(Context, Callback) 方法")
                return
            }

            // 4. 构造初始化监听回调动态代理 (监听 onAPIReady)
            val callbackInterface = initMethod.parameterTypes[1]
            val callbackProxy = Proxy.newProxyInstance(
                callbackInterface.classLoader,
                arrayOf(callbackInterface),
                object : InvocationHandler {
                    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
                        val name = method.name
                        if (name == "onAPIReady") {
                            val ready = (args?.firstOrNull() as? Boolean) == true
                            Log.i(TAG, "★ 车机 MediaCenter onAPIReady 回调: ready=$ready")
                            if (ready && !isReady) {
                                isReady = true
                                bridgeScope.launch {
                                    onMediaCenterReady()
                                }
                            }
                            return null
                        }
                        if (method.declaringClass == Any::class.java) {
                            return when (name) {
                                "toString" -> "OrbitEcarxCallback"
                                "hashCode" -> 0
                                "equals" -> proxy === args?.firstOrNull()
                                else -> null
                            }
                        }
                        if (method.returnType == Boolean::class.javaPrimitiveType) {
                            return false
                        }
                        return null
                    }
                }
            )

            // 5. 触发初始化绑定
            initMethod.invoke(apiObj, app, callbackProxy)
            Log.i(TAG, "已调用 MediaCenterAPI.init，正在等待车机服务就绪...")
        } catch (e: Throwable) {
            Log.w(TAG, "初始化 ECARX MediaCenter 异常: ${e.message}", e)
        }
    }

    /**
     * 当与车机 MediaCenter 服务连接就绪时回调
     */
    private fun onMediaCenterReady() {
        val app = appContext ?: return
        val api = apiInstance ?: return

        try {
            Log.i(TAG, "正在向车机媒体中心注册 OrbitCarMusicClient...")

            // 1. 实例化音乐客户端
            val musicClient = OrbitCarMusicClient(app)

            // 2. 查找 registerMusic 方法 (优先 registerMusic(packageName, client))
            var regMethod: Method? = null
            for (m in api.javaClass.methods) {
                if (m.name == "registerMusic" && m.parameterTypes.size == 2 && m.parameterTypes[0] == String::class.java) {
                    regMethod = m
                    break
                }
            }
            if (regMethod == null) {
                for (m in api.javaClass.methods) {
                    if (m.name == "registerMusic") {
                        regMethod = m
                        break
                    }
                }
            }

            if (regMethod == null) {
                Log.w(TAG, "未找到 registerMusic 接口")
                return
            }

            val token = if (regMethod.parameterTypes.size == 2) {
                regMethod.invoke(api, app.packageName, musicClient)
            } else {
                regMethod.invoke(api, musicClient)
            }

            clientToken = token
            Log.i(TAG, "★ OrbitCarMusicClient 注册成功! token=$token")

            // 3. 初始上报音源类型 (在线音乐 SOURCE_TYPE_ONLINE = 6)
            invokeApi("updateCurrentSourceType", token, SourceType.SOURCE_TYPE_ONLINE)

            // 4. 立即同步当前播放器状态
            pushCurrentStateToCar()

            // 5. 开启播放状态监听与进度条定时器
            startStateObserver()
            startProgressTicker()
        } catch (e: Throwable) {
            Log.e(TAG, "注册 OrbitCarMusicClient 失败: ${e.message}", e)
        }
    }

    /**
     * 请求车机底层音频播放焦点并同步音源类型
     */
    fun takeFocus() {
        if (apiInstance == null) return
        val token = clientToken ?: return
        if (!isReady) return

        try {
            Log.i(TAG, "takeFocus: 请求车机媒体播放焦点...")
            invokeApi("requestPlay", token)
            val currentSource = getCurrentSourceType()
            invokeApi("updateCurrentSourceType", token, currentSource)
        } catch (e: Throwable) {
            Log.w(TAG, "takeFocus failed: ${e.message}")
        }
    }

    /**
     * 监听播放器状态变更，实时推送元数据与歌词
     */
    private fun startStateObserver() {
        val app = appContext ?: return
        val playerManager = MusicPlayerManager.getInstance(app)

        stateObserverJob?.cancel()
        stateObserverJob = bridgeScope.launch {
            playerManager.playbackState
                .distinctUntilChanged { old, new ->
                    old.currentSong?.id == new.currentSong?.id &&
                            old.isPlaying == new.isPlaying &&
                            old.currentIndex == new.currentIndex
                }
                .collectLatest { state ->
                    pushState(state)
                    checkAndFetchLyric(state.currentSong)
                }
        }
    }

    /**
     * 获取歌词并推送至车机中控与仪表盘 HUD
     */
    private fun checkAndFetchLyric(song: Song?) {
        val app = appContext ?: return
        if (song == null) {
            cachedLyricText = null
            lastLyricSongId = -1L
            return
        }

        if (song.id == lastLyricSongId && cachedLyricText != null) {
            updateCarLyric(cachedLyricText)
            return
        }

        lyricFetchJob?.cancel()
        lyricFetchJob = bridgeScope.launch {
            try {
                val lyricLines = OnlineLyricManager.getInstance(app).getLyricForSong(song)
                if (lyricLines.isNotEmpty()) {
                    val fullText = lyricLines.joinToString("\n") { it.text }
                    cachedLyricText = fullText
                    lastLyricSongId = song.id
                    updateCarLyric(fullText)
                }
            } catch (e: Throwable) {
                Log.d(TAG, "车机歌词获取跳过: ${e.message}")
            }
        }
    }

    private fun updateCarLyric(lyric: String?) {
        val token = clientToken ?: return
        if (!isReady || lyric.isNullOrEmpty()) return
        try {
            invokeApi("updateCurrentLyric", token, lyric)
        } catch (e: Throwable) {
            // 静默处理部分车型不支持单独歌词通道
        }
    }

    /**
     * 启动进度条上报心跳（播放中每秒向车机上报一次当前播放进度）
     */
    private fun startProgressTicker() {
        val app = appContext ?: return
        val playerManager = MusicPlayerManager.getInstance(app)

        progressTickerJob?.cancel()
        progressTickerJob = bridgeScope.launch {
            while (isActive) {
                delay(1000L)
                if (isReady && clientToken != null && playerManager.playbackState.value.isPlaying) {
                    val pos = playerManager.playbackState.value.currentPositionMs
                    try {
                        invokeApi("updateCurrentProgress", clientToken, pos)
                    } catch (e: Throwable) {
                        // 忽略偶发调用超时
                    }
                }
            }
        }
    }

    /**
     * 构造并推送播放状态到车机媒体中心
     */
    fun pushCurrentStateToCar() {
        val app = appContext ?: return
        val playerManager = MusicPlayerManager.getInstance(app)
        pushState(playerManager.playbackState.value)
    }

    private fun pushState(state: PlaybackState) {
        val app = appContext ?: return
        val api = apiInstance ?: return
        val token = clientToken ?: return
        if (!isReady) return

        try {
            // 1. 同步当前音源类型 (在线 / 本地)
            val currentSource = getCurrentSourceType()
            invokeApi("updateCurrentSourceType", token, currentSource)

            // 2. 上报 MusicPlaybackInfo (歌曲标题/艺术家/专辑/封面/播放状态)
            val playbackInfo = OrbitCarMusicPlaybackInfo(app, state, cachedLyricText)
            var stateMethod: Method? = null
            for (m in api.javaClass.methods) {
                if (m.name == "updateMusicPlaybackState" && m.parameterTypes.size == 2) {
                    stateMethod = m
                    break
                }
            }
            stateMethod?.invoke(api, token, playbackInfo)

            // 3. 上报当前曲目 MediaContent (用于中控卡片和桌面 Widget 显示)
            val song = state.currentSong
            if (song != null) {
                val mediaInfo = OrbitCarMediaInfo(song, state.currentIndex, cachedLyricText)
                invokeApi("updateMediaContent", token, listOf(mediaInfo))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "pushState to car failed: ${e.message}")
        }
    }

    fun getCurrentPlaybackInfo(): MusicPlaybackInfo {
        val app = appContext ?: throw IllegalStateException("EcarxMediaBridge not initialized")
        val playerManager = MusicPlayerManager.getInstance(app)
        return OrbitCarMusicPlaybackInfo(app, playerManager.playbackState.value, cachedLyricText)
    }

    private fun getCurrentSourceType(): Int {
        val app = appContext ?: return SourceType.SOURCE_TYPE_ONLINE
        val currentSong = MusicPlayerManager.getInstance(app).playbackState.value.currentSong
        val path = currentSong?.path ?: ""
        return if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("online://")) {
            SourceType.SOURCE_TYPE_ONLINE
        } else {
            SourceType.SOURCE_TYPE_LOCAL
        }
    }

    /**
     * 反射调用 MediaCenterAPI 内部通用方法
     */
    private fun invokeApi(methodName: String, vararg args: Any?): Any? {
        val api = apiInstance ?: return null
        for (m in api.javaClass.methods) {
            if (m.name == methodName && m.parameterTypes.size == args.size) {
                return m.invoke(api, *args)
            }
        }
        return null
    }
}
