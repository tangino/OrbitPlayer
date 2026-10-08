package com.orbit.music.data.online.auth.service

import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.orbit.music.data.online.auth.model.PlatformAccount
import com.orbit.music.data.online.auth.model.QrCheckResult
import com.orbit.music.data.online.auth.model.QrCodeData
import com.orbit.music.data.online.auth.model.QrStatus
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 酷狗音乐扫码认证与用户服务实现
 */
class KugouMusicAuthService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) : IPlatformAuthService {

    override val platform: OnlinePlatform = OnlinePlatform.KUGOU

    companion object {
        private const val TAG = "KugouMusicAuthService"
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        private const val APP_ID = "1014"
        private const val PLAT_ID = "4"
    }

    override suspend fun getQrCode(): QrCodeData = withContext(Dispatchers.IO) {
        val url = "https://login.user.kugou.com/v1/qrcode/get?appid=$APP_ID&platid=$PLAT_ID&clientver=1000"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.kugou.com/")
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: throw IllegalStateException("获取酷狗二维码响应为空")
        val json = JSONObject(bodyStr)
        val data = json.optJSONObject("data") ?: throw IllegalStateException("酷狗二维码数据为空: $bodyStr")

        val key = data.optString("key", "")
        val qrcode = data.optString("qrcode", "")

        if (key.isBlank()) {
            throw IllegalStateException("酷狗二维码凭证 key 获取失败")
        }

        var imageBytes: ByteArray? = null
        var qrUrl: String? = null

        if (qrcode.startsWith("data:image")) {
            val base64Data = qrcode.substringAfter("base64,")
            imageBytes = Base64.decode(base64Data, Base64.DEFAULT)
        } else if (qrcode.startsWith("http://") || qrcode.startsWith("https://")) {
            qrUrl = qrcode
        } else {
            qrUrl = qrcode
        }

        QrCodeData(
            platform = platform,
            key = key,
            qrUrl = qrUrl,
            qrImageBytes = imageBytes,
            expireTimeMs = 120_000L
        )
    }

    override suspend fun checkQrStatus(key: String, extra: Map<String, String>): QrCheckResult = withContext(Dispatchers.IO) {
        val url = "https://login.user.kugou.com/v1/qrcode/check?key=$key&appid=$APP_ID&platid=$PLAT_ID"

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.kugou.com/")
            .build()

        val response = client.newCall(request).execute()
        val bodyStr = response.body?.string() ?: return@withContext QrCheckResult(QrStatus.ERROR, "响应为空")
        val json = JSONObject(bodyStr)
        val data = json.optJSONObject("data")

        val status = data?.optInt("status", -1) ?: json.optInt("status", -1)

        when (status) {
            0 -> QrCheckResult(QrStatus.WAITING, "请使用酷狗音乐 App 扫码")
            1 -> QrCheckResult(QrStatus.SCANNED, "二维码已扫描，请在手机上点击确认")
            4 -> QrCheckResult(QrStatus.EXPIRED, "二维码已失效，请点击刷新")
            2 -> {
                // 登录成功
                val token = data?.optString("token", "") ?: ""
                val userid = data?.optString("userid", "") ?: ""

                if (token.isBlank() || userid.isBlank()) {
                    return@withContext QrCheckResult(QrStatus.ERROR, "登录成功但未能解析到 Token 或 UserID")
                }

                val cookiesMap = mapOf(
                    "token" to token,
                    "userid" to userid,
                    "KugouID" to userid
                )

                val account = PlatformAccount(
                    platform = platform,
                    userId = userid,
                    nickname = "酷狗用户_$userid",
                    cookies = cookiesMap,
                    tokens = mapOf("token" to token, "userid" to userid),
                    updatedAt = System.currentTimeMillis()
                )

                // 尝试丰富用户信息
                val enriched = try {
                    refreshUserInfo(account)
                } catch (e: Exception) {
                    account
                }

                QrCheckResult(QrStatus.SUCCESS, "登录成功", enriched)
            }
            else -> QrCheckResult(QrStatus.ERROR, json.optString("error_msg", "扫码状态异常($status)"))
        }
    }

    override suspend fun refreshUserInfo(account: PlatformAccount): PlatformAccount = withContext(Dispatchers.IO) {
        val token = account.getPrimaryToken()
        val userid = account.userId
        if (token.isBlank() || userid.isBlank()) return@withContext account

        try {
            val url = "https://gateway.kugou.com/v3/user/get_user_info?userid=$userid&token=$token&appid=$APP_ID&platid=$PLAT_ID"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: return@withContext account
            val json = JSONObject(bodyStr)
            val data = json.optJSONObject("data")

            if (data != null) {
                val nickname = data.optString("nickname", account.nickname)
                val pic = data.optString("pic", account.avatarUrl)
                val vipType = data.optInt("vip_type", 0)
                val isVip = vipType > 0
                val vipEndTime = data.optLong("vip_end_time", 0L)

                return@withContext account.copy(
                    nickname = nickname.ifBlank { account.nickname },
                    avatarUrl = pic.ifBlank { account.avatarUrl },
                    isVip = isVip,
                    vipLevel = if (isVip) "VIP $vipType" else "",
                    vipExpireTime = vipEndTime * 1000L,
                    updatedAt = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "刷新酷狗音乐用户信息失败: ${e.message}", e)
        }
        account
    }

    override suspend fun getUserPlaylists(account: PlatformAccount): List<OnlinePlaylist> = withContext(Dispatchers.IO) {
        val token = account.getPrimaryToken()
        val userid = account.userId
        if (token.isBlank() || userid.isBlank()) return@withContext emptyList()

        val list = mutableListOf<OnlinePlaylist>()
        try {
            val url = "https://gateway.kugou.com/v1/user_playlist/get_list?userid=$userid&token=$token&appid=$APP_ID&platid=$PLAT_ID&pagesize=100"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build()

            val response = client.newCall(request).execute()
            val bodyStr = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(bodyStr)
            val data = json.optJSONObject("data")
            val info = data?.optJSONArray("info") ?: json.optJSONArray("info") ?: JSONArray()

            for (i in 0 until info.length()) {
                val item = info.getJSONObject(i)
                val listId = item.optString("listid", item.optString("specialid", ""))
                val title = item.optString("name", item.optString("specialname", "未命名歌单"))
                val pic = item.optString("pic", item.optString("img", ""))
                val count = item.optInt("count", item.optInt("songcount", 0))

                if (listId.isNotBlank()) {
                    list.add(
                        OnlinePlaylist(
                            id = listId,
                            platform = platform,
                            title = title,
                            coverUrl = pic,
                            trackCount = count,
                            creatorName = account.nickname,
                            creatorAvatarUrl = account.avatarUrl,
                            customGroup = "酷狗音乐"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取酷狗音乐用户歌单失败: ${e.message}", e)
        }
        list
    }
}
