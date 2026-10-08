package com.orbit.music.data.online.engine

import android.content.Context
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.repository.OnlinePlaylistFavoriteManager
import com.orbit.music.data.playlist.PlaylistGroupManager
import com.orbit.music.data.repository.MusicRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 云端同步歌曲条目结构
 */
data class CloudPlaylistSong(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val path: String,
    val isOnline: Boolean = false,
    val sourcePlatform: String? = null
)

/**
 * 云端同步本地歌单结构
 */
data class CloudLocalPlaylist(
    val name: String,
    val groupName: String = "默认",
    val songs: List<CloudPlaylistSong> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 云端歌单备份数据全量载荷
 */
data class CloudPlaylistBackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val localPlaylists: List<CloudLocalPlaylist> = emptyList(),
    val onlinePlaylists: List<OnlinePlaylist> = emptyList(),
    val groups: List<String> = emptyList()
)

/**
 * 歌单同步模式（增量合并 / 全量覆盖）
 */
enum class PlaylistSyncMode(val title: String, val desc: String) {
    MERGE("增量合并", "保留现有歌单与曲目，智能去重追加新内容（推荐）"),
    OVERWRITE("全量覆盖", "清除目标歌单现有曲目，完全替换为目标端内容")
}

/**
 * 歌单多端云同步管理器（选择性上传与下载）
 */
class CloudPlaylistSyncManager private constructor(private val context: Context) {

    private val authManager = CloudSourceSyncManager.getInstance(context)
    private val repository = MusicRepository.getInstance(context)
    private val groupManager = PlaylistGroupManager.getInstance(context)
    private val onlineFavoriteManager = OnlinePlaylistFavoriteManager.getInstance(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    companion object {
        @Volatile
        private var instance: CloudPlaylistSyncManager? = null

        fun getInstance(context: Context): CloudPlaylistSyncManager {
            return instance ?: synchronized(this) {
                instance ?: CloudPlaylistSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 上传选中的本地自建歌单与在线收藏歌单至云端
     */
    suspend fun uploadSelectedPlaylists(
        selectedLocalPlaylists: List<Playlist>,
        selectedOnlinePlaylists: List<OnlinePlaylist>,
        includeGroups: List<String>,
        syncMode: PlaylistSyncMode = PlaylistSyncMode.MERGE
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val username = authManager.getUsername()
            val password = authManager.getPassword()

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext Result.failure(Exception("请先在云同步中心登录账号"))
            }

            val baseUrl = authManager.getServerUrl()

            // 1. 序列化选中的本地自建歌单及其歌曲
            val localArray = JSONArray()
            for (p in selectedLocalPlaylists) {
                val songs = repository.getSongsInPlaylist(p.id)
                val pObj = JSONObject().apply {
                    put("name", p.name)
                    put("groupName", p.groupName)
                    put("createdAt", p.createdAt)
                    val songsArr = JSONArray()
                    for (s in songs) {
                        val sObj = JSONObject().apply {
                            put("title", s.title)
                            put("artist", s.artist)
                            put("album", s.album)
                            put("durationMs", s.durationMs)
                            put("path", s.path)
                            put("isOnline", s.isOnlineSong)
                            put("sourcePlatform", s.sourcePlatform?.name ?: "")
                        }
                        songsArr.put(sObj)
                    }
                    put("songs", songsArr)
                }
                localArray.put(pObj)
            }

            // 2. 序列化选中的在线收藏歌单
            val onlineArray = JSONArray()
            for (op in selectedOnlinePlaylists) {
                val opObj = JSONObject().apply {
                    put("id", op.id)
                    put("platform", op.platform.name)
                    put("title", op.title)
                    put("coverUrl", op.coverUrl)
                    put("trackCount", op.trackCount)
                    put("creatorName", op.creatorName ?: "")
                    put("creatorAvatarUrl", op.creatorAvatarUrl ?: "")
                    put("description", op.description ?: "")
                    put("customGroup", op.customGroup)
                }
                onlineArray.put(opObj)
            }

            // 3. 序列化相关分组
            val groupsArray = JSONArray()
            includeGroups.forEach { groupsArray.put(it) }

            val totalCount = selectedLocalPlaylists.size + selectedOnlinePlaylists.size

            val payload = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("playlistsJson", localArray.toString())
                put("onlineFavoritesJson", onlineArray.toString())
                put("groupsJson", groupsArray.toString())
                put("playlistCount", totalCount)
                put("mode", if (syncMode == PlaylistSyncMode.OVERWRITE) "overwrite" else "merge")
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/user/playlists/backup")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching { JSONObject(resStr).optString("message") }.getOrNull()
                    ?: "上传备份失败 (HTTP ${response.code})"
                return@withContext Result.failure(Exception(errMsg))
            }

            val resJson = JSONObject(resStr)
            if (resJson.optBoolean("success", false)) {
                val modeTitle = if (syncMode == PlaylistSyncMode.OVERWRITE) "全量覆盖" else "增量合并"
                Result.success("已完成${modeTitle}备份：上传 ${selectedLocalPlaylists.size} 个本地歌单与 ${selectedOnlinePlaylists.size} 个在线歌单")
            } else {
                Result.failure(Exception(resJson.optString("message", "上传失败")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从云端拉取当前账号已备份的所有歌单与分组信息
     */
    suspend fun fetchCloudPlaylists(): Result<CloudPlaylistBackupData> = withContext(Dispatchers.IO) {
        try {
            val username = authManager.getUsername()
            val password = authManager.getPassword()

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext Result.failure(Exception("请先登录云同步账号"))
            }

            val baseUrl = authManager.getServerUrl()
            val payload = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/user/playlists/restore")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching { JSONObject(resStr).optString("message") }.getOrNull()
                    ?: if (response.code == 404) "云端暂无歌单备份数据" else "拉取云端歌单失败 (HTTP ${response.code})"
                return@withContext Result.failure(Exception(errMsg))
            }

            val resJson = JSONObject(resStr)
            if (!resJson.optBoolean("success", false)) {
                return@withContext Result.failure(Exception(resJson.optString("message", "拉取失败")))
            }

            val record = resJson.optJSONObject("data") ?: return@withContext Result.failure(Exception("云端无有效数据"))
            val playlistsStr = record.optString("playlists_json", "[]")
            val onlineFavsStr = record.optString("online_favorites_json", "[]")
            val groupsStr = record.optString("groups_json", "[]")
            val updatedAt = record.optLong("updated_at", System.currentTimeMillis())

            // 1. 解析本地歌单
            val localPlaylists = mutableListOf<CloudLocalPlaylist>()
            val pArr = JSONArray(playlistsStr)
            for (i in 0 until pArr.length()) {
                val pObj = pArr.getJSONObject(i)
                val songs = mutableListOf<CloudPlaylistSong>()
                val sArr = pObj.optJSONArray("songs")
                if (sArr != null) {
                    for (j in 0 until sArr.length()) {
                        val sObj = sArr.getJSONObject(j)
                        songs.add(
                            CloudPlaylistSong(
                                title = sObj.optString("title"),
                                artist = sObj.optString("artist"),
                                album = sObj.optString("album"),
                                durationMs = sObj.optLong("durationMs", 0L),
                                path = sObj.optString("path"),
                                isOnline = sObj.optBoolean("isOnline", false),
                                sourcePlatform = sObj.optString("sourcePlatform").takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                localPlaylists.add(
                    CloudLocalPlaylist(
                        name = pObj.optString("name"),
                        groupName = pObj.optString("groupName", "默认"),
                        songs = songs,
                        createdAt = pObj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }

            // 2. 解析在线收藏歌单
            val onlinePlaylists = mutableListOf<OnlinePlaylist>()
            val oArr = JSONArray(onlineFavsStr)
            for (i in 0 until oArr.length()) {
                val oObj = oArr.getJSONObject(i)
                val platform = runCatching { OnlinePlatform.valueOf(oObj.optString("platform")) }.getOrDefault(OnlinePlatform.NETEASE)
                onlinePlaylists.add(
                    OnlinePlaylist(
                        id = oObj.optString("id"),
                        platform = platform,
                        title = oObj.optString("title"),
                        coverUrl = oObj.optString("coverUrl"),
                        trackCount = oObj.optInt("trackCount", 0),
                        creatorName = oObj.optString("creatorName").takeIf { it.isNotBlank() },
                        creatorAvatarUrl = oObj.optString("creatorAvatarUrl").takeIf { it.isNotBlank() },
                        description = oObj.optString("description").takeIf { it.isNotBlank() },
                        customGroup = oObj.optString("customGroup", "默认")
                    )
                )
            }

            // 3. 解析自定义分组
            val groupsList = mutableListOf<String>()
            val gArr = JSONArray(groupsStr)
            for (i in 0 until gArr.length()) {
                val g = gArr.getString(i)
                if (g.isNotBlank()) groupsList.add(g)
            }

            Result.success(
                CloudPlaylistBackupData(
                    timestamp = updatedAt,
                    localPlaylists = localPlaylists,
                    onlinePlaylists = onlinePlaylists,
                    groups = groupsList
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 将用户勾选的云端歌单恢复（增量合并或全量覆盖）到本机
     */
    suspend fun restoreSelectedPlaylists(
        selectedLocal: List<CloudLocalPlaylist>,
        selectedOnline: List<OnlinePlaylist>,
        restoreGroups: List<String>,
        syncMode: PlaylistSyncMode = PlaylistSyncMode.MERGE
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 恢复分组
            restoreGroups.forEach { g ->
                if (g.isNotBlank() && g != "默认") {
                    groupManager.addGroup(g)
                }
            }

            var localRestoredCount = 0
            val allLocalSongs = repository.allSongs.value
            val existingPlaylists = repository.playlists.value

            // 2. 恢复本地自建歌单
            for (cloudP in selectedLocal) {
                if (cloudP.name.isBlank()) continue
                groupManager.addGroup(cloudP.groupName)

                // 查找本地是否存在同名歌单
                val existingP = existingPlaylists.find { it.name.trim().equals(cloudP.name.trim(), ignoreCase = true) }
                val targetPlaylistId = if (existingP != null) {
                    if (syncMode == PlaylistSyncMode.OVERWRITE) {
                        // 覆盖模式：清空现有歌单中原有歌曲
                        repository.clearPlaylistSongs(existingP.id)
                    }
                    if (cloudP.groupName.isNotBlank() && cloudP.groupName != "默认") {
                        repository.updatePlaylistGroup(existingP.id, cloudP.groupName)
                    }
                    existingP.id
                } else {
                    repository.createPlaylist(cloudP.name, cloudP.groupName)
                }

                // 增量合并时，查出本地已有歌曲 ID 集合进行精准去重
                val currentSongIds = if (syncMode == PlaylistSyncMode.MERGE && existingP != null) {
                    repository.getSongsInPlaylist(targetPlaylistId).map { it.id }.toSet()
                } else {
                    emptySet()
                }

                // 将歌单内的歌曲关联进来
                val songIdsToAdd = mutableListOf<Long>()
                for (cs in cloudP.songs) {
                    // 尝试在当前设备本地媒体库中匹配歌曲（同名+同歌手，或同路径）
                    val matchedSong = allLocalSongs.find {
                        it.path == cs.path || (it.title.equals(cs.title, ignoreCase = true) && it.artist.equals(cs.artist, ignoreCase = true))
                    }
                    if (matchedSong != null && matchedSong.id !in currentSongIds) {
                        songIdsToAdd.add(matchedSong.id)
                    }
                }
                if (songIdsToAdd.isNotEmpty()) {
                    repository.addSongsToPlaylist(targetPlaylistId, songIdsToAdd)
                }
                localRestoredCount++
            }

            // 3. 恢复在线收藏歌单
            var onlineRestoredCount = 0
            for (op in selectedOnline) {
                if (op.id.isNotBlank()) {
                    groupManager.addGroup(op.customGroup)
                    onlineFavoriteManager.addFavorite(op)
                    onlineRestoredCount++
                }
            }

            val modeTitle = if (syncMode == PlaylistSyncMode.OVERWRITE) "全量覆盖" else "增量合并"
            Result.success("已完成${modeTitle}恢复：成功恢复 $localRestoredCount 个本地歌单与 $onlineRestoredCount 个在线收藏歌单")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 删除云端选中的特定自建歌单和在线收藏歌单
     */
    suspend fun deleteSelectedCloudPlaylists(
        selectedLocalNames: List<String>,
        selectedOnlineIds: List<String>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val username = authManager.getUsername()
            val password = authManager.getPassword()

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext Result.failure(Exception("请先在云同步中心登录账号"))
            }

            val baseUrl = authManager.getServerUrl()
            val localArr = JSONArray()
            selectedLocalNames.forEach { localArr.put(it) }

            val onlineArr = JSONArray()
            selectedOnlineIds.forEach { onlineArr.put(it) }

            val payload = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("deleteAll", false)
                put("localPlaylistNames", localArr)
                put("onlinePlaylistIds", onlineArr)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/user/playlists/delete")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching { JSONObject(resStr).optString("message") }.getOrNull()
                    ?: "删除云端歌单失败 (HTTP ${response.code})"
                return@withContext Result.failure(Exception(errMsg))
            }

            val resJson = JSONObject(resStr)
            if (resJson.optBoolean("success", false)) {
                Result.success(resJson.optString("message", "已删除所选云端歌单"))
            } else {
                Result.failure(Exception(resJson.optString("message", "删除失败")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 一键清空当前账号下的所有云端歌单备份
     */
    suspend fun clearAllCloudPlaylists(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val username = authManager.getUsername()
            val password = authManager.getPassword()

            if (username.isNullOrBlank() || password.isNullOrBlank()) {
                return@withContext Result.failure(Exception("请先在云同步中心登录账号"))
            }

            val baseUrl = authManager.getServerUrl()
            val payload = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("deleteAll", true)
            }

            val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/api/user/playlists/delete")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val resStr = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMsg = runCatching { JSONObject(resStr).optString("message") }.getOrNull()
                    ?: "清空云端歌单失败 (HTTP ${response.code})"
                return@withContext Result.failure(Exception(errMsg))
            }

            val resJson = JSONObject(resStr)
            if (resJson.optBoolean("success", false)) {
                Result.success(resJson.optString("message", "已清空云端所有歌单备份数据"))
            } else {
                Result.failure(Exception(resJson.optString("message", "清空失败")))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
