package com.orbit.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.repository.OnlineMusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 平台筛选选项（包含全部平台聚合模式）
 */
sealed class OnlineSearchFilterPlatform {
    object ALL : OnlineSearchFilterPlatform()
    data class Single(val platform: OnlinePlatform) : OnlineSearchFilterPlatform()

    val displayName: String
        get() = when (this) {
            is ALL -> "全部平台"
            is Single -> platform.displayName
        }
}

data class OnlineSongSearchUiState(
    val titleQuery: String = "",
    val artistQuery: String = "",
    val isDualInputMode: Boolean = false, // 是否开启歌名与歌手独立双输入框
    val selectedPlatformFilter: OnlineSearchFilterPlatform = OnlineSearchFilterPlatform.ALL,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
    val searchResults: List<OnlineSongItem> = emptyList(),
    val platformResultCounts: Map<OnlinePlatform, Int> = emptyMap(),
    val errorMessage: String? = null,
    val searchHistory: List<String> = emptyList()
)

class OnlineSongSearchViewModel(
    private val repository: OnlineMusicRepository = OnlineMusicRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlineSongSearchUiState())
    val uiState: StateFlow<OnlineSongSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var loadMoreJob: Job? = null

    // 缓存各平台的完整搜索歌曲列表
    private val cachedPlatformResults = mutableMapOf<OnlinePlatform, MutableList<OnlineSongItem>>()
    // 各平台当前加载到的页码
    private val platformPages = mutableMapOf<OnlinePlatform, Int>()
    // 各平台是否还有更多数据
    private val platformHasMoreMap = mutableMapOf<OnlinePlatform, Boolean>()

    fun setTitleQuery(query: String) {
        _uiState.update { it.copy(titleQuery = query) }
    }

    fun setArtistQuery(query: String) {
        _uiState.update { it.copy(artistQuery = query) }
    }

    fun toggleDualInputMode() {
        _uiState.update { it.copy(isDualInputMode = !it.isDualInputMode) }
    }

    fun selectPlatformFilter(filter: OnlineSearchFilterPlatform) {
        _uiState.update { it.copy(selectedPlatformFilter = filter) }
        applyFilterResults(filter)
    }

    private fun getCombinedQuery(title: String, artist: String): String {
        val t = title.trim()
        val a = artist.trim()
        return when {
            t.isNotBlank() && a.isNotBlank() -> "$t $a"
            t.isNotBlank() -> t
            a.isNotBlank() -> a
            else -> ""
        }
    }

    /**
     * 执行全网多平台歌曲搜索 (首屏)
     */
    fun performSearch(
        title: String = _uiState.value.titleQuery,
        artist: String = _uiState.value.artistQuery
    ) {
        val query = getCombinedQuery(title, artist)
        if (query.isBlank()) return

        searchJob?.cancel()
        loadMoreJob?.cancel()

        val historyItem = query
        val currentHistory = _uiState.value.searchHistory.toMutableList()
        currentHistory.remove(historyItem)
        currentHistory.add(0, historyItem)
        if (currentHistory.size > 15) {
            currentHistory.removeAt(currentHistory.lastIndex)
        }

        cachedPlatformResults.clear()
        platformPages.clear()
        platformHasMoreMap.clear()

        _uiState.update {
            it.copy(
                titleQuery = title,
                artistQuery = artist,
                isSearching = true,
                isLoadingMore = false,
                currentPage = 1,
                hasMore = true,
                errorMessage = null,
                searchHistory = currentHistory
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val pageSize = 30
                // 并发搜索所有网络平台
                val resultMap = repository.searchSongsAllPlatforms(query, page = 1, pageSize = pageSize)
                val counts = mutableMapOf<OnlinePlatform, Int>()

                resultMap.forEach { (plat, res) ->
                    val songs = res.getOrDefault(emptyList()).filter { s -> s.title.isNotBlank() }
                    cachedPlatformResults[plat] = songs.toMutableList()
                    platformPages[plat] = 1
                    platformHasMoreMap[plat] = songs.size >= pageSize
                    counts[plat] = songs.size
                }

                val anyHasMore = platformHasMoreMap.values.any { it }

                _uiState.update {
                    it.copy(
                        platformResultCounts = counts,
                        isSearching = false,
                        hasMore = anyHasMore,
                        currentPage = 1
                    )
                }

                applyFilterResults(_uiState.value.selectedPlatformFilter)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "搜索出错: ${e.localizedMessage ?: "网络异常"}"
                    )
                }
            }
        }
    }

    /**
     * 载入更多歌曲
     */
    fun loadMore() {
        if (_uiState.value.isSearching || _uiState.value.isLoadingMore || !_uiState.value.hasMore) return

        val query = getCombinedQuery(_uiState.value.titleQuery, _uiState.value.artistQuery)
        if (query.isBlank()) return

        loadMoreJob?.cancel()
        _uiState.update { it.copy(isLoadingMore = true) }

        loadMoreJob = viewModelScope.launch {
            try {
                val filter = _uiState.value.selectedPlatformFilter
                val pageSize = 30
                var anyNewLoaded = false

                when (filter) {
                    is OnlineSearchFilterPlatform.ALL -> {
                        // 针对全部有更多数据的平台并发加载下一页
                        val targetPlatforms = OnlinePlatform.values().filter { platformHasMoreMap[it] != false }
                        if (targetPlatforms.isEmpty()) {
                            _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                            return@launch
                        }

                        targetPlatforms.forEach { plat ->
                            val nextPage = (platformPages[plat] ?: 1) + 1
                            val res = repository.searchSongs(query, page = nextPage, pageSize = pageSize, platform = plat)
                            val newSongs = res.getOrDefault(emptyList()).filter { s -> s.title.isNotBlank() }

                            val list = cachedPlatformResults.getOrPut(plat) { mutableListOf() }
                            val existingIds = list.map { it.id }.toSet()
                            val deduplicated = newSongs.filter { !existingIds.contains(it.id) }

                            if (deduplicated.isNotEmpty()) {
                                list.addAll(deduplicated)
                                anyNewLoaded = true
                            }
                            platformPages[plat] = nextPage
                            platformHasMoreMap[plat] = newSongs.size >= pageSize
                        }
                    }
                    is OnlineSearchFilterPlatform.Single -> {
                        val plat = filter.platform
                        if (platformHasMoreMap[plat] == false) {
                            _uiState.update { it.copy(isLoadingMore = false, hasMore = false) }
                            return@launch
                        }
                        val nextPage = (platformPages[plat] ?: 1) + 1
                        val res = repository.searchSongs(query, page = nextPage, pageSize = pageSize, platform = plat)
                        val newSongs = res.getOrDefault(emptyList()).filter { s -> s.title.isNotBlank() }

                        val list = cachedPlatformResults.getOrPut(plat) { mutableListOf() }
                        val existingIds = list.map { it.id }.toSet()
                        val deduplicated = newSongs.filter { !existingIds.contains(it.id) }

                        if (deduplicated.isNotEmpty()) {
                            list.addAll(deduplicated)
                            anyNewLoaded = true
                        }
                        platformPages[plat] = nextPage
                        platformHasMoreMap[plat] = newSongs.size >= pageSize
                    }
                }

                val counts = mutableMapOf<OnlinePlatform, Int>()
                cachedPlatformResults.forEach { (p, l) -> counts[p] = l.size }

                val currentHasMore = when (filter) {
                    is OnlineSearchFilterPlatform.ALL -> platformHasMoreMap.values.any { it }
                    is OnlineSearchFilterPlatform.Single -> platformHasMoreMap[filter.platform] ?: false
                }

                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        hasMore = currentHasMore,
                        currentPage = it.currentPage + 1,
                        platformResultCounts = counts
                    )
                }

                applyFilterResults(_uiState.value.selectedPlatformFilter)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        errorMessage = "加载更多失败: ${e.localizedMessage ?: "网络异常"}"
                    )
                }
            }
        }
    }

    private fun applyFilterResults(filter: OnlineSearchFilterPlatform) {
        when (filter) {
            is OnlineSearchFilterPlatform.ALL -> {
                // 聚合全平台结果，交错排列 Round-robin
                val platformLists = cachedPlatformResults.values.toList()
                val aggregated = mutableListOf<OnlineSongItem>()
                var maxLen = 0
                for (l in platformLists) {
                    if (l.size > maxLen) maxLen = l.size
                }
                for (i in 0 until maxLen) {
                    for (l in platformLists) {
                        if (i < l.size) {
                            val item = l[i]
                            if (aggregated.none { it.id == item.id && it.platform == item.platform }) {
                                aggregated.add(item)
                            }
                        }
                    }
                }
                val hasMore = platformHasMoreMap.values.any { it }
                _uiState.update { it.copy(searchResults = aggregated, hasMore = hasMore) }
            }
            is OnlineSearchFilterPlatform.Single -> {
                val list = cachedPlatformResults[filter.platform] ?: emptyList()
                val hasMore = platformHasMoreMap[filter.platform] ?: false
                _uiState.update { it.copy(searchResults = list, hasMore = hasMore) }
            }
        }
    }

    fun clearHistory() {
        _uiState.update { it.copy(searchHistory = emptyList()) }
    }

    fun clearSearch() {
        searchJob?.cancel()
        loadMoreJob?.cancel()
        cachedPlatformResults.clear()
        platformPages.clear()
        platformHasMoreMap.clear()
        _uiState.update {
            it.copy(
                titleQuery = "",
                artistQuery = "",
                searchResults = emptyList(),
                platformResultCounts = emptyMap(),
                isSearching = false,
                isLoadingMore = false,
                hasMore = true,
                currentPage = 1,
                errorMessage = null
            )
        }
    }
}
