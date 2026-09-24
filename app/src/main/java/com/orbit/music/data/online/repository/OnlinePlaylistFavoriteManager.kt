package com.orbit.music.data.online.repository

import android.content.Context
import android.content.SharedPreferences
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * 用户网络歌单收藏管理器（单例，持久化至本地）
 */
class OnlinePlaylistFavoriteManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "online_playlist_favorites_prefs"
        private const val KEY_FAVORITES_JSON = "favorite_playlists_json"

        @Volatile
        private var instance: OnlinePlaylistFavoriteManager? = null

        fun getInstance(context: Context): OnlinePlaylistFavoriteManager {
            return instance ?: synchronized(this) {
                instance ?: OnlinePlaylistFavoriteManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<OnlinePlaylist>>(emptyList())
    val favorites: StateFlow<List<OnlinePlaylist>> = _favorites.asStateFlow()

    init {
        loadFavoritesFromPrefs()
    }

    private fun loadFavoritesFromPrefs() {
        val jsonStr = prefs.getString(KEY_FAVORITES_JSON, null)
        if (jsonStr.isNullOrBlank()) {
            _favorites.value = emptyList()
            return
        }

        try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<OnlinePlaylist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val platformStr = obj.optString("platform", OnlinePlatform.NETEASE.name)
                val platform = try {
                    OnlinePlatform.valueOf(platformStr)
                } catch (_: Exception) {
                    OnlinePlatform.NETEASE
                }

                val playlist = OnlinePlaylist(
                    id = obj.optString("id"),
                    platform = platform,
                    title = obj.optString("title"),
                    coverUrl = obj.optString("coverUrl"),
                    playCount = obj.optLong("playCount", 0L),
                    trackCount = obj.optInt("trackCount", 0),
                    creatorName = obj.optString("creatorName").takeIf { it.isNotBlank() },
                    creatorAvatarUrl = obj.optString("creatorAvatarUrl").takeIf { it.isNotBlank() },
                    description = obj.optString("description").takeIf { it.isNotBlank() }
                )
                if (playlist.id.isNotBlank()) {
                    list.add(playlist)
                }
            }
            _favorites.value = list
        } catch (_: Exception) {
            _favorites.value = emptyList()
        }
    }

    private fun saveFavoritesToPrefs(list: List<OnlinePlaylist>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("platform", item.platform.name)
                    put("title", item.title)
                    put("coverUrl", item.coverUrl)
                    put("playCount", item.playCount)
                    put("trackCount", item.trackCount)
                    put("creatorName", item.creatorName ?: "")
                    put("creatorAvatarUrl", item.creatorAvatarUrl ?: "")
                    put("description", item.description ?: "")
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_FAVORITES_JSON, jsonArray.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /**
     * 判断指定网络歌单是否已被收藏
     */
    fun isFavorite(playlist: OnlinePlaylist): Boolean {
        return _favorites.value.any { it.platform == playlist.platform && it.id == playlist.id }
    }

    /**
     * 切换收藏状态（已收藏则移除，未收藏则添加），返回最新收藏状态
     */
    fun toggleFavorite(playlist: OnlinePlaylist): Boolean {
        val current = _favorites.value.toMutableList()
        val index = current.indexOfFirst { it.platform == playlist.platform && it.id == playlist.id }
        val nowFavorite: Boolean
        if (index >= 0) {
            current.removeAt(index)
            nowFavorite = false
        } else {
            current.add(0, playlist)
            nowFavorite = true
        }
        _favorites.value = current
        saveFavoritesToPrefs(current)
        return nowFavorite
    }

    /**
     * 移除收藏
     */
    fun removeFavorite(playlist: OnlinePlaylist) {
        val current = _favorites.value.toMutableList()
        val removed = current.removeAll { it.platform == playlist.platform && it.id == playlist.id }
        if (removed) {
            _favorites.value = current
            saveFavoritesToPrefs(current)
        }
    }

    /**
     * 获取指定平台的已收藏歌单列表
     */
    fun getFavoritesByPlatform(platform: OnlinePlatform): List<OnlinePlaylist> {
        return _favorites.value.filter { it.platform == platform }
    }
}
