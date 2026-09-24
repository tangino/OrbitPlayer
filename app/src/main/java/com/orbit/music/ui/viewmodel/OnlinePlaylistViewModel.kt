package com.orbit.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.music.data.online.model.OnlineLeaderboard
import com.orbit.music.data.online.model.OnlinePlatform
import com.orbit.music.data.online.model.OnlinePlaylist
import com.orbit.music.data.online.model.OnlinePlaylistTag
import com.orbit.music.data.online.model.OnlineSongItem
import com.orbit.music.data.online.repository.OnlineMusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 在线公共歌单 UI 状态
 */
data class OnlinePlaylistUiState(
    val currentPlatform: OnlinePlatform = OnlinePlatform.NETEASE,
    val selectedTab: Int = 0, // 0: 精选推荐, 1: 热门分类, 2: 官方榜单
    val tags: List<OnlinePlaylistTag> = emptyList(),
    val selectedTag: OnlinePlaylistTag = OnlinePlaylistTag("全部", "全部"),
    val playlists: List<OnlinePlaylist> = emptyList(),
    val leaderboards: List<OnlineLeaderboard> = emptyList(),
    val currentPage: Int = 1,
    val hasMorePlaylists: Boolean = true,
    val isLoading: Boolean = false,
    val isPagingLoading: Boolean = false,
    val errorMessage: String? = null,
    // 歌单详情相关
    val activePlaylist: OnlinePlaylist? = null,
    val activePlaylistSongs: List<OnlineSongItem> = emptyList(),
    val isLoadingDetail: Boolean = false,
    val detailErrorMessage: String? = null,
    // 搜索相关
    val searchKeyword: String = "",
    val searchResults: List<OnlinePlaylist> = emptyList(),
    val isSearching: Boolean = false,
    val isSearchMode: Boolean = false,
    // 导入解析相关
    val isImportDialogOpen: Boolean = false,
    val importLinkInput: String = "",
    val isImporting: Boolean = false,
    val importError: String? = null
)

class OnlinePlaylistViewModel(
    private val repository: OnlineMusicRepository = OnlineMusicRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnlinePlaylistUiState())
    val uiState: StateFlow<OnlinePlaylistUiState> = _uiState.asStateFlow()

    init {
        loadCurrentPlatformData()
    }

    /**
     * 切换平台 (网易云音乐 / QQ 音乐)
     */
    fun switchPlatform(platform: OnlinePlatform) {
        if (_uiState.value.currentPlatform == platform) return
        repository.switchPlatform(platform)
        _uiState.update {
            it.copy(
                currentPlatform = platform,
                currentPage = 1,
                hasMorePlaylists = true,
                selectedTag = OnlinePlaylistTag("全部", "全部"),
                playlists = emptyList(),
                leaderboards = emptyList(),
                activePlaylist = null,
                activePlaylistSongs = emptyList(),
                errorMessage = null
            )
        }
        loadCurrentPlatformData()
    }

    /**
     * 切换顶部 Tab
     */
    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
        if (tabIndex == 2 && _uiState.value.leaderboards.isEmpty()) {
            loadLeaderboards()
        }
    }

    /**
     * 加载当前平台分类与默认歌单
     */
    fun loadCurrentPlatformData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val platform = _uiState.value.currentPlatform

            // 1. 加载分类标签
            val tagsResult = repository.getTags(platform)
            val tags = tagsResult.getOrDefault(emptyList())

            // 2. 加载歌单列表
            val playlistsResult = repository.getPlaylists(
                tagId = _uiState.value.selectedTag.id,
                page = 1,
                platform = platform
            )

            playlistsResult.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        tags = tags,
                        playlists = list,
                        currentPage = 1,
                        hasMorePlaylists = list.isNotEmpty(),
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        tags = tags,
                        isLoading = false,
                        errorMessage = "加载失败: ${err.localizedMessage ?: "网络错误"}"
                    )
                }
            }
        }
    }

    /**
     * 选择分类标签
     */
    fun selectTag(tag: OnlinePlaylistTag) {
        if (_uiState.value.selectedTag.id == tag.id && _uiState.value.playlists.isNotEmpty()) return
        _uiState.update {
            it.copy(
                selectedTag = tag,
                currentPage = 1,
                hasMorePlaylists = true,
                isLoading = true,
                errorMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.getPlaylists(
                tagId = tag.id,
                page = 1,
                platform = _uiState.value.currentPlatform
            )
            result.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        playlists = list,
                        currentPage = 1,
                        hasMorePlaylists = list.isNotEmpty(),
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "加载分类歌单失败: ${err.localizedMessage ?: "网络异常"}"
                    )
                }
            }
        }
    }

    /**
     * 加载下一页歌单 (分页)
     */
    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isPagingLoading || !state.hasMorePlaylists) return

        val nextPage = state.currentPage + 1
        _uiState.update { it.copy(isPagingLoading = true) }

        viewModelScope.launch {
            val result = repository.getPlaylists(
                tagId = state.selectedTag.id,
                page = nextPage,
                platform = state.currentPlatform
            )
            result.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        playlists = it.playlists + list,
                        currentPage = nextPage,
                        hasMorePlaylists = list.size >= 15,
                        isPagingLoading = false
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isPagingLoading = false) }
            }
        }
    }

    /**
     * 加载官方排行榜
     */
    fun loadLeaderboards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = repository.getLeaderboards(_uiState.value.currentPlatform)
            result.onSuccess { boards ->
                _uiState.update {
                    it.copy(
                        leaderboards = boards,
                        isLoading = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "加载排行榜失败: ${err.localizedMessage ?: "网络异常"}"
                    )
                }
            }
        }
    }

    /**
     * 打开歌单详情
     */
    fun openPlaylistDetail(playlist: OnlinePlaylist) {
        _uiState.update {
            it.copy(
                activePlaylist = playlist,
                activePlaylistSongs = emptyList(),
                isLoadingDetail = true,
                detailErrorMessage = null
            )
        }
        viewModelScope.launch {
            val result = repository.getPlaylistDetail(
                playlistId = playlist.id,
                platform = playlist.platform
            )
            result.onSuccess { (detailPlaylist, songs) ->
                _uiState.update {
                    it.copy(
                        activePlaylist = detailPlaylist,
                        activePlaylistSongs = songs,
                        isLoadingDetail = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "加载歌单曲目失败: ${err.localizedMessage ?: "解析错误"}"
                    )
                }
            }
        }
    }

    /**
     * 关闭歌单详情页（返回歌单广场）
     */
    fun closePlaylistDetail() {
        _uiState.update {
            it.copy(
                activePlaylist = null,
                activePlaylistSongs = emptyList(),
                detailErrorMessage = null
            )
        }
    }

    /**
     * 搜索歌单
     */
    fun search(keyword: String) {
        if (keyword.isBlank()) {
            _uiState.update { it.copy(searchKeyword = "", searchResults = emptyList(), isSearchMode = false) }
            return
        }
        _uiState.update { it.copy(searchKeyword = keyword, isSearching = true, isSearchMode = true) }
        viewModelScope.launch {
            val result = repository.searchPlaylists(
                keyword = keyword,
                platform = _uiState.value.currentPlatform
            )
            result.onSuccess { list ->
                _uiState.update {
                    it.copy(searchResults = list, isSearching = false)
                }
            }.onFailure {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchKeyword = "", searchResults = emptyList(), isSearchMode = false) }
    }

    /**
     * 导入链接弹窗控制
     */
    fun openImportDialog() {
        _uiState.update { it.copy(isImportDialogOpen = true, importLinkInput = "", importError = null) }
    }

    fun closeImportDialog() {
        _uiState.update { it.copy(isImportDialogOpen = false, importLinkInput = "", importError = null) }
    }

    fun updateImportInput(input: String) {
        _uiState.update { it.copy(importLinkInput = input, importError = null) }
    }

    /**
     * 解析并打开导入的歌单链接
     */
    fun importPlaylistLink() {
        val input = _uiState.value.importLinkInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(importError = "请输入歌单链接或 ID") }
            return
        }

        val parsed = repository.parseLinkOrText(input)
        if (parsed == null) {
            _uiState.update { it.copy(importError = "未能识别出歌单 ID，请检查链接格式") }
            return
        }

        val (platform, playlistId) = parsed
        _uiState.update {
            it.copy(
                isImportDialogOpen = false,
                currentPlatform = platform,
                activePlaylist = OnlinePlaylist(
                    id = playlistId,
                    platform = platform,
                    title = "正在解析歌单...",
                    coverUrl = ""
                ),
                activePlaylistSongs = emptyList(),
                isLoadingDetail = true,
                detailErrorMessage = null
            )
        }

        viewModelScope.launch {
            val result = repository.getPlaylistDetail(playlistId, platform)
            result.onSuccess { (detail, songs) ->
                _uiState.update {
                    it.copy(
                        activePlaylist = detail,
                        activePlaylistSongs = songs,
                        isLoadingDetail = false
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoadingDetail = false,
                        detailErrorMessage = "解析导入失败: ${err.localizedMessage ?: "未找到歌单"}"
                    )
                }
            }
        }
    }
}
