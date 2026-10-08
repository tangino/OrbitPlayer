package com.orbit.music.data.online.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.orbit.music.data.online.auth.model.PlatformAccount
import com.orbit.music.data.online.auth.model.QrCheckResult
import com.orbit.music.data.online.auth.model.QrCodeData
import com.orbit.music.data.online.auth.service.IPlatformAuthService
import com.orbit.music.data.online.auth.service.KugouMusicAuthService
import com.orbit.music.data.online.auth.service.QQMusicAuthService
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 在线音乐平台账号及登录凭据统一管理器
 */
class PlatformAccountManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PlatformAccountMgr"
        private const val PREFS_NAME = "platform_auth_accounts"

        @Volatile
        private var instance: PlatformAccountManager? = null

        fun getInstance(context: Context): PlatformAccountManager {
            return instance ?: synchronized(this) {
                instance ?: PlatformAccountManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 平台服务实例注册表
    private val authServices: Map<OnlinePlatform, IPlatformAuthService> = mapOf(
        OnlinePlatform.QQ to QQMusicAuthService(),
        OnlinePlatform.KUGOU to KugouMusicAuthService()
    )

    private val _accounts = MutableStateFlow<Map<OnlinePlatform, PlatformAccount?>>(emptyMap())
    val accounts: StateFlow<Map<OnlinePlatform, PlatformAccount?>> = _accounts.asStateFlow()

    init {
        loadSavedAccounts()
        try {
            com.orbit.music.data.online.repository.OnlineMusicRepository.getInstance().bindAuthManager(this)
        } catch (_: Exception) {
        }
    }

    /**
     * 从本地存储加载所有已保存的平台登录账号
     */
    private fun loadSavedAccounts() {
        val map = mutableMapOf<OnlinePlatform, PlatformAccount?>()
        for (platform in OnlinePlatform.values()) {
            val jsonStr = prefs.getString(platform.id, null)
            if (!jsonStr.isNullOrBlank()) {
                try {
                    val account = deserializeAccount(jsonStr)
                    map[platform] = account
                } catch (e: Exception) {
                    Log.e(TAG, "解析 ${platform.displayName} 账号数据失败: ${e.message}")
                }
            } else {
                map[platform] = null
            }
        }
        _accounts.value = map
    }

    /**
     * 获取指定平台的认证服务
     */
    fun getAuthService(platform: OnlinePlatform): IPlatformAuthService? {
        return authServices[platform]
    }

    /**
     * 获取指定平台当前已登录的账号
     */
    fun getAccount(platform: OnlinePlatform): PlatformAccount? {
        return _accounts.value[platform]
    }

    /**
     * 判断指定平台是否已登录
     */
    fun isLoggedIn(platform: OnlinePlatform): Boolean {
        val account = getAccount(platform)
        return account != null && account.userId.isNotBlank()
    }

    /**
     * 获取指定平台的 Cookie 请求头
     */
    fun getCookieHeader(platform: OnlinePlatform): String {
        return getAccount(platform)?.toCookieHeader() ?: ""
    }

    /**
     * 获取二维码
     */
    suspend fun getQrCode(platform: OnlinePlatform): QrCodeData = withContext(Dispatchers.IO) {
        val service = authServices[platform] ?: throw IllegalArgumentException("暂不支持 ${platform.displayName} 登录服务")
        service.getQrCode()
    }

    /**
     * 检查扫码状态
     */
    suspend fun checkQrStatus(platform: OnlinePlatform, key: String, extra: Map<String, String> = emptyMap()): QrCheckResult = withContext(Dispatchers.IO) {
        val service = authServices[platform] ?: throw IllegalArgumentException("暂不支持 ${platform.displayName} 登录服务")
        val result = service.checkQrStatus(key, extra)
        if (result.status == com.orbit.music.data.online.auth.model.QrStatus.SUCCESS && result.account != null) {
            saveAccount(result.account)
        }
        result
    }

    /**
     * 保存或更新平台账号
     */
    fun saveAccount(account: PlatformAccount) {
        try {
            val jsonStr = serializeAccount(account)
            prefs.edit().putString(account.platform.id, jsonStr).apply()

            val updated = _accounts.value.toMutableMap()
            updated[account.platform] = account
            _accounts.value = updated
            Log.i(TAG, "成功保存 ${account.platform.displayName} 账号: ${account.nickname} (${account.userId})")
        } catch (e: Exception) {
            Log.e(TAG, "保存 ${account.platform.displayName} 账号异常: ${e.message}", e)
        }
    }

    /**
     * 登出/清除指定平台账号
     */
    fun logout(platform: OnlinePlatform) {
        prefs.edit().remove(platform.id).apply()
        val updated = _accounts.value.toMutableMap()
        updated[platform] = null
        _accounts.value = updated
        Log.i(TAG, "已退出 ${platform.displayName} 登录")
    }

    /**
     * 刷新指定平台账号的个人资料与 VIP 状态
     */
    suspend fun refreshAccount(platform: OnlinePlatform): PlatformAccount? = withContext(Dispatchers.IO) {
        val current = getAccount(platform) ?: return@withContext null
        val service = authServices[platform] ?: return@withContext current
        try {
            val refreshed = service.refreshUserInfo(current)
            saveAccount(refreshed)
            refreshed
        } catch (e: Exception) {
            Log.e(TAG, "刷新 ${platform.displayName} 用户信息失败: ${e.message}")
            current
        }
    }

    /**
     * 获取用户在指定平台的自建/收藏歌单
     */
    suspend fun fetchUserPlaylists(platform: OnlinePlatform): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val account = getAccount(platform) ?: return@withContext emptyList()
        val service = authServices[platform] ?: return@withContext emptyList()
        try {
            service.getUserPlaylists(account)
        } catch (e: Exception) {
            Log.e(TAG, "获取 ${platform.displayName} 用户歌单失败: ${e.message}")
            emptyList()
        }
    }

    // ================= 序列化与反序列化工具 =================

    private fun serializeAccount(account: PlatformAccount): String {
        val json = JSONObject().apply {
            put("platform", account.platform.id)
            put("userId", account.userId)
            put("nickname", account.nickname)
            put("avatarUrl", account.avatarUrl)
            put("isVip", account.isVip)
            put("vipLevel", account.vipLevel)
            put("vipExpireTime", account.vipExpireTime)
            put("updatedAt", account.updatedAt)

            val cookiesJson = JSONObject()
            account.cookies.forEach { (k, v) -> cookiesJson.put(k, v) }
            put("cookies", cookiesJson)

            val tokensJson = JSONObject()
            account.tokens.forEach { (k, v) -> tokensJson.put(k, v) }
            put("tokens", tokensJson)

            val extraJson = JSONObject()
            account.extraData.forEach { (k, v) -> extraJson.put(k, v) }
            put("extraData", extraJson)
        }
        return json.toString()
    }

    private fun deserializeAccount(jsonStr: String): PlatformAccount {
        val json = JSONObject(jsonStr)
        val platformId = json.getString("platform")
        val platform = OnlinePlatform.values().firstOrNull { it.id == platformId } ?: OnlinePlatform.QQ

        val cookiesMap = mutableMapOf<String, String>()
        val cookiesJson = json.optJSONObject("cookies")
        cookiesJson?.keys()?.forEach { key ->
            cookiesMap[key] = cookiesJson.optString(key, "")
        }

        val tokensMap = mutableMapOf<String, String>()
        val tokensJson = json.optJSONObject("tokens")
        tokensJson?.keys()?.forEach { key ->
            tokensMap[key] = tokensJson.optString(key, "")
        }

        val extraMap = mutableMapOf<String, String>()
        val extraJson = json.optJSONObject("extraData")
        extraJson?.keys()?.forEach { key ->
            extraMap[key] = extraJson.optString(key, "")
        }

        return PlatformAccount(
            platform = platform,
            userId = json.optString("userId", ""),
            nickname = json.optString("nickname", ""),
            avatarUrl = json.optString("avatarUrl", ""),
            isVip = json.optBoolean("isVip", false),
            vipLevel = json.optString("vipLevel", ""),
            vipExpireTime = json.optLong("vipExpireTime", 0L),
            cookies = cookiesMap,
            tokens = tokensMap,
            extraData = extraMap,
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }
}
