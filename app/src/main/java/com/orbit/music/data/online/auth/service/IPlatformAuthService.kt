package com.orbit.music.data.online.auth.service

import com.orbit.music.data.online.auth.model.PlatformAccount
import com.orbit.music.data.online.auth.model.QrCheckResult
import com.orbit.music.data.online.auth.model.QrCodeData
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist

/**
 * 平台登录与认证服务接口
 */
interface IPlatformAuthService {
    val platform: OnlinePlatform

    /**
     * 获取登录二维码信息（包含二维码数据及轮询密钥 key）
     */
    suspend fun getQrCode(): QrCodeData

    /**
     * 轮询检查二维码扫码及确认状态
     * @param key 二维码唯一 key
     * @param extra 额外参数（如 QQ 的 qrsig 等）
     */
    suspend fun checkQrStatus(key: String, extra: Map<String, String> = emptyMap()): QrCheckResult

    /**
     * 刷新/获取用户详细信息（昵称、头像、VIP状态等）
     */
    suspend fun refreshUserInfo(account: PlatformAccount): PlatformAccount

    /**
     * 获取用户在平台上的自建/收藏歌单
     */
    suspend fun getUserPlaylists(account: PlatformAccount): List<OnlinePlaylist>
}
