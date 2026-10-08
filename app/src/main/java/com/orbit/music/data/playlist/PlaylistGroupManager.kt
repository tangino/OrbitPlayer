package com.orbit.music.data.playlist

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * 歌单自定义分组管理器 (用于车机、手机多设备场景歌单管理)
 */
class PlaylistGroupManager private constructor(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "playlist_groups_prefs"
        private const val KEY_CUSTOM_GROUPS = "custom_groups_json"
        
        const val DEFAULT_GROUP = "默认"
        const val CAR_GROUP = "🚗 车载专用"
        const val PHONE_GROUP = "📱 手机精选"

        @Volatile
        private var instance: PlaylistGroupManager? = null

        fun getInstance(context: Context): PlaylistGroupManager {
            return instance ?: synchronized(this) {
                instance ?: PlaylistGroupManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _groups = MutableStateFlow<List<String>>(emptyList())
    val groups: StateFlow<List<String>> = _groups.asStateFlow()

    init {
        loadGroups()
    }

    private fun loadGroups() {
        val jsonStr = prefs.getString(KEY_CUSTOM_GROUPS, null)
        if (jsonStr.isNullOrBlank()) {
            // 默认推荐场景分组
            val defaultList = listOf(CAR_GROUP, PHONE_GROUP, DEFAULT_GROUP, "长途自驾", "睡前轻音乐")
            _groups.value = defaultList
            saveGroups(defaultList)
        } else {
            try {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val g = array.getString(i).trim()
                    if (g.isNotBlank() && g !in list) {
                        list.add(g)
                    }
                }
                if (DEFAULT_GROUP !in list) {
                    list.add(DEFAULT_GROUP)
                }
                _groups.value = list
            } catch (_: Exception) {
                _groups.value = listOf(CAR_GROUP, PHONE_GROUP, DEFAULT_GROUP)
            }
        }
    }

    private fun saveGroups(list: List<String>) {
        try {
            val array = JSONArray()
            for (g in list) {
                array.put(g)
            }
            prefs.edit().putString(KEY_CUSTOM_GROUPS, array.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /**
     * 添加新分组
     */
    fun addGroup(name: String): Boolean {
        val clean = name.trim()
        if (clean.isBlank()) return false
        val current = _groups.value.toMutableList()
        if (clean in current) return false
        current.add(clean)
        _groups.value = current
        saveGroups(current)
        return true
    }

    /**
     * 重命名分组
     */
    fun renameGroup(oldName: String, newName: String): Boolean {
        val cleanOld = oldName.trim()
        val cleanNew = newName.trim()
        if (cleanNew.isBlank() || cleanOld == cleanNew) return false
        val current = _groups.value.toMutableList()
        val idx = current.indexOf(cleanOld)
        if (idx >= 0) {
            current[idx] = cleanNew
            _groups.value = current
            saveGroups(current)
            return true
        }
        return false
    }

    /**
     * 删除分组
     */
    fun deleteGroup(name: String): Boolean {
        val clean = name.trim()
        if (clean == DEFAULT_GROUP) return false // 默认分组不能删除
        val current = _groups.value.toMutableList()
        val removed = current.remove(clean)
        if (removed) {
            _groups.value = current
            saveGroups(current)
            return true
        }
        return false
    }
}
