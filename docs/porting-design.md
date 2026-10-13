# 移植设计详案（原 `AGENTS.MD` §6）

> 本文件是 [`AGENTS.MD`](../AGENTS.MD) **§6「四大功能移植设计」**的完整正文。
> **为什么拆出来**：`AGENTS.MD` 一度达到 68 KB，而工作区指令预算只有 65536 B，
> 导致加载时**从尾部截断约 2.5 KB** —— §10 工作流规则与 §11 参考资料对读者长期不可见。
> 把体量最大的 §6 移到这里之后 `AGENTS.MD` 回到预算内，不再截断。
>
> **§6 依然是权威设计**，与 `AGENTS.MD` 同等效力；`AGENTS.MD` §6 处保留摘要与关键红线。
> 配套文档：[`porting-reference.md`](porting-reference.md)（源→目标映射、文件索引、平台端点速查）、
> [`cloud-sync-contract.md`](cloud-sync-contract.md)（云同步契约）。

---

## 6. 四大功能移植设计

> **开工前先读 [`docs/porting-reference.md`](docs/porting-reference.md)**：内含完整的「源 → 目标」映射总表、RawS/OrBit 关键文件索引、5 个平台端点速查。
> 云同步相关的所有代码，动手前先读 [`docs/cloud-sync-contract.md`](docs/cloud-sync-contract.md)。

**移植总原则**：只搬**数据 / 算法 / 常量**，界面全部用 Miuix 重写；OrBit 的存储（`SQLiteOpenHelper` / `SharedPreferences`）一律改造成 RawS 的 Room / `AppPreferences`；OrBit 的 ViewModel 一律改造成 RawS 的 Store + `StateFlow`。

### 6.1 在线歌单广场（含歌手库 + 全网搜歌）

**现状差距**：RawS **完全没有**原生平台曲库（只有用户导入的 LX/MusicFree 脚本），所以要把 OrBit 的 5 个平台适配器整套搬过来。

**新增模块 `:module:online`**（推荐）：

- `namespace = "com.rawsmusic.module.online"`；`settings.gradle.kts` 加 `include(":module:online")`。
- 依赖：`project(":core:common")`、`project(":module:data")`、`libs.okhttp`、`libs.gson`、`org.json`（内置）、`com.google.zxing:core`、coroutines、`libs.androidx.core.ktx`。
- `:core:ui` 与 `:app` 各加 `implementation(project(":module:online"))`。
- `:module:online` **不得依赖 `:core:ui`**（保持分层），UI 一律在 `core:ui`。
- 备选（不推荐，仅在新增模块导致构建问题时）：放进 `:module:data` 的 `com.rawsmusic.module.data.online.*`，并给 `:module:data` 加 `libs.okhttp` + ZXing。

**要移植的内容**

1. **模型**：`OnlinePlatform`（5 平台，`id` / `displayName`）、`OnlinePlaylistTag`、`OnlinePlaylist`、`OnlineSongItem`、`OnlineLeaderboard`、`OnlineArtistCategory`、`OnlineArtist`、`OnlineAlbum`、`OnlineArtistDetail`。
2. **接口 `OnlinePlatformSource`**（原 `IOnlineMusicSource`）：`getTags` / `getPlaylists` / `getLeaderboards` / `getPlaylistDetail` / `searchPlaylists` / `searchSongs` / `extractPlaylistId` + 歌手与专辑方法。**保留方法的默认空实现**（酷我/咪咕/酷狗只实现部分能力）。
3. **5 个平台实现 + `NeteaseCrypto`**（网易 weapi/eapi）。端点常量、Referer、JSONP 清洗、fallback 链**全部照搬，不要「优化」**——这些是脆弱的外部契约，速查见 `docs/porting-reference.md` §4。
4. **`OnlineMusicRepository`**：
   - 平台切换 `switchPlatform`
   - 缓存（改 RawS 风格：`LruCache` 或 `StateFlow` + 时间戳；详情缓存 50 条）
   - **全网搜歌**：`searchSongsAllPlatforms`（5 平台并发 fan-out，返回 `Map<OnlinePlatform, Result<List<OnlineSongItem>>>`）+ `searchSongsAggregated`（轮询合并 + 去重）
   - 歌手库：分类 / 列表 / 搜索 / 详情 / 歌手歌曲 / 歌手专辑
   - 专辑：`getAlbumDetail` + `resolveAlbumDetail`（按专辑名回退搜索的兜底策略）
   - `parseLinkOrText`（粘贴分享链接/文本自动识别平台）
5. **播放解析适配层**（**关键：不要移植 `OnlineAudioSourceManager`**）
   - 广场歌曲只有平台 catalog 元数据、没有播放地址。播放时**复用 RawS 现有能力**：把 `OnlineSongItem` 转成 `RawSourceMediaItem`，交给 `MusicSourceAudioResolver` / `MusicSourcePlaybackController` / `MusicSourceRuntimeService` 解析 URL。
   - 从 `OnlineAudioSourceManager` **只摘算法**：
     - 跨平台歌曲匹配打分（`SongMatcher`：标题清洗、歌手拆分、时长容差、`calculateMatchScore`）→ `OnlinePlaybackMatcher`
     - 音质阶梯 `flac24bit → flac → 320k → 128k` 及其余降级规则
     - URL 探测校验（HEAD/Range + `MediaMetadataRetriever` 时长校验）→ `OnlinePlaybackProbe`
     - 网易 outer url / 咪咕直链兜底
   - 这些作为**候选筛选与兜底**接在 RawS 解析器之前/之后，**不要替换** RawS 的解析器。
   - 两种寻源策略（经典轮询 / 并发竞速）作为可选项保留，默认沿用 RawS 行为。
6. **在线歌单收藏 → Room 新表**（与 §6.2 的建表放在同一个 `MIGRATION_6_7`，即 DB version 6→7）
   ```sql
   online_playlist_favorites(
     playlist_key TEXT NOT NULL PRIMARY KEY,
     platform TEXT NOT NULL, remote_id TEXT NOT NULL, title TEXT NOT NULL,
     cover_url TEXT NOT NULL DEFAULT '', play_count INTEGER NOT NULL DEFAULT 0,
     track_count INTEGER NOT NULL DEFAULT 0,
     creator_name TEXT NOT NULL DEFAULT '', creator_avatar_url TEXT NOT NULL DEFAULT '',
     description TEXT NOT NULL DEFAULT '', group_name TEXT NOT NULL DEFAULT '默认',
     added_at INTEGER NOT NULL)
   CREATE INDEX index_online_playlist_favorites_platform ON online_playlist_favorites(platform)
   ```
7. **搜索历史**：优先复用 `SearchHistoryEntity`，加 `scope` 列区分本地/在线；确有必要才新建表。
8. **UI（全部 Miuix 重写）**：
   - 新增 `NavScene.ONLINE_MUSIC`（**id 54**，当前最大是 53）+ `core/ui/.../scene/pages/OnlineMusicPortalPage.kt`，骨架照抄 `SourceImportPage`（自带二级 tab + 底部 `LiquidBottomTabs` + 独立返回栈）。
   - Portal 内部 tab：**歌单广场** / **歌手库** / **全网搜歌** / **我的收藏**。
   - 「歌单广场」内：顶部平台选择（横向 chip 行，选中态 `colors.primary.copy(alpha = 0.12f)`——照抄 `SourcePortalChrome.kt` 的 chip 写法）；次级 tab（精选推荐 / 热门分类 / 官方榜单 / 我的收藏）；歌单卡片网格；下拉刷新；分页加载。
     **网格实现照 RawS 既有做法**（§4.5）：`chunked(N)` + `VirtualListCustomProvider`，或直接用 `LazyColumn` 内嵌固定高度的行。**不要引入 `LazyVerticalGrid`**（全仓 0 使用）；确要用必须在 §9 登记新约定。
   - 「歌单详情」：`NavScene.ONLINE_PLAYLIST_DETAIL`（id 53）或 Portal 内二级页；曲目列表 + 播放全部 / 随机播放 / 收藏 / 导入到本地歌单 / 下载。
   - 「歌手库」：分类 chip + 网格；歌手详情（热门歌曲 / 专辑 / 简介，简介可对接 RawS 已有的 `ArtistBiographyRepository`）。
   - 「全网搜歌」：标题 + 歌手双输入模式、平台过滤器（全部 / 单平台）、结果按平台分组、平台结果计数、分页加载更多、搜索历史。也可注册成 `GlobalSearchScope` 的新类别接入全局搜索页。
   - 全部使用 `SectionHeader` / `SettingsCard` / `themeColors()` / `appFontFamily()`；文案加 `online_*` 前缀 key（§4.7）。

**风险**：5 个平台接口是逆向契约，随时失效。**必须保留 fallback 链与错误隔离**（单平台失败不影响其它平台，并用 `AppNoticeBus` 提示）。

### 6.2 歌单管理

RawS 现有：`user_playlists` / `user_playlist_songs`（Room）+ `PlaylistStore`。需补 5 块。

**(A) 我喜欢的歌 / 我不喜欢的歌**

> ⚠️ **本节原先的假设是错的**（原写「喜欢/不喜欢都不存在」）。实测 RawS **已有**一套完整的「喜欢」机制：
> 系统歌单 `FAVORITES_PLAYLIST_ID`（`core/common/.../model/UserPlaylist.kt`，名**正好是**「我喜欢的音乐」）
> + `PlaylistStore.favoriteSongKeys()/isFavorite()/toggleFavorite()`，并且已打通 **播放器**（`PlayerService`）、
> **长按菜单**（`SongActionSheetHelper`）、**歌单页**（`PlaylistScreen` 的心形、粉色强调色、禁止改名/删除）、
> **WebDAV 备份**（`BackupAndroidPersistence` / `BackupArchiveCodec`）。
>
> 若按本节字面新建 `song_attitude` 并把它当作「喜欢」的事实来源，会**制造第二个事实来源**：
> 播放器写一处、歌单页读另一处，心形状态不一致。
>
> **已确认的决策（§9 D14，用户拍板）——混合方案**：
>
> | 场景 | 归属 | 说明 |
> | --- | --- | --- |
> | **本地曲的「喜欢」** | `PlaylistStore`（**既有**） | 不要走 `song_attitude.is_favorite` |
> | **「不喜欢」（本地 + 在线）** | `song_attitude.is_disliked` | RawS 原本没有，纯新增 |
> | **在线曲的「喜欢」** | `song_attitude.is_favorite` | 既有歌单只能装 `AudioFile`，装不下在线曲 |
>
> 实现见 `module/data/.../repository/SongAttitudeRepository.kt`（它**只**负责后两行）。

- Room 新表（与 §6.1 建表同在 `MIGRATION_6_7`）：
  ```sql
  song_attitude(song_key TEXT NOT NULL PRIMARY KEY,
                is_favorite INTEGER NOT NULL DEFAULT 0,
                is_disliked INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL)
  CREATE INDEX index_song_attitude_is_favorite ON song_attitude(is_favorite)
  CREATE INDEX index_song_attitude_is_disliked ON song_attitude(is_disliked)
  ```
- `song_key` 语义：本地曲 = `AudioFile` 稳定标识（**必须沿用 `UserPlaylistSongEntity.song_key` 的取值规则**）；在线曲 = `online://<platformId>/<songId>`。
- 新建 `SongAttitudeDao` + `SongAttitudeRepository`（`StateFlow`）。
- 行为：`NONE → FAVORITE → DISLIKED → NONE` 循环；另有 `setFavorite` / `removeDislike` / `clearAllDislikes`。
- UI：
  - ⚠️ **改 `app/src/main/kotlin/com/rawsmusic/ui/playlist/PlaylistScreen.kt`（592 行），不是 `core/ui/.../scene/pages/PlaylistsPage.kt`。**
    `PlaylistsPage.kt` 只有 29 行、是个只渲染空态的**占位页**；真正在用的歌单列表走外部渲染逃生口：
    `ComposeNavHost.kt` 的 `NavScene.PLAYLISTS -> externalPageRenderer?.RenderPage(...)` → `AppPageRenderer.kt:120-131` → `PlaylistScreen(...)`。
  - 在该页的 `LibraryListScaffold` content / `VirtualListCustomProvider` 路径顶部插入两个伪歌单卡片：`我喜欢的歌`、`我不喜欢的歌`，曲目数实时显示（参考 OrBit 的 `FavoritePlaylistItemCard` / `DislikedPlaylistItemCard` 的**信息结构**，不是样式）。
    该页已有 2 列网格写法（`playlists.chunked(2)` + `playlistRowHeight`），伪歌单卡片按同一行高体系插入。`PlaylistScreen.kt:189` 与 `PlaylistDetailScreen.kt` 是 `LibraryListScaffold` 的现成用法范例。
  - 歌曲长按菜单加「喜欢 / 不喜欢 / 取消不喜欢」。
  - 「清空所有不喜欢」二次确认弹窗。
  - 批量操作栏支持批量喜欢。
  - 播放时跳过不喜欢的策略**沿用 RawS 现有 play mode 逻辑**，不要新造。

**(B) 歌单分组**

- Room：`user_playlists` 加列 `group_name TEXT NOT NULL DEFAULT '默认'`；新建 `playlist_groups(name TEXT NOT NULL PRIMARY KEY, sort_order INTEGER NOT NULL)`。`ALTER TABLE` 要与 Room schema 校验一致。
- 默认分组 `默认`；OrBit 的 `🚗 车载专用` / `📱 手机精选` 等作为**可增删的种子分组**，不要硬编码强塞。
- `PlaylistGroupStore`：增 / 改名 / 删 / 排序；删分组时其下歌单回落 `默认`。
- UI：歌单页按 `SectionHeader(groupName)` 分区；歌单/分组长按 → 移动分组、改名、删除。

**(C) 歌单导入导出 / 链接导入**

- 移植 `PlaylistTransferManager` → `module/data/.../playlist/PlaylistTransferManager.kt`：
  - 格式：JSON（自有 schema `version=1`）、M3U8（`#EXTINF` + 自定义标签，把 `#EXTORBIT-*` 改名为 `#EXTRAWS-*`）、纯文本 `歌名 - 歌手` 行
  - 导出：`cacheDir/playlist_exports` → `ACTION_SEND` 分享；或写入 MediaStore `Downloads/RawS-Music`
  - 导入：链接/文本识别（先平台链接解析 → JSON → M3U8 → 纯文本）
  - **开工前先读 `UserPlaylistDao` + `PlaylistStore`**，把在线虚拟曲目与 `song_key` / `media_id` 的约定定清楚再动手。
- UI：`ExportPlaylistDialog` / `ImportPlaylistDialog` 用 Miuix 重写。

**(D) QQ音乐 / 酷狗音乐 登录与歌单导入**

- 移植 `IPlatformAuthService` + `QQMusicAuthService` + `KugouMusicAuthService` + `PlatformAccountModels` + `PlatformAccountManager` + `QrCodeGenerator`（ZXing 生成二维码 Bitmap）。**完整端点/参数见 [`docs/porting-reference.md`](docs/porting-reference.md) §4，照搬勿改。**
- ⚠️ 酷狗登录有 **10 条候选 URL 兜底链**、签名盐 `WEB_SIGN_SALT` / `ANDROID_SIGN_SALT`、`APP_ID=1005` / `SRC_APP_ID=2919` / `PLAT_ID=4` —— **必须全部保留**，集中成常量并注释来源（易变）。
- **凭据存储**：OrBit 用明文 SharedPreferences，**不要效仿**。
  → 在 `AppPreferences` 加 `object PlatformAuth`；cookie/token 用 Android Keystore 派生密钥做 **AES-GCM 加密**后存 MMKV。`EncryptedSharedPreferences` 不可用则不引入新库，自己用 `javax.crypto` + Keystore。
- UI（Miuix 重写）：
  - 「平台账号」页：QQ / 酷狗两张账号卡（头像、昵称、VIP、登录/退出/刷新），入口放设置「在线音乐」分组。
  - 扫码登录弹窗：二维码位图 + 轮询状态（等待扫码 / 已扫描待确认 / 成功 / 过期可刷新），用 `RawMiuixOverlayDialog` 承载，`DisposableEffect` 取消轮询。
  - 「导入我的歌单」：拉取账号歌单 → 多选 → 导入为本地歌单（在线曲目保留 `online://` 标识，播放经 §6.1 解析层）。

**(E) 歌单同步（D1）**

见 §6.5 与 [`docs/cloud-sync-contract.md`](docs/cloud-sync-contract.md)。移植 `CloudPlaylistSyncManager` 的**合并/覆盖语义与 JSON 形状**，UI 用 Miuix 重写（骨架照抄 `WebDavPage.kt`）。

**(F) 其他（低优先，可选）**

- 最近播放歌单（`PlayedPlaylistManager`）：`AppPreferences` 存 `current` / `previous` 两个条目。
- 歌单排序、歌单封面自定义：先查 RawS 是否已有；无则登记为 P3。

### 6.3 洛雪歌源同步

**RawS 已有**：`LxSourceScriptParser`、`LxCompatProtocol`、`LxSourcePluginStore`（`InstalledLxSource`）、`MusicSourcePluginStore`（`InstalledMusicSource`）、`SourceImportPage`（3 tab：音源 / 配置音源 / 下载管理）、`MusicSourceRuntimeService`（LX 执行）。

**结论：运行时不要移植。只移植「云端同步 +（可选）社区音源广场」。**

**(A) 云端音源同步（D1）**

- 新建 `module/online/.../sync/CloudSourceSyncClient.kt`，对接 `POST /api/user/auth`、`/api/user/sources/{backup,restore,delete}`、`/api/user/data/clear`（契约见 `docs/cloud-sync-contract.md`）。
- **关键差异（必须处理）**：
  - OrBit 的 `SourceScriptItem` **内嵌脚本正文** `scriptContent`，整体存 prefs；
  - RawS 的 `InstalledLxSource` **只存脚本文件路径 + sha256**（`scriptPath` / `scriptSha256`）。
  → 上传时必须**读取脚本文件内容并内联进 JSON**，否则云端还原后拿不到脚本。
  → 定义同步 DTO `CloudSourceEntry`，字段取并集：`id, name, version, author, description, sourceUrl, scriptContent, scriptSha256, enabled, isPrimary, supportPlatforms, installedAtMs, updatedAtMs, origin, methods`。
  → 还原时：写回脚本文件到插件目录 → 计算 sha256 校验 → 走**既有安装 API**（`LxSourcePluginStore` / `MusicSourcePluginStore`）注册，**不要绕过**。
  → **兼容 OrBit 备份**：有 `scriptContent` 无 `scriptPath` → 按内联脚本安装；只有路径没有正文 → 明确报错「该备份不完整」，不静默失败。
- UI：在音源门户 `SourceImportPage` 新增 tab「云同步」（追加 `SourcePortalTab.CloudSync`；**注意 `SourcePortalBottomNavigation` 的 tab 数、图标映射、`SourcePortalTabIcon` 要同步加**），或在设置里加「云同步」入口。
  内容：服务器地址输入（**用户自填，不内嵌 URL**）、账号登录、上传 / 下载 / 删除 / 清空、上次备份时间、备份条目列表。

**(B) 社区音源广场（可选，P3）**

- 后端已有 `GET /api/sources`（列 `is_active=1` 的脚本元数据）与 `POST /api/sources`（发布/更新）。
- **注意：这两个接口当前完全无鉴权**（安全隐患）。使用前登记 §9 风险；若开放发布，必须先在 Worker 加鉴权 —— 那属于「改后端」，需要用户批准。
- UI：音源门户新增「社区音源」列表 + 一键安装（下载 `script_url` → 走既有导入流程）。默认关闭。

**(C) 不做的事**

- ❌ 不移植 QuickJS 绑定与 `libquickjs-android-wrapper.so`（RawS 已有等价运行时）。
- ❌ 不移植 `user-api-preload.js`。
- ❌ 不移植 `OnlineAudioSourceManager`。

### 6.4 频谱可视化

**RawS 已有**：
- 数据源：`module/player/.../dsp/RealtimeSpectrumPipeline.kt` + `NativeStereoSpectrumAnalyzer.kt`（`BAND_COUNT = OUTPUT_SIZE = 112`，加载 `librawscoreservice`），由 `app/.../MainActivityVisualizerCoordinator.kt` 消费：经 `PlayerController.onPcmWaveformFrame` 拿 PCM，16 ms 帧 / 24 ms 交付。
  **正确的接入点**：`MainActivity.kt` 里 `RealtimeSpectrumPipeline { ... }` 回调写入的 `visualizerSpectrum: MutableState<FloatArray>`（约 L2814），其 `derivedStateOf` 在约 L3322-3336。
  ⚠️ **不要再注册第二个 `setWaveformConsumerActive(true)` 消费者** —— 管线是共享的、由 `MainActivityVisualizerCoordinator.updateRuntime()` 统一开关。
  ⚠️ **不要使用 `android.media.audiofx.Visualizer`**（全仓 0 使用），也不要新写 FFT。
- 渲染：`core/ui/.../widget/player/ComposeAudioVisualizer.kt`（约 1140 行）。`AudioVisualizerStyle{Spectrum(0)…Ripple(5)}`、`AudioVisualizerParticleColorMode{White, Rainbow}`、`LocalAudioVisualizerStyle(-Change)`、`DisplaySpectrumMotion`（attack 62 s⁻¹ / release 28 或 8）、`Modifier.audioVisualizerFrame(...)`（Choreographer 驱动）。
  统一入口 `SharedRealtimeVisualizerOverlay(style, spectrum, spectrumState, visible, isPlaying, modifier)`。
  **注意 `spectrum` 与 `spectrumState: State<FloatArray>` 双参数**：传后者可让绘制节点直接读最新帧而**不触发整页重组**，性能敏感场景必须用。
- 偏好：`AppPreferences.UI.isAudioVisualizerEnabled` / `.audioVisualizerStyle`（**Int 型**）/ `.audioVisualizerHapticsEnabled` / `.audioVisualizerParticleColorMode`；跨 Activity 刷新用广播 `ACTION_AUDIO_VISUALIZER_SETTING_CHANGED`；触感在 `app/.../AudioVisualizerHapticController.kt`。
- **UI 归属（重要，别找错文件）**：`PlayerInterfaceScreen.kt` 只管**启用开关 / 权限门 / 粒子配色模式 / 触感 / 波形颜色**；**形态选择器不在设置页，而在播放器页** —— `MainActivity.kt` 读出初值 → 传给 `ComposePlayerContainer` → 切换时写回 → 返回时重读；参数在 `ComposePlayerContainer.kt`（含 `LocalAudioVisualizerStyle provides ...`）。

**要移植的内容**

1. **5 个 OpenGL 形态**：`TimeTunnel`、`Octgrams`、`SoundCity`、`FractalGalaxy`、`QuantumVortex`。
   - 目录：`core/ui/src/main/kotlin/com/rawsmusic/core/ui/widget/player/gl/`
   - 每个：`XxxGLRenderer : GLSurfaceView.Renderer`（**数学照搬**）+ 一个 `AndroidView { GLSurfaceView }` 的 Compose 包装（`setEGLContextClientVersion(2/3)`、`RENDERMODE_CONTINUOUSLY`、页面不可见即 `onPause()`）。
   - 输入：`FloatArray` magnitudes（来自 `RealtimeSpectrumPipeline`，经 `MainActivityVisualizerCoordinator` 的 `setSpectrum` 状态传入）。**不要**引入 OrBit 的 `AudioVisualizerManager`。
2. **Canvas 形态**：`BarsWithPeaks`（峰值帽 + 重力回弹）、`AuroraMountain`、`MirroredBars`。
   - 移植物理参数：attack `0.82f` / decay `0.68f`；低于 `0.02f` 归零；峰值保持 5 帧；重力增量 `0.0035f`；峰值不小于柱高。
   - 对数分频（`exp` 等分 + `hypot(re, im)` 取模 + 高频提升 `1 + (b/count)*1.8`）与静音门限 `totalEnergy > 0.010f` 作为**风格参数**；**优先复用 RawS 已有的频点输出**，不要新造数据源。
3. **配色方案**：新增 `VisualizerColorScheme{FOLLOW_BACKGROUND, NEON_CYAN_PURPLE, FIRE_AMBER, ELECTRIC_GREEN, CUSTOM}`（primary / secondary / peak 三色），存 `AppPreferences.UI`。
4. **视觉参数体系**：柱宽 `barWidthDp`、柱透明度 `barAlpha`、描边（宽 / 色 / 透明度 / 仅描边）、单色模式 `singleColor`、自定义双色、峰值衰减开关 `peakDecayEnabled`、背景亮/暗色。全部进 `AppPreferences.UI` 的 `Visualizer` 子 object。
5. **全屏最大化可视化**：`AppPreferences.UI.isVisualizerMaximized` + 播放器页全屏覆盖层（点按退出、返回键处理、切换形态）。
6. **性能**：帧率上限沿用现有 `UI_DELIVERY_PERIOD_MS = 24`；GL 形态需限制渲染分辨率（不要 4K 全屏跑满）。
7. **权限**：**不需要 `RECORD_AUDIO`**（RawS 走播放器内部 PCM，而非 `android.media.audiofx.Visualizer`）。**不要**为可视化申请录音权限。

**枚举兼容性（重要）**：`AudioVisualizerStyle` 是 Int 持久化（`AppPreferences.UI.audioVisualizerStyle`）。
→ 新值**只能追加**：`BarsWithPeaks=6, AuroraMountain=7, MirroredBars=8, TimeTunnel=9, Octgrams=10, SoundCity=11, FractalGalaxy=12, QuantumVortex=13`。
→ **严禁重排或复用现有 0..5**，否则老用户偏好会错位。`from(value)` 的兜底保持 `Spectrum`。

**UI**：
- **形态选择器必须扩在播放器页**（`MainActivity.kt` 的 `:3483-3494` 一带 + `ComposePlayerContainer.kt` 的 `AudioVisualizerStyle` 参数），因为现有形态切换就在那里。
- 配色选择器 + 参数滑块：放 `app/.../ui/settings/PlayerInterfaceScreen.kt`（与现有可视化开关、粒子配色、触感同处），或新建「可视化」子页（`NavScene.VISUALIZER_SETTINGS` id 顺延 + `VisualizerSettingsActivity.kt` / `VisualizerSettingsScreen.kt`，外壳照抄 `AudioEffectsActivity` / `AudioEffectsScreen`）。
- 设置页内嵌小尺寸实时预览（复用同一渲染器，可见性控制渲染启停）。
- 播放器页：形态切换入口 + 全屏最大化。

### 6.5 云端同步统一接入（D1）

- **一个客户端**：`module/online/.../sync/CloudSyncClient.kt`
  - 统一 OkHttp 实例（RawS 风格自建，不引入 DI）
  - 统一响应模型与错误映射：`CloudSyncResult<T> = Success(data, message) | Failure(message, code)`
  - 用户配置（服务器地址 / 用户名 / 密码 / 上次备份时间）存 `AppPreferences.CloudSync`（MMKV），**密码加密存储**（Keystore + AES-GCM，同 §6.2 D）
  - **服务器地址必须用户自填**，占位提示 `https://<worker>.<subdomain>.workers.dev`
- **两个业务封装**：`CloudPlaylistSyncClient`（歌单/喜欢/不喜欢/分组/在线歌单收藏）、`CloudSourceSyncClient`（音源）。
- **UI**：新建 `NavScene.CLOUD_SYNC` 设置子页，**骨架照抄 `app/.../ui/webdav/WebDavPage.kt` + `ui/settings/WebDavBackupScreen.kt`**（账号区 + 地址区 + 动作区 + 状态区）。
  - 上传模式：增量合并（`mode="merge"`）/ 全量覆盖（`mode="overwrite"`）
  - 选择性上传 / 下载（本地歌单多选、在线歌单收藏多选）
  - 危险操作（清空云端所有数据）必须二次确认
- **序列化**：DTO 用 Gson，**字段名与 `docs/cloud-sync-contract.md` §4 的 JSON 契约逐字一致**。
- **喜欢/不喜欢**必须沿用系统歌单名 `我喜欢的音乐` / `不喜欢的音乐`（`groupName = "系统"`）+ 每首歌 `isFavorite` / `isDisliked`，以保证与 OrBit 跨端互认。
- **同步不是实时**：手动触发 + 显示 `updated_at`。不要做后台自动同步（RawS 没有相应基建，且会与备份功能冲突）。

---

