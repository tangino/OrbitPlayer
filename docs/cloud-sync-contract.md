# Cloud Sync 契约参考（Cloudflare Worker + D1）

> **本文件是从 [`AGENTS.MD`](../AGENTS.MD) §7 拆出的参考文档。**
> 实施任何云端同步相关代码（歌单同步、音源同步、账号登录）之前**必须完整阅读本文件**。
> 源码位置：`D:\development\OrBitPlayer\server\orbit\`（`src/index.ts` 单文件 672 行 + `schema.sql`）
> 该目录另有独立 `AGENTS.md`：任何**改动 Worker** 的工作必须先去查最新 Cloudflare 文档（`developers.cloudflare.com/workers/`、`/d1/`）。

## 0. 部署与配置

`wrangler.jsonc`（当前生效内容）：

```jsonc
{
  "$schema": "node_modules/wrangler/config-schema.json",
  "name": "orbit",
  "main": "src/index.ts",
  "compatibility_date": "2026-10-06",
  "observability": { "enabled": true },
  "upload_source_maps": true,
  "d1_databases": [
    { "binding": "orbit_db", "database_name": "orbit_db",
      "database_id": "43bc9f93-4145-4d83-8a09-6f2047f32ff6" }
  ]
}
```

- Worker 名 `orbit`；D1 绑定 `orbit_db`；**没有 KV / R2 / Durable Objects / Queues / Vectorize / AI / service bindings / vars / secrets**。
- **没有 `routes` / 自定义域 / `workers_dev` 配置** → 仓库里**查不到线上地址**。客户端必须让用户自填（RawS 侧同样不内嵌 URL）。
- 预期形如 `https://orbit.<account-subdomain>.workers.dev`。本地开发 `npx wrangler dev` 打印 `http://localhost:<port>`（Android 模拟器用 `http://10.0.2.2:<port>`）。
- 脚本：`npm run deploy` / `npm run dev` / `npm test` / `npm run cf-typegen`。
- `worker-configuration.d.ts` **已过期**（缺 `orbit_db` 绑定），`src/index.ts` 内的 `Env` 才是权威；`test/index.spec.ts` 是过时的 Hello World 模板，无参考价值。

## 1. 鉴权机制

- **无 token / 无 session / 无 Cookie / 无 JWT / 无 deviceId / 无 HMAC 签名**。`API_SECRET` 在 `Env` 里声明但**从未被引用**。
- `username` + **明文 `password`** 放在**每个请求的 body**；`password` 省略或为空时该请求按**匿名**处理直接放行。
- `POST /api/user/auth` 在账号不存在时**自动注册**（打错密码会静默建号）。
- 密码哈希：`SHA-256(password + "_orbit_salt_2026")` 的 hex 小写字符串（固定全局盐，无 per-user 盐、无 pepper）。
- CORS：所有响应带 `Access-Control-Allow-Origin: *`；`OPTIONS` 返回 204。
- 错误包装：整个路由体在一个 `try/catch` 内，异常 → `500 {"success":false,"error":"<message>"}`；未匹配路径 → `404 {"success":false,"message":"Not Found: 接口不存在"}`。

## 2. 路由表

| 方法 | 路径 | 用途 | 关键请求字段 | 响应 |
| --- | --- | --- | --- | --- |
| ANY | `/`、`/api/health` | 健康检查 | — | `{success, name, version, database, timestamp}` |
| POST | `/api/user/auth` | 登录/自动注册 | `username`, `password` | `{success, username, isNew, message}`；400（缺字段）/401（密码错） |
| GET | `/api/sources` | 公开音源列表（**无需鉴权**） | — | `{success, count, data:[source_scripts 行]}` |
| POST | `/api/sources` | 发布/覆盖音源（**完全未鉴权**） | `id`, `name`, `scriptUrl`(必填), `version?`, `author?`, `description?`, `isActive?` | `{success, message}` |
| POST | `/api/user/sources/backup` | 音源备份（全量覆盖） | `username\|userId`, `password?`, `sourcesJson`(必填), `sourceCount?` | `{success, message, username, count, updatedAt}` |
| GET/POST | `/api/user/sources/restore` | 音源还原 | `username\|userId`, `password?` | `{success, username, data:{user_id, sources_json, source_count, created_at, updated_at}}`；无备份 404 |
| POST/DELETE | `/api/user/sources/delete` | 删除音源备份 | 同上 | `{success, message, username}` |
| POST | `/api/user/playlists/backup` | **歌单备份（核心）** | `username\|userId`, `password?`, `playlistsJson`(必填), `onlineFavoritesJson?`, `groupsJson?`, `playlistCount?`(服务端忽略), `mode`=`merge`\|`overwrite`（默认 merge） | `{success, message, mode, username, count, updatedAt}` |
| GET/POST | `/api/user/playlists/restore` | 歌单还原 | `username\|userId`, `password?` | `{success, username, data:{user_id, playlists_json, online_favorites_json, groups_json, playlist_count, created_at, updated_at}}`；无备份 404 |
| POST/DELETE | `/api/user/playlists/delete` | 选择性/全量删除云端歌单 | `username`, `password?`, `deleteAll?`, `localPlaylistNames?[]`, `onlinePlaylistIds?[]` | `{success, message, count}` |
| POST | `/api/user/data/clear` | 清空该账号所有云端数据（**不删 `users` 行**） | `username`, `password?`（**不接受 `userId` 别名**） | `{success, message}` |
| POST | `/api/playlist/sync` | 旧版单歌单同步（未鉴权） | `id`, `userId`, `playlistName`, `songListJson?`, `songCount?` | `{success, message, id, updatedAt}` |
| GET | `/api/playlist/pull?userId=` | 旧版拉取 | — | `{success, count, data:[...]}` |
| POST/DELETE | `/api/playlist/delete?id=&userId=` | 旧版删除（**只读 query，忽略 body**） | — | `{success, message}` |
| POST | `/api/settings/sync` | 设置上传（未鉴权） | `userId`, `preferredQuality?`, `equalizerPreset?`, `settingsJson?` | `{success, message, updatedAt}` |
| GET | `/api/settings/pull?userId=` | 设置下载（未鉴权） | — | `{success, data: row\|null}`（无数据也是 200） |
| OPTIONS | `*` | CORS 预检 | — | 204 + CORS 头 |

**本移植只使用**：`/api/user/auth`、`/api/user/sources/{backup,restore,delete}`、`/api/user/playlists/{backup,restore,delete}`、`/api/user/data/clear`。
（社区音源广场若启用，另加 `GET /api/sources`；`POST /api/sources` 因无鉴权暂不建议开放。）

**客户端请求构造**：base URL 去掉末尾 `/`，拼成 `"$baseUrl/api/..."`；请求体为 JSON。

## 3. D1 表结构（`schema.sql` 全文）

```sql
-- 0. 用户账号表
CREATE TABLE IF NOT EXISTS users (
    username TEXT PRIMARY KEY,
    password_hash TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- 1. 用户歌单与收藏同步表（旧版单歌单）
CREATE TABLE IF NOT EXISTS user_playlists (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    playlist_name TEXT NOT NULL,
    song_list_json TEXT NOT NULL,
    song_count INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_playlists_user_id ON user_playlists(user_id);

-- 1.1 用户歌单与分组多端备份表（跨端选择性备份与恢复）
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

-- 2.1 用户多音源全量云端备份表
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
```

要点：
- 时间戳一律 **epoch 毫秒**（`Date.now()`），无 ISO 字符串。
- `user_id` 在所有表里 == `username` 字符串。
- 无外键、无额外 UNIQUE、**无 version / revision / device / tombstone 列** → 没有行级冲突解决能力。
- 全库唯一索引：`idx_playlists_user_id`。
- **没有广场表、没有 likes/dislikes 表、没有 QQ/酷狗 token 表、没有 LX 音源元数据表**。

## 4. 客户端必须复刻的 JSON 契约

```jsonc
// ① playlists_json —— 本地歌单数组（顶层是数组）
[{
  "name": "歌单名",
  "groupName": "默认",
  "createdAt": 1730000000000,
  "songs": [{
    "title": "...",
    "artist": "...",
    "album": "...",
    "durationMs": 0,
    "path": "/storage/... 或 online://<platformId>/<songId>",
    "albumArtUri": "https://... 或 \"\"",
    "coverUrl": "https://... 或 \"\"",
    "isOnline": false,
    "sourcePlatform": "QQ|NETEASE|KUGOU|KUWO|MIGU 或 \"\"",
    "isFavorite": false,
    "isDisliked": false
  }]
}]

// ② online_favorites_json —— 收藏的在线歌单数组
[{ "id":"...", "platform":"NETEASE|QQ|KUGOU|KUWO|MIGU", "title":"...",
   "coverUrl":"...", "trackCount":0, "creatorName":"", "creatorAvatarUrl":"",
   "description":"", "customGroup":"默认" }]

// ③ groups_json —— 分组名数组（客户端会放入 "系统"）
["分组名", ...]

// ④ sources_json —— 音源数组（Worker 视为不透明字符串，客户端自定义形状）
//
// ⚠️ 下面这份**以 OrBit 的真实实现为准**（`app/src/main/java/com/orbit/music/data/online/model/SourceScriptItem.kt`
//    的 `toJson()` / `fromJson()`）。本节原先写的是 `enabled` / `installedAtMs` / 无类型的
//    `supportPlatforms`，与 OrBit 实际不符 —— 照那份写会导致 OrBit ↔ RawS 互相读不到，已于 2026-10 订正。
[{ "id", "name", "version", "author", "description", "scriptContent", "sourceUrl",
   "isEnabled",          // ← 不是 enabled
   "isPrimary",
   "supportPlatforms",   // ← 逗号分隔**字符串**（OrBit 用 joinToString(",") 写、optString 读），不是数组
   "importedAt",         // ← 不是 installedAtMs
   // ↓ 以下两个是 RawS 的扩展字段，OrBit 没有。OrBit 按 key 取值，多出来的键会被忽略，
   //   所以多写它们是安全的（换来 RawS 侧能做 sha256 完整性校验）。
   "scriptSha256", "updatedAtMs" }]
```

> **`platforms` / `actions` / `format` 不要往云端带**：它们在 RawS 侧是安装时从脚本正文解析出来的
> （`LxSourcePluginStore` 的 `descriptor.platforms` / `metadata.format`），只要 `scriptContent` 在，
> 还原后会自动重新推导。`platforms` 里唯一有跨端意义的只是**平台码**，对应 `supportPlatforms`。

**喜欢 / 不喜欢没有独立表或接口**。它们作为两个**系统歌单**塞进 `playlists_json`：

| 常量 | 值 |
| --- | --- |
| `SYSTEM_PLAYLIST_FAVORITE` | `"我喜欢的音乐"` |
| `SYSTEM_PLAYLIST_DISLIKED` | `"不喜欢的音乐"` |
| `SYSTEM_GROUP_NAME` | `"系统"` |

每首歌上带 `isFavorite` / `isDisliked` 标记。还原时客户端识别这两个名字 → 走态度批量同步（RawS 侧对应 `SongAttitudeRepository.batchSync(...)`）而**不是**建普通歌单。

> **RawS 侧必须沿用这套名字与语义**，否则 OrBit ↔ RawS 跨端数据无法互认。

**RawS 音源同步的结构差异（重要）**：
- OrBit 的 `SourceScriptItem` **内嵌脚本正文** `scriptContent`，整体存 SharedPreferences。
- RawS 的 `InstalledLxSource` / `InstalledMusicSource` **只存脚本文件路径 + sha256**（`scriptPath`, `scriptSha256`）。
→ 上传时必须**读取脚本文件内容并内联进 JSON**，否则云端备份还原后拿不到脚本。
→ 还原时：写回脚本文件到插件目录 → 计算 sha256 校验 → 走**既有安装 API**（`LxSourcePluginStore` / `MusicSourcePluginStore`）注册，不要绕过。
→ 兼容 OrBit 备份：有 `scriptContent` 无 `scriptPath` 时按内联脚本安装；只有路径没有正文时给出明确错误（"该备份不完整"），不静默失败。

## 5. 服务端合并语义（`mode="merge"`，默认）

仅当该用户**已存在备份行**时执行合并：

- **本地歌单按 `name` 合并**：
  - 同名 → 歌曲并集，去重键 `s.path || "${s.title}__${s.artist}"`（Map，**上传值覆盖已有值**）；
    `groupName = p.groupName || existing.groupName || "默认"`；
    `createdAt = p.createdAt || existing.createdAt || now`。
  - 新名字 → 原样追加。
  - 上传侧缺 `name` 的歌单被跳过。
- **在线收藏按 `op.id` 合并**（同 id 时上传值覆盖）。
- **分组** = `[...existing, ...incoming]` 去重后 `.filter(g => g && g !== "默认")` → **`"默认"` 永远被丢弃**，`"系统"` 保留。
- 合并块内任何 `JSON.parse` 异常被 `catch (_e) {}` 静默吞掉，随后存原始上传值。
- `mode !== "merge"`（即 `"overwrite"`）跳过合并，直接覆盖存储。
- `playlist_count` = 合并后本地歌单数 + 在线收藏数；客户端传的 `playlistCount` 被解析但**从不使用**。
- 无分页、无 `Cache-Control` / `ETag`、无限流、无 `If-Match`、无版本号、无墓碑 → **last-write-wins**。

## 6. 服务端已知问题（客户端必须容错）

1. `playlistsJson` / `onlineFavoritesJson` / `groupsJson` 的 `JSON.parse` 在 backup 路径**未做保护** → 非法 JSON 直接 **500**。客户端必须保证序列化合法。
2. `/api/user/playlists/delete` 若**既不传 `deleteAll` 也不传两个数组**，会**清空全部云端歌单**。客户端必须显式传参（清空要显式 `deleteAll: true`）。
3. `POST|DELETE /api/user/playlists/delete` 的选择性删除**不会回写 `groups_json`**（分组信息可能残留）。
4. `GET /api/playlist/pull`、`POST /api/settings/sync`、`GET /api/settings/pull`、旧版 `/api/playlist/*` 全部**未鉴权**。
5. 密码明文随每次请求传输；省略 `password` 即匿名放行 —— 任何知道用户名的人都能读写其数据。
6. 用未知用户名 + `password` 调 restore/delete 会**先创建 `users` 行**再返回 404（自动注册副作用）。
7. 整个歌单库塞在一个 TEXT 单元格里，**没有分片或大小校验** → 超大曲库会撞 D1 行大小限制。
8. **一旦后端要改**（加鉴权、加表、加字段），属于"改后端"，**必须先取得用户明确同意**并在 `AGENTS.MD` §9 登记。
