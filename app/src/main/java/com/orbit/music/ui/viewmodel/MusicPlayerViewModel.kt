package com.orbit.music.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orbit.music.audio.MusicPlayerManager
import com.orbit.music.audio.PlaybackState
import com.orbit.music.audio.ShuffleStrategy
import com.orbit.music.data.model.AlbumItem
import com.orbit.music.data.model.ArtistItem
import com.orbit.music.data.model.FolderItem
import com.orbit.music.data.model.Playlist
import com.orbit.music.data.model.PlaybackOrigin
import com.orbit.music.data.model.Song
import com.orbit.music.data.model.SongAttitude
import com.orbit.music.data.repository.MusicRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab {
    SONGS, FOLDERS, ALBUMS, ARTISTS, PLAYLISTS
}

enum class LibraryViewMode {
    LIST_NO_ART,      // 1. 无缩略图列表
    LIST_SMALL_ART,   // 2. 带小缩略图列表
    LIST_LARGE_ART,   // 3. 带缩略图的大列表
    GRID_4_COL,       // 4. 4列精细网格
    GRID_3_COL,       // 5. 3列标准网格
    GRID_2_COL,       // 6. 2列大图网格
    COVER_FLOW        // 7. Mac OS X 经典 3D 封面流与联动列表
}

val LibraryTab.pageKey: String
    get() = when (this) {
        LibraryTab.SONGS -> "tab_songs"
        LibraryTab.FOLDERS -> "tab_folders"
        LibraryTab.ALBUMS -> "tab_albums"
        LibraryTab.ARTISTS -> "tab_artists"
        LibraryTab.PLAYLISTS -> "tab_playlists"
    }

data class LibraryUiState(
    val currentTab: LibraryTab = LibraryTab.SONGS,
    val pageViewModes: Map<String, LibraryViewMode> = defaultPageViewModes(),
    val isCoverFlowInertiaEnabled: Boolean = true,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val selectedFolder: FolderItem? = null,
    val selectedAlbum: AlbumItem? = null,
    val selectedArtist: ArtistItem? = null,
    val selectedPlaylist: Playlist? = null,
    val isNowPlayingExpanded: Boolean = false
) {
    val viewMode: LibraryViewMode
        get() {
            val key = when {
                selectedFolder != null -> "detail_folder"
                selectedAlbum != null -> "detail_album"
                selectedArtist != null -> "detail_artist"
                selectedPlaylist != null -> "detail_playlist"
                else -> currentTab.pageKey
            }
            return pageViewModes[key] ?: defaultModeFor(key)
        }

    fun getViewModeFor(pageKey: String): LibraryViewMode {
        return pageViewModes[pageKey] ?: defaultModeFor(pageKey)
    }

    companion object {
        fun defaultPageViewModes(): Map<String, LibraryViewMode> = mapOf(
            "tab_songs" to LibraryViewMode.LIST_SMALL_ART,
            "tab_folders" to LibraryViewMode.LIST_SMALL_ART,
            "tab_albums" to LibraryViewMode.GRID_3_COL,
            "tab_artists" to LibraryViewMode.LIST_SMALL_ART,
            "tab_playlists" to LibraryViewMode.LIST_SMALL_ART,
            "detail_folder" to LibraryViewMode.LIST_SMALL_ART,
            "detail_album" to LibraryViewMode.LIST_SMALL_ART,
            "detail_artist" to LibraryViewMode.LIST_SMALL_ART,
            "detail_playlist" to LibraryViewMode.LIST_SMALL_ART
        )

        fun defaultModeFor(pageKey: String): LibraryViewMode = when (pageKey) {
            "tab_albums" -> LibraryViewMode.GRID_3_COL
            else -> LibraryViewMode.LIST_SMALL_ART
        }
    }
}

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository.getInstance(application)
    private val playerManager = MusicPlayerManager.getInstance(application)
    private val prefs: SharedPreferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _libraryUiState = MutableStateFlow(LibraryUiState())
    val libraryUiState: StateFlow<LibraryUiState> = _libraryUiState.asStateFlow()

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState
    val visualizerFlow = playerManager.visualizerManager.visualizerFlow
    val isScanning: StateFlow<Boolean> = repository.isScanning

    val allSongs: StateFlow<List<Song>> = repository.allSongs
    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
    val dislikedSongs: StateFlow<List<Song>> = repository.dislikedSongs
    val folders: StateFlow<List<FolderItem>> = repository.folders
    val albums: StateFlow<List<AlbumItem>> = repository.albums
    val artists: StateFlow<List<ArtistItem>> = repository.artists
    val playlists: StateFlow<List<Playlist>> = repository.playlists
    val includedFolders: StateFlow<Set<String>> = repository.includedFolders
    val excludedFolders: StateFlow<Set<String>> = repository.excludedFolders

    // 搜索过滤后的歌曲列表
    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, _libraryUiState) { songs, state ->
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 搜索过滤后的文件夹列表（文件夹名/路径匹配，或包含符合搜索条件的歌曲）
    val filteredFolders: StateFlow<List<FolderItem>> = combine(folders, filteredSongs, _libraryUiState) { folderList, songs, state ->
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            folderList
        } else {
            val matchingFolderPaths = songs.map { it.folderPath }.toSet()
            folderList.filter { folder ->
                folder.folderName.contains(query, ignoreCase = true) ||
                folder.folderPath.contains(query, ignoreCase = true) ||
                matchingFolderPaths.contains(folder.folderPath)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 搜索过滤后的专辑列表（专辑名/艺术家匹配，或包含符合搜索条件的歌曲）
    val filteredAlbums: StateFlow<List<AlbumItem>> = combine(albums, allSongs, _libraryUiState) { albumList, songList, state ->
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            albumList
        } else {
            val matchingSongs = songList.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
            val matchingAlbumTitles = matchingSongs.map { it.album.trim().lowercase() }.filter { it.isNotBlank() }.toSet()
            albumList.filter { album ->
                val albumTitle = album.title.trim().lowercase()
                album.title.contains(query, ignoreCase = true) ||
                album.artist.contains(query, ignoreCase = true) ||
                matchingAlbumTitles.contains(albumTitle)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 搜索过滤后的艺术家列表（艺术家名匹配，或包含符合搜索条件的歌曲）
    val filteredArtists: StateFlow<List<ArtistItem>> = combine(artists, allSongs, _libraryUiState) { artistList, songList, state ->
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            artistList
        } else {
            val matchingSongs = songList.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
            }
            val matchingArtists = matchingSongs.flatMap { song ->
                listOf(song.artist.trim().lowercase()) +
                song.artist.split('/', ',', '&', '、', ';').map { it.trim().lowercase() }
            }.filter { it.isNotBlank() }.toSet()
            artistList.filter { artist ->
                artist.name.contains(query, ignoreCase = true) ||
                matchingArtists.contains(artist.name.trim().lowercase()) ||
                matchingArtists.any { it.contains(artist.name.trim().lowercase()) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 搜索过滤后的播放列表（歌单名称匹配）
    val filteredPlaylists: StateFlow<List<Playlist>> = combine(playlists, _libraryUiState) { playlistList, state ->
        val query = state.searchQuery.trim()
        if (query.isBlank()) {
            playlistList
        } else {
            playlistList.filter { playlist ->
                playlist.name.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        restoreLibraryUiState()

        viewModelScope.launch {
            allSongs.collect { songs ->
                if (songs.isNotEmpty()) {
                    playerManager.attachFullQueueIfRestored(songs)
                }
            }
        }
    }

    private fun restoreLibraryUiState() {
        val tabName = prefs.getString(KEY_TAB, LibraryTab.SONGS.name) ?: LibraryTab.SONGS.name
        val inertiaEnabled = prefs.getBoolean(KEY_COVER_FLOW_INERTIA, true)

        val restoredTab = try {
            LibraryTab.valueOf(tabName)
        } catch (e: Exception) {
            LibraryTab.SONGS
        }

        val map = LibraryUiState.defaultPageViewModes().toMutableMap()
        for (key in map.keys) {
            val savedName = prefs.getString(KEY_VIEW_MODE_PREFIX + key, null)
            if (savedName != null) {
                try {
                    map[key] = LibraryViewMode.valueOf(savedName)
                } catch (e: Exception) {
                    // ignore
                }
            } else if (key == "tab_songs" && prefs.contains(KEY_VIEW_MODE)) {
                try {
                    map[key] = LibraryViewMode.valueOf(prefs.getString(KEY_VIEW_MODE, "") ?: "")
                } catch (e: Exception) {
                    // ignore
                }
            }
        }

        _libraryUiState.update {
            it.copy(
                currentTab = restoredTab,
                pageViewModes = map,
                isCoverFlowInertiaEnabled = inertiaEnabled
            )
        }
    }

    private fun saveLibraryUiState() {
        val s = _libraryUiState.value
        val editor = prefs.edit()
            .putString(KEY_TAB, s.currentTab.name)
            .putBoolean(KEY_COVER_FLOW_INERTIA, s.isCoverFlowInertiaEnabled)
        for ((k, v) in s.pageViewModes) {
            editor.putString(KEY_VIEW_MODE_PREFIX + k, v.name)
        }
        editor.apply()
    }

    fun setCoverFlowInertiaEnabled(enabled: Boolean) {
        _libraryUiState.update {
            it.copy(isCoverFlowInertiaEnabled = enabled)
        }
        prefs.edit().putBoolean(KEY_COVER_FLOW_INERTIA, enabled).apply()
    }

    fun setTab(tab: LibraryTab) {
        _libraryUiState.update {
            it.copy(
                currentTab = tab,
                selectedFolder = null,
                selectedAlbum = null,
                selectedArtist = null,
                selectedPlaylist = null
            )
        }
        saveLibraryUiState()
    }

    fun setViewMode(viewMode: LibraryViewMode, pageKey: String? = null) {
        val targetKey = pageKey ?: run {
            val s = _libraryUiState.value
            when {
                s.selectedFolder != null -> "detail_folder"
                s.selectedAlbum != null -> "detail_album"
                s.selectedArtist != null -> "detail_artist"
                s.selectedPlaylist != null -> "detail_playlist"
                else -> s.currentTab.pageKey
            }
        }
        _libraryUiState.update { current ->
            val updated = current.pageViewModes.toMutableMap()
            updated[targetKey] = viewMode
            current.copy(pageViewModes = updated)
        }
        prefs.edit().putString(KEY_VIEW_MODE_PREFIX + targetKey, viewMode.name).apply()
    }

    fun cycleViewMode(pageKey: String? = null) {
        val targetKey = pageKey ?: run {
            val s = _libraryUiState.value
            when {
                s.selectedFolder != null -> "detail_folder"
                s.selectedAlbum != null -> "detail_album"
                s.selectedArtist != null -> "detail_artist"
                s.selectedPlaylist != null -> "detail_playlist"
                else -> s.currentTab.pageKey
            }
        }
        val currentMode = _libraryUiState.value.getViewModeFor(targetKey)
        val next = when (currentMode) {
            LibraryViewMode.LIST_NO_ART -> LibraryViewMode.LIST_SMALL_ART
            LibraryViewMode.LIST_SMALL_ART -> LibraryViewMode.LIST_LARGE_ART
            LibraryViewMode.LIST_LARGE_ART -> LibraryViewMode.GRID_2_COL
            LibraryViewMode.GRID_2_COL -> LibraryViewMode.GRID_3_COL
            LibraryViewMode.GRID_3_COL -> LibraryViewMode.GRID_4_COL
            LibraryViewMode.GRID_4_COL -> LibraryViewMode.COVER_FLOW
            LibraryViewMode.COVER_FLOW -> LibraryViewMode.LIST_NO_ART
        }
        setViewMode(next, targetKey)
    }

    fun setSearchQuery(query: String) {
        _libraryUiState.update { it.copy(searchQuery = query) }
    }

    fun toggleSearch() {
        _libraryUiState.update {
            val nextState = !it.isSearching
            it.copy(
                isSearching = nextState,
                searchQuery = if (!nextState) "" else it.searchQuery
            )
        }
    }

    fun selectFolder(folder: FolderItem?) {
        _libraryUiState.update { it.copy(selectedFolder = folder) }
    }

    fun selectAlbum(album: AlbumItem?) {
        _libraryUiState.update { it.copy(selectedAlbum = album) }
    }

    fun openAlbum(song: Song) {
        val albumTitle = song.album.trim().ifEmpty { "Unknown Album" }
        val allAlbumsList = albums.value
        val targetAlbum = allAlbumsList.find { it.title.equals(albumTitle, ignoreCase = true) }
            ?: AlbumItem(
                id = song.albumId,
                title = albumTitle,
                artist = song.artist,
                songCount = 1,
                albumArtUri = song.albumArtUri
            )
        _libraryUiState.update {
            it.copy(
                currentTab = LibraryTab.ALBUMS,
                selectedFolder = null,
                selectedAlbum = targetAlbum,
                selectedArtist = null,
                selectedPlaylist = null,
                searchQuery = "",
                isSearching = false,
                isNowPlayingExpanded = false
            )
        }
    }

    fun selectArtist(artist: ArtistItem?) {
        _libraryUiState.update { it.copy(selectedArtist = artist) }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _libraryUiState.update { it.copy(selectedPlaylist = playlist) }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _libraryUiState.update { it.copy(isNowPlayingExpanded = expanded) }
    }

    fun addSongsToQueueNext(songs: List<Song>) {
        playerManager.addSongsToQueueNext(songs)
    }

    fun addSongsToPlaylist(
        playlistId: Long,
        songIds: Collection<Long>,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.addSongsToPlaylist(playlistId, songIds)
            onComplete?.invoke()
        }
    }

    fun setFavoriteBatch(songs: List<Song>, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavoriteBatch(songs, isFavorite)
        }
    }

    fun deleteSongs(
        songs: List<Song>,
        deleteLocalFiles: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = repository.deleteSongs(songs, deleteLocalFiles)
            onComplete?.invoke(success)
        }
    }

    fun scanMedia() {
        viewModelScope.launch {
            repository.refreshMedia()
        }
    }

    fun clearAllPlayCounts() {
        viewModelScope.launch {
            repository.clearAllPlayCounts()
        }
    }

    private val _playbackOrigin = MutableStateFlow<PlaybackOrigin>(PlaybackOrigin.AllSongs)
    val playbackOrigin: StateFlow<PlaybackOrigin> = _playbackOrigin.asStateFlow()

    fun setPlaybackOrigin(origin: PlaybackOrigin) {
        _playbackOrigin.value = origin
    }

    fun playSong(songs: List<Song>, index: Int, origin: PlaybackOrigin? = null) {
        if (origin != null) {
            _playbackOrigin.value = origin
        }
        playerManager.playSongList(songs, index)
    }

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun playNext() = playerManager.playNext()
    fun playPrevious() = playerManager.playPrevious()
    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)
    fun toggleShuffle(): Int = playerManager.toggleShuffle()
    fun toggleRepeatMode() = playerManager.toggleRepeatMode()

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    suspend fun getSongsInPlaylist(playlistId: Long): List<Song> {
        return repository.getSongsInPlaylist(playlistId)
    }

    fun cycleSongAttitude(song: Song, onResult: ((SongAttitude) -> Unit)? = null) {
        viewModelScope.launch {
            val next = repository.cycleSongAttitude(song)
            onResult?.invoke(next)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            repository.toggleFavorite(song)
        }
    }

    fun removeDislike(song: Song) {
        viewModelScope.launch {
            repository.removeDislike(song)
        }
    }

    fun clearAllDislikes() {
        viewModelScope.launch {
            repository.clearAllDislikes()
        }
    }

    fun setShuffleStrategy(strategy: ShuffleStrategy) {
        playerManager.setShuffleStrategy(strategy)
    }

    fun addIncludedFolder(folderPath: String) {
        repository.addIncludedFolder(folderPath)
    }

    fun removeIncludedFolder(folderPath: String) {
        repository.removeIncludedFolder(folderPath)
    }

    fun addExcludedFolder(folderPath: String) {
        repository.addExcludedFolder(folderPath)
    }

    fun removeExcludedFolder(folderPath: String) {
        repository.removeExcludedFolder(folderPath)
    }

    fun updateSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: Int = 0
    ) {
        viewModelScope.launch {
            repository.updateSongMetadata(song, newTitle, newArtist, newAlbum, newYear)
        }
    }

    suspend fun loadSongMetadata(song: Song): com.orbit.music.data.model.SongMetadata {
        return repository.getSongMetadata(song)
    }

    fun saveFullSongMetadata(
        song: Song,
        metadata: com.orbit.music.data.model.SongMetadata,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            repository.saveFullSongMetadata(song, metadata)
            onComplete?.invoke()
        }
    }

    fun deleteSong(
        song: Song,
        deleteLocalFile: Boolean,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val success = repository.deleteSong(song, deleteLocalFile)
            onComplete?.invoke(success)
        }
    }

    companion object {
        private const val PREFS_NAME = "music_library_ui_prefs"
        private const val KEY_TAB = "key_library_tab"
        private const val KEY_VIEW_MODE = "key_library_view_mode"
        private const val KEY_VIEW_MODE_PREFIX = "key_view_mode_"
        const val KEY_COVER_FLOW_INERTIA = "key_cover_flow_inertia"
    }
}
