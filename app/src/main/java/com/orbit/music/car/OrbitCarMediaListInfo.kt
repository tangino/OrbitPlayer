package com.orbit.music.car

import com.ecarx.eas.sdk.mediacenter.MediaInfo
import com.ecarx.eas.sdk.mediacenter.MediaListInfo
import com.ecarx.eas.sdk.mediacenter.SourceType

/**
 * 车机媒体中心播放列表数据结构 (适配吉利/领克/银河/极氪 ECARX 架构)
 * 参考 Flyme Auto 版 QQ音乐 FaMediaListInfo 设计
 */
class OrbitCarMediaListInfo(
    private val mediaList: List<MediaInfo>,
    private val playlistId: String = "orbit_current_playlist",
    private val sourceType: Int = SourceType.SOURCE_TYPE_ONLINE
) : MediaListInfo() {

    override fun getMediaList(): List<MediaInfo> = mediaList

    override fun getMediaListId(): String = playlistId

    override fun getMediaListType(): Int = 17 // 17 表示通用播放队列，与车机端规范保持一致

    override fun getSourceType(): Int = sourceType
}
