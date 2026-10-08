-- 0. 用户账号表 (支持账号密码登录与多端同步)
CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 1. 用户歌单与收藏同步表
CREATE TABLE IF NOT EXISTS user_playlists (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    playlist_name TEXT NOT NULL,
    song_list_json TEXT NOT NULL,
    song_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 为 user_id 创建索引加速查询
CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON user_playlists(user_id);

-- 1.1 用户歌单与分组多端备份表 (用于跨端选择性备份与恢复)
CREATE TABLE IF NOT EXISTS user_playlist_backups (
    user_id TEXT PRIMARY KEY,
    playlists_json TEXT NOT NULL,
    online_favorites_json TEXT,
    groups_json TEXT,
    playlist_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 2. 音源脚本共享与订阅表
CREATE TABLE IF NOT EXISTS source_scripts (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    version TEXT NOT NULL,
    author TEXT,
    description TEXT,
    script_url TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 2.1 用户多音源全量云端备份表 (用于跨设备无缝备份与恢复)
CREATE TABLE IF NOT EXISTS user_source_backups (
    user_id TEXT PRIMARY KEY,
    sources_json TEXT NOT NULL,
    source_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 3. 用户偏好与均衡器音效配置同步表
CREATE TABLE IF NOT EXISTS user_settings (
    user_id TEXT PRIMARY KEY,
    preferred_quality TEXT NOT NULL DEFAULT 'flac',
    equalizer_preset TEXT,
    settings_json TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
