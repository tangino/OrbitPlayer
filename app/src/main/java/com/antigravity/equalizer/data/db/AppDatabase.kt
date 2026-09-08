package com.antigravity.equalizer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.antigravity.equalizer.data.model.Playlist
import com.antigravity.equalizer.data.model.Song
import com.antigravity.equalizer.data.model.SongMetadata

data class PresetEntity(
    val id: String,
    val name: String,
    val isCustom: Boolean,
    val preampGainDb: Float,
    val bands10Gain: String,
    val limiterEnabled: Boolean,
    val limiterThresholdDb: Float
)

data class DeviceProfileEntity(
    val deviceName: String,
    val deviceType: String,
    val presetId: String,
    val enabled: Boolean
)

data class AppProfileEntity(
    val packageName: String,
    val appName: String,
    val presetId: String,
    val enabled: Boolean
)

class AppDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "equalizer_music_v2.db", null, 5) {

    val equalizerDao = EqualizerDaoImpl(this)
    val songDao = SongDaoImpl(this)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS presets (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                isCustom INTEGER NOT NULL,
                preampGainDb REAL NOT NULL,
                bands10Gain TEXT NOT NULL,
                limiterEnabled INTEGER NOT NULL,
                limiterThresholdDb REAL NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS device_profiles (
                deviceName TEXT PRIMARY KEY,
                deviceType TEXT NOT NULL,
                presetId TEXT NOT NULL,
                enabled INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS app_profiles (
                packageName TEXT PRIMARY KEY,
                appName TEXT NOT NULL,
                presetId TEXT NOT NULL,
                enabled INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS songs (
                id INTEGER PRIMARY KEY,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                albumId INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                path TEXT NOT NULL,
                size INTEGER NOT NULL,
                albumArtUri TEXT,
                folderPath TEXT NOT NULL,
                year INTEGER NOT NULL,
                mimeType TEXT NOT NULL,
                addedDate INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_songs_folder ON songs (folderPath)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs (artist)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_songs_album ON songs (album)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS song_stats (
                path TEXT PRIMARY KEY,
                isFavorite INTEGER NOT NULL DEFAULT 0,
                isDisliked INTEGER NOT NULL DEFAULT 0,
                playCount INTEGER NOT NULL DEFAULT 0,
                lastPlayedTimestamp INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_songs (
                playlistId INTEGER NOT NULL,
                songId INTEGER NOT NULL,
                orderIndex INTEGER NOT NULL,
                PRIMARY KEY (playlistId, songId, orderIndex)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS song_tags (
                path TEXT PRIMARY KEY,
                title TEXT,
                track TEXT,
                year TEXT,
                genre TEXT,
                artist TEXT,
                album TEXT,
                albumArtist TEXT,
                composer TEXT,
                comment TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS song_stats (
                    path TEXT PRIMARY KEY,
                    isFavorite INTEGER NOT NULL DEFAULT 0,
                    isDisliked INTEGER NOT NULL DEFAULT 0,
                    playCount INTEGER NOT NULL DEFAULT 0,
                    lastPlayedTimestamp INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE song_stats ADD COLUMN isDisliked INTEGER NOT NULL DEFAULT 0")
            } catch (_: Exception) {}
        }
        if (oldVersion < 5) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS song_tags (
                    path TEXT PRIMARY KEY,
                    title TEXT,
                    track TEXT,
                    year TEXT,
                    genre TEXT,
                    artist TEXT,
                    album TEXT,
                    albumArtist TEXT,
                    composer TEXT,
                    comment TEXT
                )
                """.trimIndent()
            )
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = AppDatabase(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}

class EqualizerDaoImpl(private val helper: SQLiteOpenHelper) {
    fun getAllPresets(): List<PresetEntity> {
        val list = mutableListOf<PresetEntity>()
        val db = helper.readableDatabase
        db.rawQuery("SELECT * FROM presets", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    PresetEntity(
                        id = c.getString(0),
                        name = c.getString(1),
                        isCustom = c.getInt(2) == 1,
                        preampGainDb = c.getFloat(3),
                        bands10Gain = c.getString(4),
                        limiterEnabled = c.getInt(5) == 1,
                        limiterThresholdDb = c.getFloat(6)
                    )
                )
            }
        }
        return list
    }

    fun insertPreset(p: PresetEntity) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("id", p.id)
            put("name", p.name)
            put("isCustom", if (p.isCustom) 1 else 0)
            put("preampGainDb", p.preampGainDb)
            put("bands10Gain", p.bands10Gain)
            put("limiterEnabled", if (p.limiterEnabled) 1 else 0)
            put("limiterThresholdDb", p.limiterThresholdDb)
        }
        db.insertWithOnConflict("presets", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deletePreset(presetId: String) {
        helper.writableDatabase.delete("presets", "id = ?", arrayOf(presetId))
    }

    fun getAllDeviceProfiles(): List<DeviceProfileEntity> {
        val list = mutableListOf<DeviceProfileEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM device_profiles", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    DeviceProfileEntity(
                        deviceName = c.getString(0),
                        deviceType = c.getString(1),
                        presetId = c.getString(2),
                        enabled = c.getInt(3) == 1
                    )
                )
            }
        }
        return list
    }

    fun insertDeviceProfile(p: DeviceProfileEntity) {
        val cv = ContentValues().apply {
            put("deviceName", p.deviceName)
            put("deviceType", p.deviceType)
            put("presetId", p.presetId)
            put("enabled", if (p.enabled) 1 else 0)
        }
        helper.writableDatabase.insertWithOnConflict("device_profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllAppProfiles(): List<AppProfileEntity> {
        val list = mutableListOf<AppProfileEntity>()
        helper.readableDatabase.rawQuery("SELECT * FROM app_profiles", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    AppProfileEntity(
                        packageName = c.getString(0),
                        appName = c.getString(1),
                        presetId = c.getString(2),
                        enabled = c.getInt(3) == 1
                    )
                )
            }
        }
        return list
    }

    fun insertAppProfile(p: AppProfileEntity) {
        val cv = ContentValues().apply {
            put("packageName", p.packageName)
            put("appName", p.appName)
            put("presetId", p.presetId)
            put("enabled", if (p.enabled) 1 else 0)
        }
        helper.writableDatabase.insertWithOnConflict("app_profiles", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}

class SongDaoImpl(private val helper: SQLiteOpenHelper) {
    fun getAllSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val sql = """
            SELECT s.id, s.title, s.artist, s.album, s.albumId, s.durationMs, s.path, s.size, s.albumArtUri, s.folderPath, s.year, s.mimeType,
                   COALESCE(st.isFavorite, 0) AS isFavorite,
                   COALESCE(st.isDisliked, 0) AS isDisliked,
                   COALESCE(st.playCount, 0) AS playCount
            FROM songs s
            LEFT JOIN song_stats st ON s.path = st.path
            ORDER BY s.title COLLATE NOCASE ASC
        """.trimIndent()
        helper.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val songId = c.getLong(0)
                val songPath = c.getString(6)
                val songAlbum = c.getString(3)
                val rawArt = c.getString(8)
                list.add(
                    Song(
                        id = songId,
                        title = c.getString(1),
                        artist = c.getString(2),
                        album = songAlbum,
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        path = songPath,
                        size = c.getLong(7),
                        albumArtUri = sanitizeAlbumArtUri(rawArt, songId, songPath, songAlbum),
                        folderPath = c.getString(9),
                        year = c.getInt(10),
                        mimeType = c.getString(11),
                        isFavorite = c.getInt(12) == 1,
                        isDisliked = c.getInt(13) == 1,
                        playCount = c.getInt(14)
                    )
                )
            }
        }
        return list
    }

    fun updateSongAttitude(path: String, isFavorite: Boolean, isDisliked: Boolean) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("path", path)
            put("isFavorite", if (isFavorite) 1 else 0)
            put("isDisliked", if (isDisliked) 1 else 0)
        }
        val rows = db.update("song_stats", cv, "path = ?", arrayOf(path))
        if (rows == 0) {
            cv.put("playCount", 0)
            cv.put("lastPlayedTimestamp", 0L)
            db.insertWithOnConflict("song_stats", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun toggleFavorite(path: String, isFavorite: Boolean) {
        updateSongAttitude(path, isFavorite = isFavorite, isDisliked = false)
    }

    fun incrementPlayCount(path: String) {
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        var currentCount = 0
        var currentFav = 0
        var currentDisliked = 0
        db.rawQuery("SELECT isFavorite, isDisliked, playCount FROM song_stats WHERE path = ?", arrayOf(path)).use { cursor ->
            if (cursor.moveToFirst()) {
                currentFav = cursor.getInt(0)
                currentDisliked = cursor.getInt(1)
                currentCount = cursor.getInt(2)
            }
        }
        val cv = ContentValues().apply {
            put("path", path)
            put("isFavorite", currentFav)
            put("isDisliked", currentDisliked)
            put("playCount", currentCount + 1)
            put("lastPlayedTimestamp", now)
        }
        db.insertWithOnConflict("song_stats", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getFavoriteSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val sql = """
            SELECT s.id, s.title, s.artist, s.album, s.albumId, s.durationMs, s.path, s.size, s.albumArtUri, s.folderPath, s.year, s.mimeType,
                   1 AS isFavorite, 0 AS isDisliked, COALESCE(st.playCount, 0) AS playCount
            FROM songs s
            INNER JOIN song_stats st ON s.path = st.path
            WHERE st.isFavorite = 1 AND COALESCE(st.isDisliked, 0) = 0
            ORDER BY s.title COLLATE NOCASE ASC
        """.trimIndent()
        helper.readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val songId = c.getLong(0)
                val songPath = c.getString(6)
                val songAlbum = c.getString(3)
                val rawArt = c.getString(8)
                list.add(
                    Song(
                        id = songId,
                        title = c.getString(1),
                        artist = c.getString(2),
                        album = songAlbum,
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        path = songPath,
                        size = c.getLong(7),
                        albumArtUri = sanitizeAlbumArtUri(rawArt, songId, songPath, songAlbum),
                        folderPath = c.getString(9),
                        year = c.getInt(10),
                        mimeType = c.getString(11),
                        isFavorite = true,
                        isDisliked = false,
                        playCount = c.getInt(14)
                    )
                )
            }
        }
        return list
    }

    fun insertAll(songs: List<Song>) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            for (s in songs) {
                val cv = ContentValues().apply {
                    put("id", s.id)
                    put("title", s.title)
                    put("artist", s.artist)
                    put("album", s.album)
                    put("albumId", s.albumId)
                    put("durationMs", s.durationMs)
                    put("path", s.path)
                    put("size", s.size)
                    put("albumArtUri", s.albumArtUri)
                    put("folderPath", s.folderPath)
                    put("year", s.year)
                    put("mimeType", s.mimeType)
                    put("addedDate", System.currentTimeMillis())
                }
                db.insertWithOnConflict("songs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearAll() {
        helper.writableDatabase.delete("songs", null, null)
    }

    fun updateSongMetadata(songId: Long, title: String, artist: String, album: String, year: Int = 0) {
        val db = helper.writableDatabase
        val cv = ContentValues().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
            if (year > 0) {
                put("year", year)
            }
        }
        db.update("songs", cv, "id = ?", arrayOf(songId.toString()))
    }

    fun getSongMetadata(path: String): SongMetadata? {
        val db = helper.readableDatabase
        val sql = "SELECT title, track, year, genre, artist, album, albumArtist, composer, comment FROM song_tags WHERE path = ?"
        db.rawQuery(sql, arrayOf(path)).use { c ->
            if (c.moveToFirst()) {
                return SongMetadata(
                    title = c.getString(0).orEmpty(),
                    track = c.getString(1).orEmpty(),
                    year = c.getString(2).orEmpty(),
                    genre = c.getString(3).orEmpty(),
                    artist = c.getString(4).orEmpty(),
                    album = c.getString(5).orEmpty(),
                    albumArtist = c.getString(6).orEmpty(),
                    composer = c.getString(7).orEmpty(),
                    comment = c.getString(8).orEmpty()
                )
            }
        }
        return null
    }

    fun saveSongMetadata(path: String, songId: Long, metadata: SongMetadata) {
        val db = helper.writableDatabase
        val cvTags = ContentValues().apply {
            put("path", path)
            put("title", metadata.title)
            put("track", metadata.track)
            put("year", metadata.year)
            put("genre", metadata.genre)
            put("artist", metadata.artist)
            put("album", metadata.album)
            put("albumArtist", metadata.albumArtist)
            put("composer", metadata.composer)
            put("comment", metadata.comment)
        }
        db.insertWithOnConflict("song_tags", null, cvTags, SQLiteDatabase.CONFLICT_REPLACE)

        val yearInt = metadata.year.toIntOrNull() ?: 0
        val cvSong = ContentValues().apply {
            put("title", metadata.title)
            put("artist", metadata.artist)
            put("album", metadata.album)
            if (yearInt > 0) {
                put("year", yearInt)
            }
        }
        if (songId != 0L) {
            db.update("songs", cvSong, "id = ?", arrayOf(songId.toString()))
        }
        db.update("songs", cvSong, "path = ?", arrayOf(path))
    }

    fun getAllPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val db = helper.readableDatabase
        db.rawQuery("SELECT p.id, p.name, p.createdAt, COUNT(ps.songId) FROM playlists p LEFT JOIN playlist_songs ps ON p.id = ps.playlistId GROUP BY p.id ORDER BY p.createdAt DESC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    Playlist(
                        id = c.getLong(0),
                        name = c.getString(1),
                        createdAt = c.getLong(2),
                        songCount = c.getInt(3)
                    )
                )
            }
        }
        return list
    }

    fun insertPlaylist(name: String): Long {
        val cv = ContentValues().apply {
            put("name", name)
            put("createdAt", System.currentTimeMillis())
        }
        return helper.writableDatabase.insert("playlists", null, cv)
    }

    fun deletePlaylist(playlistId: Long) {
        val db = helper.writableDatabase
        db.delete("playlists", "id = ?", arrayOf(playlistId.toString()))
        db.delete("playlist_songs", "playlistId = ?", arrayOf(playlistId.toString()))
    }

    fun updatePlaylistName(playlistId: Long, newName: String): Int {
        val cv = ContentValues().apply {
            put("name", newName)
        }
        return helper.writableDatabase.update("playlists", cv, "id = ?", arrayOf(playlistId.toString()))
    }

    fun insertSongToPlaylist(playlistId: Long, songId: Long, orderIndex: Int) {
        val cv = ContentValues().apply {
            put("playlistId", playlistId)
            put("songId", songId)
            put("orderIndex", orderIndex)
        }
        helper.writableDatabase.insertWithOnConflict("playlist_songs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long): Int {
        return helper.writableDatabase.delete("playlist_songs", "playlistId = ? AND songId = ?", arrayOf(playlistId.toString(), songId.toString()))
    }

    fun getSongsInPlaylist(playlistId: Long): List<Song> {
        val list = mutableListOf<Song>()
        val sql = """
            SELECT s.id, s.title, s.artist, s.album, s.albumId, s.durationMs, s.path, s.size, s.albumArtUri, s.folderPath, s.year, s.mimeType,
                   COALESCE(st.isFavorite, 0) AS isFavorite, COALESCE(st.playCount, 0) AS playCount
            FROM songs s
            INNER JOIN playlist_songs ps ON s.id = ps.songId
            LEFT JOIN song_stats st ON s.path = st.path
            WHERE ps.playlistId = ?
            ORDER BY ps.orderIndex ASC, s.title COLLATE NOCASE ASC
        """.trimIndent()
        helper.readableDatabase.rawQuery(sql, arrayOf(playlistId.toString())).use { c ->
            while (c.moveToNext()) {
                val songId = c.getLong(0)
                val songPath = c.getString(6)
                val songAlbum = c.getString(3)
                val rawArt = c.getString(8)
                list.add(
                    Song(
                        id = songId,
                        title = c.getString(1),
                        artist = c.getString(2),
                        album = songAlbum,
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        path = songPath,
                        size = c.getLong(7),
                        albumArtUri = sanitizeAlbumArtUri(rawArt, songId, songPath, songAlbum),
                        folderPath = c.getString(9),
                        year = c.getInt(10),
                        mimeType = c.getString(11),
                        isFavorite = c.getInt(12) == 1,
                        playCount = c.getInt(13)
                    )
                )
            }
        }
        return list
    }
    private fun sanitizeAlbumArtUri(rawArtUri: String?, songId: Long, path: String, album: String?): String {
        if (rawArtUri.isNullOrBlank() || rawArtUri.contains("media/external/audio/albumart")) {
            return com.antigravity.equalizer.data.provider.AudioCoverProvider.buildSongCoverUri(songId, path, album)
        }
        return rawArtUri
    }
}
