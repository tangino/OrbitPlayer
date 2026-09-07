package com.antigravity.equalizer.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.antigravity.equalizer.audio.MusicPlayerManager
import com.antigravity.equalizer.audio.PlaybackState
import com.antigravity.equalizer.audio.ShuffleStrategy
import com.antigravity.equalizer.data.model.AlbumItem
import com.antigravity.equalizer.data.model.ArtistItem
import com.antigravity.equalizer.data.model.FolderItem
import com.antigravity.equalizer.data.model.Playlist
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.model.SongAttitude
import com.antigravity.equalizer.data.repository.MusicRepository
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
    GRID_2_COL        // 6. 2列大图网格
}

data class LibraryUiState(
    val currentTab: LibraryTab = LibraryTab.SONGS,
    val viewMode: LibraryViewMode = LibraryViewMode.LIST_SMALL_ART,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val selectedFolder: FolderItem? = null,
    val selectedAlbum: AlbumItem? = null,
    val selectedArtist: ArtistItem? = null,
    val selectedPlaylist: Playlist? = null,
    val isNowPlayingExpanded: Boolean = false
)

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
    val folders: StateFlow<List<FolderItem>> = repository.folders
    val albums: StateFlow<List<AlbumItem>> = repository.albums
    val artists: StateFlow<List<ArtistItem>> = repository.artists
    val playlists: StateFlow<List<Playlist>> = repository.playlists
    val includedFolders: StateFlow<Set<String>> = repository.includedFolders
    val excludedFolders: StateFlow<Set<String>> = repository.excludedFolders

    // 搜索过滤后的歌曲列表
    val filteredSongs: StateFlow<List<Song>> = combine(allSongs, _libraryUiState) { songs, state ->
        if (state.searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(state.searchQuery, ignoreCase = true) ||
                it.artist.contains(state.searchQuery, ignoreCase = true) ||
                it.album.contains(state.searchQuery, ignoreCase = true)
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
        val viewModeName = prefs.getString(KEY_VIEW_MODE, LibraryViewMode.LIST_SMALL_ART.name) ?: LibraryViewMode.LIST_SMALL_ART.name

        val restoredTab = try {
            LibraryTab.valueOf(tabName)
        } catch (e: Exception) {
            LibraryTab.SONGS
        }

        val restoredViewMode = try {
            LibraryViewMode.valueOf(viewModeName)
        } catch (e: Exception) {
            LibraryViewMode.LIST_SMALL_ART
        }

        _libraryUiState.update {
            it.copy(
                currentTab = restoredTab,
                viewMode = restoredViewMode
            )
        }
    }

    private fun saveLibraryUiState() {
        val s = _libraryUiState.value
        prefs.edit()
            .putString(KEY_TAB, s.currentTab.name)
            .putString(KEY_VIEW_MODE, s.viewMode.name)
            .apply()
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

    fun setViewMode(viewMode: LibraryViewMode) {
        _libraryUiState.update { it.copy(viewMode = viewMode) }
        saveLibraryUiState()
    }

    fun cycleViewMode() {
        val next = when (_libraryUiState.value.viewMode) {
            LibraryViewMode.LIST_NO_ART -> LibraryViewMode.LIST_SMALL_ART
            LibraryViewMode.LIST_SMALL_ART -> LibraryViewMode.LIST_LARGE_ART
            LibraryViewMode.LIST_LARGE_ART -> LibraryViewMode.GRID_2_COL
            LibraryViewMode.GRID_2_COL -> LibraryViewMode.GRID_3_COL
            LibraryViewMode.GRID_3_COL -> LibraryViewMode.GRID_4_COL
            LibraryViewMode.GRID_4_COL -> LibraryViewMode.LIST_NO_ART
        }
        setViewMode(next)
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

    fun selectArtist(artist: ArtistItem?) {
        _libraryUiState.update { it.copy(selectedArtist = artist) }
    }

    fun selectPlaylist(playlist: Playlist?) {
        _libraryUiState.update { it.copy(selectedPlaylist = playlist) }
    }

    fun setNowPlayingExpanded(expanded: Boolean) {
        _libraryUiState.update { it.copy(isNowPlayingExpanded = expanded) }
    }

    fun scanMedia() {
        viewModelScope.launch {
            repository.refreshMedia()
        }
    }

    fun playSong(songs: List<Song>, index: Int) {
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

    companion object {
        private const val PREFS_NAME = "music_library_ui_prefs"
        private const val KEY_TAB = "key_library_tab"
        private const val KEY_VIEW_MODE = "key_library_view_mode"
    }
}
