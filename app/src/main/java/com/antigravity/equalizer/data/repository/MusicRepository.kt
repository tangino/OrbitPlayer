package com.antigravity.equalizer.data.repository

import android.content.Context
import com.antigravity.equalizer.audio.MusicPlayerManager
import com.antigravity.equalizer.data.db.AppDatabase
import com.antigravity.equalizer.data.model.AlbumItem
import com.antigravity.equalizer.data.model.ArtistItem
import com.antigravity.equalizer.data.model.FolderItem
import com.antigravity.equalizer.data.model.Playlist
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.model.SongAttitude
import com.antigravity.equalizer.data.scanner.MediaStoreScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MusicRepository private constructor(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val scanner = MediaStoreScanner(context)
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    private val scanPrefs = context.getSharedPreferences(PREFS_SCAN, Context.MODE_PRIVATE)

    private val _includedFolders = MutableStateFlow<Set<String>>(
        scanPrefs.getStringSet(KEY_INCLUDED_FOLDERS, emptySet()) ?: emptySet()
    )
    val includedFolders: StateFlow<Set<String>> = _includedFolders.asStateFlow()

    private val _excludedFolders = MutableStateFlow<Set<String>>(
        scanPrefs.getStringSet(KEY_EXCLUDED_FOLDERS, emptySet()) ?: emptySet()
    )
    val excludedFolders: StateFlow<Set<String>> = _excludedFolders.asStateFlow()

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    private val _folders = MutableStateFlow<List<FolderItem>>(emptyList())
    val folders: StateFlow<List<FolderItem>> = _folders.asStateFlow()

    private val _albums = MutableStateFlow<List<AlbumItem>>(emptyList())
    val albums: StateFlow<List<AlbumItem>> = _albums.asStateFlow()

    private val _artists = MutableStateFlow<List<ArtistItem>>(emptyList())
    val artists: StateFlow<List<ArtistItem>> = _artists.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadFromDatabase()
    }

    private fun loadFromDatabase() {
        repositoryScope.launch {
            val songs = db.songDao.getAllSongs()
            if (songs.isNotEmpty()) {
                updateCollections(songs)
            } else {
                // 首次若数据库为空，自动扫描
                refreshMedia()
            }
            refreshPlaylists()
        }
    }

    suspend fun refreshMedia() = withContext(Dispatchers.IO) {
        _isScanning.value = true
        try {
            val scannedSongs = scanner.scanLocalMedia(
                includedFolders = _includedFolders.value,
                excludedFolders = _excludedFolders.value
            )
            updateCollections(scannedSongs)
        } finally {
            _isScanning.value = false
        }
    }

    fun addIncludedFolder(folderPath: String) {
        val current = _includedFolders.value.toMutableSet()
        if (current.add(folderPath.trim())) {
            _includedFolders.value = current
            scanPrefs.edit().putStringSet(KEY_INCLUDED_FOLDERS, current).apply()
        }
    }

    fun removeIncludedFolder(folderPath: String) {
        val current = _includedFolders.value.toMutableSet()
        if (current.remove(folderPath.trim())) {
            _includedFolders.value = current
            scanPrefs.edit().putStringSet(KEY_INCLUDED_FOLDERS, current).apply()
        }
    }

    fun addExcludedFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        if (current.add(folderPath.trim())) {
            _excludedFolders.value = current
            scanPrefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply()
        }
    }

    fun removeExcludedFolder(folderPath: String) {
        val current = _excludedFolders.value.toMutableSet()
        if (current.remove(folderPath.trim())) {
            _excludedFolders.value = current
            scanPrefs.edit().putStringSet(KEY_EXCLUDED_FOLDERS, current).apply()
        }
    }

    suspend fun cycleSongAttitude(song: Song): SongAttitude = withContext(Dispatchers.IO) {
        val nextAttitude = when (song.attitude) {
            SongAttitude.NONE -> SongAttitude.FAVORITE
            SongAttitude.FAVORITE -> SongAttitude.DISLIKED
            SongAttitude.DISLIKED -> SongAttitude.NONE
        }
        val isFav = nextAttitude == SongAttitude.FAVORITE
        val isDisliked = nextAttitude == SongAttitude.DISLIKED

        db.songDao.updateSongAttitude(song.path, isFavorite = isFav, isDisliked = isDisliked)
        val updatedList = _allSongs.value.map {
            if (it.path == song.path) it.copy(isFavorite = isFav, isDisliked = isDisliked) else it
        }
        updateCollections(updatedList)
        MusicPlayerManager.getInstance(context).updateSongAttitude(song.path, isFav, isDisliked)
        nextAttitude
    }

    suspend fun toggleFavorite(song: Song) = withContext(Dispatchers.IO) {
        val newFavorite = !song.isFavorite
        db.songDao.toggleFavorite(song.path, newFavorite)
        val updatedList = _allSongs.value.map {
            if (it.path == song.path) it.copy(isFavorite = newFavorite, isDisliked = false) else it
        }
        updateCollections(updatedList)
        MusicPlayerManager.getInstance(context).updateSongFavorite(song.path, newFavorite)
    }

    suspend fun recordSongPlay(song: Song) = withContext(Dispatchers.IO) {
        db.songDao.incrementPlayCount(song.path)
        val updatedList = _allSongs.value.map {
            if (it.path == song.path) it.copy(playCount = it.playCount + 1) else it
        }
        updateCollections(updatedList)
    }

    suspend fun getSongMetadata(song: Song): com.antigravity.equalizer.data.model.SongMetadata = withContext(Dispatchers.IO) {
        val fromDb = db.songDao.getSongMetadata(song.path)
        com.antigravity.equalizer.data.model.SongMetadataHelper.extractInitialMetadata(song, fromDb)
    }

    suspend fun saveFullSongMetadata(
        song: Song,
        metadata: com.antigravity.equalizer.data.model.SongMetadata
    ) = withContext(Dispatchers.IO) {
        db.songDao.saveSongMetadata(song.path, song.id, metadata)
        val yearInt = metadata.year.toIntOrNull() ?: song.year
        val updatedSong = song.copy(
            title = metadata.title.ifBlank { song.title },
            artist = metadata.artist.ifBlank { song.artist },
            album = metadata.album.ifBlank { song.album },
            year = yearInt
        )
        val updatedList = _allSongs.value.map {
            if (it.path == song.path || it.id == song.id) updatedSong else it
        }
        updateCollections(updatedList)
        MusicPlayerManager.getInstance(context).updateSongMetadata(updatedSong)
    }

    suspend fun updateSongMetadata(
        song: Song,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: Int = 0
    ) = withContext(Dispatchers.IO) {
        db.songDao.updateSongMetadata(song.id, newTitle, newArtist, newAlbum, newYear)
        val updatedSong = song.copy(
            title = newTitle,
            artist = newArtist,
            album = newAlbum,
            year = if (newYear > 0) newYear else song.year
        )
        val updatedList = _allSongs.value.map {
            if (it.id == song.id) updatedSong else it
        }
        updateCollections(updatedList)
        MusicPlayerManager.getInstance(context).updateSongMetadata(updatedSong)
    }

    private fun updateCollections(songs: List<Song>) {
        _allSongs.value = songs
        _favoriteSongs.value = songs.filter { it.isFavorite && !it.isDisliked }

        // 1. 构建文件夹树 (Folders)
        val folderMap = songs.groupBy { it.folderPath }
        _folders.value = folderMap.map { (path, songList) ->
            FolderItem(
                folderPath = path,
                folderName = try { File(path).name.ifEmpty { "Root" } } catch (e: Exception) { "Folder" },
                songCount = songList.size
            )
        }.sortedBy { it.folderName }

        // 2. 构建专辑 (Albums)
        val albumMap = songs.groupBy { it.album }
        _albums.value = albumMap.map { (albumTitle, songList) ->
            val firstSong = songList.first()
            val albumId = if (firstSong.albumId > 0) {
                firstSong.albumId
            } else {
                (albumTitle.hashCode().toLong() xor (firstSong.artist.hashCode().toLong() shl 16)) and 0x7FFFFFFFFFFFFFFFL
            }
            AlbumItem(
                id = albumId,
                title = albumTitle,
                artist = firstSong.artist,
                albumArtUri = firstSong.albumArtUri,
                songCount = songList.size
            )
        }.sortedBy { it.title }

        // 3. 构建艺术家 (Artists)
        val artistMap = songs.groupBy { it.artist }
        _artists.value = artistMap.map { (artistName, songList) ->
            ArtistItem(
                name = artistName,
                songCount = songList.size,
                albumCount = songList.map { it.album }.distinct().size
            )
        }.sortedBy { it.name }
    }

    suspend fun refreshPlaylists() = withContext(Dispatchers.IO) {
        _playlists.value = db.songDao.getAllPlaylists()
    }

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        val id = db.songDao.insertPlaylist(name)
        refreshPlaylists()
        id
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) = withContext(Dispatchers.IO) {
        db.songDao.updatePlaylistName(playlistId, newName)
        refreshPlaylists()
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        db.songDao.deletePlaylist(playlistId)
        refreshPlaylists()
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        db.songDao.insertSongToPlaylist(playlistId, songId, 0)
        refreshPlaylists()
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = withContext(Dispatchers.IO) {
        db.songDao.removeSongFromPlaylist(playlistId, songId)
        refreshPlaylists()
    }

    suspend fun getSongsInPlaylist(playlistId: Long): List<Song> = withContext(Dispatchers.IO) {
        db.songDao.getSongsInPlaylist(playlistId)
    }

    suspend fun getSongsInFolder(folderPath: String): List<Song> = withContext(Dispatchers.IO) {
        _allSongs.value.filter { it.folderPath == folderPath }
    }

    suspend fun getSongsInAlbum(albumName: String): List<Song> = withContext(Dispatchers.IO) {
        _allSongs.value.filter { it.album == albumName }
    }

    suspend fun getSongsByArtist(artistName: String): List<Song> = withContext(Dispatchers.IO) {
        _allSongs.value.filter { it.artist == artistName }
    }

    companion object {
        const val PREFS_SCAN = "music_scan_prefs"
        const val KEY_INCLUDED_FOLDERS = "scan_included_folders"
        const val KEY_EXCLUDED_FOLDERS = "scan_excluded_folders"

        @Volatile
        private var INSTANCE: MusicRepository? = null

        fun getInstance(context: Context): MusicRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = MusicRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
