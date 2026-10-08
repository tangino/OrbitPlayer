package com.orbit.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.orbit.music.data.model.PlaybackOrigin
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.Song
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * 歌单类别枚举
 */
enum class PlaylistCategory {
    LOCAL_CUSTOM,    // 本地自建歌单
    LOCAL_FAVORITE,  // 我喜欢的音乐
    LOCAL_DISLIKED,  // 不喜欢的音乐
    ONLINE           // 各在线平台歌单（网易云/QQ/酷狗/酷我/咪咕）
}

/**
 * 播放歌单条目模型（涵盖当前播放歌单与上次播放歌单）
 */
data class PlayedPlaylistEntry(
    val id: String,
    val title: String,
    val category: PlaylistCategory,
    val platform: OnlinePlatform? = null,
    val coverUrl: String? = null,
    val songCount: Int = 0,
    val creatorName: String? = null,
    val lastPlayedSongTitle: String? = null,
    val lastPlayedSongArtist: String? = null,
    val playedTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * 判断两个歌单是否为同一个歌单
     */
    fun isSamePlaylist(other: PlayedPlaylistEntry?): Boolean {
        if (other == null) return false
        if (category != other.category) return false
        if (category == PlaylistCategory.ONLINE) {
            return platform == other.platform && id == other.id
        }
        return id == other.id
    }

    /**
     * 校验当前播放来源是否与本歌单匹配
     */
    fun isMatchingOrigin(origin: PlaybackOrigin?): Boolean {
        if (origin == null) return false
        return when (origin) {
            is PlaybackOrigin.PlaylistOrigin -> {
                category != PlaylistCategory.ONLINE && id == origin.playlist.id.toString()
            }
            is PlaybackOrigin.OnlinePlaylistOrigin -> {
                category == PlaylistCategory.ONLINE && platform == origin.onlinePlaylist.platform && id == origin.onlinePlaylist.id
            }
            is PlaybackOrigin.OnlineAlbumOrigin -> {
                category == PlaylistCategory.ONLINE && platform == origin.onlineAlbum.platform && id == origin.onlineAlbum.id
            }
            is PlaybackOrigin.OnlineArtistOrigin -> {
                category == PlaylistCategory.ONLINE && platform == origin.onlineArtist.platform && id == "artist_${origin.onlineArtist.id}"
            }
            is PlaybackOrigin.OnlineSquareOrigin -> {
                category == PlaylistCategory.ONLINE && platform == origin.platform && id == "square_${origin.platform.name}_${origin.tabIndex}"
            }
            else -> false
        }
    }
}

/**
 * 当前播放歌单与上次播放歌单管理器（单例，支持冷启动持久化）
 */
class PlayedPlaylistManager private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "played_playlists_prefs"
        private const val KEY_CURRENT_PLAYLIST_JSON = "current_played_playlist_json"
        private const val KEY_PREVIOUS_PLAYLIST_JSON = "previous_played_playlist_json"

        @Volatile
        private var instance: PlayedPlaylistManager? = null

        fun getInstance(context: Context): PlayedPlaylistManager {
            return instance ?: synchronized(this) {
                instance ?: PlayedPlaylistManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _currentPlaylist = MutableStateFlow<PlayedPlaylistEntry?>(null)
    val currentPlaylist: StateFlow<PlayedPlaylistEntry?> = _currentPlaylist.asStateFlow()

    private val _previousPlaylist = MutableStateFlow<PlayedPlaylistEntry?>(null)
    val previousPlaylist: StateFlow<PlayedPlaylistEntry?> = _previousPlaylist.asStateFlow()

    init {
        loadFromPrefs()
    }

    /**
     * 从本地持久化加载历史记录
     */
    private fun loadFromPrefs() {
        val currentJson = prefs.getString(KEY_CURRENT_PLAYLIST_JSON, null)
        val previousJson = prefs.getString(KEY_PREVIOUS_PLAYLIST_JSON, null)

        _currentPlaylist.value = currentJson?.let { parseEntry(it) }
        _previousPlaylist.value = previousJson?.let { parseEntry(it) }
    }

    /**
     * 记录新播放的歌单
     */
    fun recordPlayed(newEntry: PlayedPlaylistEntry) {
        val current = _currentPlaylist.value

        // 如果新歌单与当前歌单相同，仅更新时间戳和内容
        if (newEntry.isSamePlaylist(current)) {
            _currentPlaylist.value = newEntry
            saveCurrentToPrefs(newEntry)
            return
        }

        // 如果之前有当前歌单，将其推进为“上次播放歌单”
        if (current != null) {
            _previousPlaylist.value = current
            savePreviousToPrefs(current)
        }

        _currentPlaylist.value = newEntry
        saveCurrentToPrefs(newEntry)
    }

    /**
     * 依据播放来源与曲目信息智能记录歌单播放
     */
    fun recordPlayedByOrigin(
        origin: PlaybackOrigin?,
        currentSong: Song?,
        playlistSongs: List<Song> = emptyList()
    ) {
        if (origin == null) return
        val entry = when (origin) {
            is PlaybackOrigin.PlaylistOrigin -> {
                val p = origin.playlist
                val category = when (p.id) {
                    -999L -> PlaylistCategory.LOCAL_FAVORITE
                    -998L -> PlaylistCategory.LOCAL_DISLIKED
                    else -> PlaylistCategory.LOCAL_CUSTOM
                }
                PlayedPlaylistEntry(
                    id = p.id.toString(),
                    title = p.name,
                    category = category,
                    coverUrl = playlistSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri ?: currentSong?.albumArtUri,
                    songCount = if (playlistSongs.isNotEmpty()) playlistSongs.size else p.songCount,
                    lastPlayedSongTitle = currentSong?.title,
                    lastPlayedSongArtist = currentSong?.artist,
                    playedTimestamp = System.currentTimeMillis()
                )
            }
            is PlaybackOrigin.OnlinePlaylistOrigin -> {
                val op = origin.onlinePlaylist
                PlayedPlaylistEntry(
                    id = op.id,
                    title = op.title,
                    category = PlaylistCategory.ONLINE,
                    platform = op.platform,
                    coverUrl = op.coverUrl,
                    songCount = if (playlistSongs.isNotEmpty()) playlistSongs.size else op.trackCount,
                    creatorName = op.creatorName,
                    lastPlayedSongTitle = currentSong?.title,
                    lastPlayedSongArtist = currentSong?.artist,
                    playedTimestamp = System.currentTimeMillis()
                )
            }
            is PlaybackOrigin.OnlineAlbumOrigin -> {
                val album = origin.onlineAlbum
                PlayedPlaylistEntry(
                    id = album.id,
                    title = album.title,
                    category = PlaylistCategory.ONLINE,
                    platform = album.platform,
                    coverUrl = album.coverUrl,
                    songCount = if (playlistSongs.isNotEmpty()) playlistSongs.size else album.songCount,
                    creatorName = album.artist,
                    lastPlayedSongTitle = currentSong?.title,
                    lastPlayedSongArtist = currentSong?.artist,
                    playedTimestamp = System.currentTimeMillis()
                )
            }
            is PlaybackOrigin.OnlineArtistOrigin -> {
                val artist = origin.onlineArtist
                PlayedPlaylistEntry(
                    id = "artist_${artist.id}",
                    title = "${artist.name} 的精选歌曲",
                    category = PlaylistCategory.ONLINE,
                    platform = artist.platform,
                    coverUrl = artist.avatarUrl,
                    songCount = playlistSongs.size,
                    creatorName = artist.name,
                    lastPlayedSongTitle = currentSong?.title,
                    lastPlayedSongArtist = currentSong?.artist,
                    playedTimestamp = System.currentTimeMillis()
                )
            }
            is PlaybackOrigin.OnlineSquareOrigin -> {
                val p = origin.platform
                PlayedPlaylistEntry(
                    id = "square_${p.name}_${origin.tabIndex}",
                    title = "${p.displayName} 在线精选",
                    category = PlaylistCategory.ONLINE,
                    platform = p,
                    coverUrl = playlistSongs.firstOrNull { !it.albumArtUri.isNullOrBlank() }?.albumArtUri ?: currentSong?.albumArtUri,
                    songCount = playlistSongs.size,
                    creatorName = p.displayName,
                    lastPlayedSongTitle = currentSong?.title,
                    lastPlayedSongArtist = currentSong?.artist,
                    playedTimestamp = System.currentTimeMillis()
                )
            }
            else -> null
        }

        if (entry != null) {
            recordPlayed(entry)
        } else {
            // 如果用户播放了非歌单来源（全部歌曲、专辑、艺术家、文件夹等）
            // 原当前歌单沉淀为“上次播放歌单”，当前歌单置空
            val current = _currentPlaylist.value
            if (current != null) {
                _previousPlaylist.value = current
                savePreviousToPrefs(current)
                _currentPlaylist.value = null
                saveCurrentToPrefs(null)
            }
        }
    }

    /**
     * 更新当前播放歌单中的最新播放歌曲（严格匹配播放来源）
     */
    fun updateCurrentSong(song: Song?, origin: PlaybackOrigin?) {
        val cur = _currentPlaylist.value ?: return
        if (song == null) return
        if (!cur.isMatchingOrigin(origin)) return

        val updated = cur.copy(
            lastPlayedSongTitle = song.title,
            lastPlayedSongArtist = song.artist,
            playedTimestamp = System.currentTimeMillis()
        )
        _currentPlaylist.value = updated
        saveCurrentToPrefs(updated)
    }

    private fun saveCurrentToPrefs(entry: PlayedPlaylistEntry?) {
        if (entry != null) {
            prefs.edit().putString(KEY_CURRENT_PLAYLIST_JSON, serializeEntry(entry).toString()).apply()
        } else {
            prefs.edit().remove(KEY_CURRENT_PLAYLIST_JSON).apply()
        }
    }

    private fun savePreviousToPrefs(entry: PlayedPlaylistEntry?) {
        if (entry != null) {
            prefs.edit().putString(KEY_PREVIOUS_PLAYLIST_JSON, serializeEntry(entry).toString()).apply()
        } else {
            prefs.edit().remove(KEY_PREVIOUS_PLAYLIST_JSON).apply()
        }
    }

    private fun serializeEntry(entry: PlayedPlaylistEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("title", entry.title)
            put("category", entry.category.name)
            put("platform", entry.platform?.name ?: "")
            put("coverUrl", entry.coverUrl ?: "")
            put("songCount", entry.songCount)
            put("creatorName", entry.creatorName ?: "")
            put("lastPlayedSongTitle", entry.lastPlayedSongTitle ?: "")
            put("lastPlayedSongArtist", entry.lastPlayedSongArtist ?: "")
            put("playedTimestamp", entry.playedTimestamp)
        }
    }

    private fun parseEntry(jsonStr: String): PlayedPlaylistEntry? {
        return try {
            val obj = JSONObject(jsonStr)
            val categoryStr = obj.optString("category", PlaylistCategory.LOCAL_CUSTOM.name)
            val category = try {
                PlaylistCategory.valueOf(categoryStr)
            } catch (_: Exception) {
                PlaylistCategory.LOCAL_CUSTOM
            }

            val platformStr = obj.optString("platform", "")
            val platform = if (platformStr.isNotBlank()) {
                try {
                    OnlinePlatform.valueOf(platformStr)
                } catch (_: Exception) {
                    null
                }
            } else null

            PlayedPlaylistEntry(
                id = obj.optString("id"),
                title = obj.optString("title"),
                category = category,
                platform = platform,
                coverUrl = obj.optString("coverUrl").takeIf { it.isNotBlank() },
                songCount = obj.optInt("songCount", 0),
                creatorName = obj.optString("creatorName").takeIf { it.isNotBlank() },
                lastPlayedSongTitle = obj.optString("lastPlayedSongTitle").takeIf { it.isNotBlank() },
                lastPlayedSongArtist = obj.optString("lastPlayedSongArtist").takeIf { it.isNotBlank() },
                playedTimestamp = obj.optLong("playedTimestamp", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }
}
