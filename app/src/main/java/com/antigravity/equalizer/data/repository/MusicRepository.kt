package com.antigravity.equalizer.data.repository

import android.content.Context
import com.antigravity.equalizer.data.db.AppDatabase
import com.antigravity.equalizer.data.model.AlbumItem
import com.antigravity.equalizer.data.model.ArtistItem
import com.antigravity.equalizer.data.model.FolderItem
import com.antigravity.equalizer.data.model.Playlist
import com.antigravity.equalizer.data.model.Song
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

    private val _allSongs = MutableStateFlow<List<Song>>(emptyList())
    val allSongs: StateFlow<List<Song>> = _allSongs.asStateFlow()

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
            val scannedSongs = scanner.scanLocalMedia()
            updateCollections(scannedSongs)
        } finally {
            _isScanning.value = false
        }
    }

    private fun updateCollections(songs: List<Song>) {
        _allSongs.value = songs

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
            AlbumItem(
                id = firstSong.albumId,
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
