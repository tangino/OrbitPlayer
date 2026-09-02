package com.antigravity.equalizer.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.antigravity.equalizer.data.model.Playlist
import com.antigravity.equalizer.data.model.Song

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
    SQLiteOpenHelper(context, "equalizer_music_v2.db", null, 2) {

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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS presets")
        db.execSQL("DROP TABLE IF EXISTS device_profiles")
        db.execSQL("DROP TABLE IF EXISTS app_profiles")
        db.execSQL("DROP TABLE IF EXISTS songs")
        db.execSQL("DROP TABLE IF EXISTS playlists")
        db.execSQL("DROP TABLE IF EXISTS playlist_songs")
        onCreate(db)
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
        helper.readableDatabase.rawQuery("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC", null).use { c ->
            while (c.moveToNext()) {
                list.add(
                    Song(
                        id = c.getLong(0),
                        title = c.getString(1),
                        artist = c.getString(2),
                        album = c.getString(3),
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        path = c.getString(6),
                        size = c.getLong(7),
                        albumArtUri = c.getString(8),
                        folderPath = c.getString(9),
                        year = c.getInt(10),
                        mimeType = c.getString(11)
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

    fun insertSongToPlaylist(playlistId: Long, songId: Long, orderIndex: Int) {
        val cv = ContentValues().apply {
            put("playlistId", playlistId)
            put("songId", songId)
            put("orderIndex", orderIndex)
        }
        helper.writableDatabase.insertWithOnConflict("playlist_songs", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
