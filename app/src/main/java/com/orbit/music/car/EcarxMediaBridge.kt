package com.orbit.music.car

import android.content.Context
import android.content.Intent
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
 * 参考 Flyme Auto 版 QQ音乐 MediaCenterManager 与 MediaCenterFocusUseCase 重构实现
 *
 * 核心能力：
 * 1. 动态探测车机 MediaCenter 服务与 SDK 环境，非车机设备安全降级（零性能开销、零崩溃风险）
 * 2. 注册 OrbitCarMusicClient，并调用 updateMediaSourceTypeList，使应用在车机顶栏【播放源下拉菜单】中高亮显示
 * 3. 注册 MusicRecoveryIntent，使车机下拉切换音源或冷启动时能准确唤起与恢复播放
 * 4. 实时争夺车机播放焦点 (requestPlay)，使方向盘切歌按键准确派发给本播放器
 * 5. 双向同步播放元数据、歌单列表 (updateMediaList)、高保真封面、歌词与播放进度至车机中控与仪表盘 HUD
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
    private var musicClientInstance: OrbitCarMusicClient? = null

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

            Log.i(TAG, "★ 检测到车载系统支持 ECARX MediaCenterAPI，启动车机多媒体与音源桥接初始化...")

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
            Log.i(TAG, "已调用 MediaCenterAPI.init，等待车机底层服务握手就绪...")
        } catch (e: Throwable) {
            Log.w(TAG, "初始化 ECARX MediaCenter 异常: ${e.message}", e)
        }
    }

    /**
     * 当与车机 MediaCenter 服务连接就绪时回调
     * 参考 QQ音乐 MediaCenterManager.U 完整实现
     */
    private fun onMediaCenterReady() {
        val app = appContext ?: return
        val api = apiInstance ?: return

        try {
            Log.i(TAG, "★ 车机媒体中心已就绪，正在注册 OrbitCarMusicClient...")

            // 1. 实例化音乐客户端
            val musicClient = OrbitCarMusicClient(app)
            musicClientInstance = musicClient

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

            // 3. 【核心修复 1】向车机声明支持的音源类型列表（使车机顶栏播放源下拉菜单能够生成入口）
            val supportedSources = intArrayOf(SourceType.SOURCE_TYPE_ONLINE, SourceType.SOURCE_TYPE_LOCAL)
            val updateListResult = invokeApi("updateMediaSourceTypeList", token, supportedSources)
            Log.i(TAG, "★ updateMediaSourceTypeList([6, 0]) -> $updateListResult")

            // 4. 【核心修复 2】向车机注册音源恢复 Intent（响应下拉选择、熄火唤醒续播）
            try {
                val recoveryIntent = Intent("com.orbit.music.recovery").setPackage(app.packageName)
                val regIntentRet = invokeApi("registerMusicRecoveryIntent", token, 0, recoveryIntent)
                Log.i(TAG, "★ registerMusicRecoveryIntent(com.orbit.music.recovery) -> $regIntentRet")
            } catch (e: Throwable) {
                Log.w(TAG, "registerMusicRecoveryIntent 异常: ${e.message}")
            }

            // 5. 【核心修复 3】向车机声明支持收藏功能
            try {
                invokeApi("declareSupportCollectTypes", token, intArrayOf(0, -1))
            } catch (e: Throwable) {
                // 兼容处理
            }

            // 6. 更新当前音源类型 (在线音乐 SOURCE_TYPE_ONLINE = 6)
            invokeApi("updateCurrentSourceType", token, SourceType.SOURCE_TYPE_ONLINE)

            // 7. 立即向车机全量同步当前状态与播放列表
            pushCurrentStateToCar()

            // 8. 启动播放状态监听与进度条定时器
            startStateObserver()
            startProgressTicker()
        } catch (e: Throwable) {
            Log.e(TAG, "注册 OrbitCarMusicClient 失败: ${e.message}", e)
        }
    }

    /**
     * 主动向车机媒体中心争夺播放焦点 (requestPlay)
     * 参考 QQ音乐 MediaCenterFocusUseCase.requestPlay
     *
     * 作用：确保当前应用成为车机 Active Focus Client，方向盘切歌按键才能正确分发至本播放器！
     */
    fun takeFocus(): Boolean {
        if (apiInstance == null) return false
        val token = clientToken ?: return false
        if (!isReady) return false

        return try {
            val app = appContext
            val myPkg = app?.packageName ?: ""

            // 检查当前焦点是否已属于自身
            val currentFocusClient = try {
                invokeApi("queryCurrentFocusClient", token) as? String
            } catch (e: Throwable) {
                null
            }

            if (currentFocusClient == myPkg && !myPkg.isEmpty()) {
                Log.d(TAG, "takeFocus: 当前已持有车机媒体焦点，无需重复申请")
                true
            } else {
                Log.i(TAG, "takeFocus: 请求车机媒体播放焦点 (旧焦点: $currentFocusClient)...")
                val focusRet = invokeApi("requestPlay", token)
                val granted = (focusRet as? Boolean) ?: true
                Log.i(TAG, "takeFocus: requestPlay 返回 granted=$granted")

                if (granted) {
                    val currentSource = getCurrentSourceType()
                    invokeApi("updateCurrentSourceType", token, currentSource)
                    // 同步刷新一次媒体列表
                    updateCarMediaList()
                }
                granted
            }
        } catch (e: Throwable) {
            Log.w(TAG, "takeFocus failed: ${e.message}")
            false
        }
    }

    /**
     * 当车机顶栏下拉音源切换、或收到车机广播、或恢复服务唤起时响应
     */
    fun onCarSourceSwitched(sourceType: Int = SourceType.SOURCE_TYPE_ONLINE) {
        Log.i(TAG, "★ 车机音源切换唤起: sourceType=$sourceType")
        val app = appContext ?: return
        val playerManager = MusicPlayerManager.getInstance(app)

        takeFocus()
        if (!playerManager.playbackState.value.isPlaying) {
            playerManager.play()
        }
    }

    /**
     * 监听播放器状态变更，实时推送元数据、列表与歌词
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
                            old.currentIndex == new.currentIndex &&
                            old.currentPlaylist.size == new.currentPlaylist.size &&
                            old.repeatMode == new.repeatMode &&
                            old.isShuffleEnabled == new.isShuffleEnabled
                }
                .collectLatest { state ->
                    // 当播放状态变为 PLAYING 时，自动确保夺取车机播放焦点
                    if (state.isPlaying) {
                        takeFocus()
                    }
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
            musicClientInstance?.currentPlaybackInfo = playbackInfo

            var stateMethod: Method? = null
            for (m in api.javaClass.methods) {
                if (m.name == "updateMusicPlaybackState" && m.parameterTypes.size == 2) {
                    stateMethod = m
                    break
                }
            }
            stateMethod?.invoke(api, token, playbackInfo)

            // 3. 【核心补充】向车机上报当前播放列表 updateMediaList (供车机方向盘、中控卡片切歌与展示)
            updateCarMediaList()

            // 4. 上报当前曲目 MediaContent (用于中控卡片和桌面 Widget 显示)
            val song = state.currentSong
            if (song != null) {
                val mediaInfo = OrbitCarMediaInfo(song, state.currentIndex, cachedLyricText)
                invokeApi("updateMediaContent", token, listOf(mediaInfo))
            }
        } catch (e: Throwable) {
            Log.w(TAG, "pushState to car failed: ${e.message}")
        }
    }

    /**
     * 上报当前播放列表至车机
     */
    private fun updateCarMediaList() {
        val app = appContext ?: return
        val token = clientToken ?: return
        val playerManager = MusicPlayerManager.getInstance(app)
        val playlist = playerManager.playbackState.value.currentPlaylist

        try {
            val limitedList = if (playlist.size > 300) playlist.take(300) else playlist
            val mediaInfoList = limitedList.mapIndexed { index, song ->
                OrbitCarMediaInfo(song, index)
            }
            val mediaListInfo = OrbitCarMediaListInfo(mediaInfoList)
            invokeApi("updateMediaList", token, mediaListInfo)
        } catch (e: Throwable) {
            Log.d(TAG, "updateMediaList failed: ${e.message}")
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
     * 反射调用 MediaCenterAPI 内部方法
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
