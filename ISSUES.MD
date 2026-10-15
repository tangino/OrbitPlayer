# 移植缺陷与优化清单 (ISSUES.MD)

> 本文档记录 OrbitPlayer 核心功能迁移至 RawS-Music 过程中发现的缺陷、未完备功能及体验差距，用于指导后续针对性修复与补齐。

---

## 缺陷与需求总览

| 编号 | 类别 | 严重级别 | 标题 | 核心受影响模块 |
| :--- | :--- | :--- | :--- | :--- |
| **ISSUE-01** | 崩溃缺陷 | **Blocker (严重)** | 播放状态下进入音效设置或拖动进度条导致闪退 | `module:player`, `app/settings` |
| **ISSUE-02** | 逻辑缺陷 | **Normal (一般)** | 歌单页重复渲染两个“我喜欢的音乐”歌单 | `app/playlist` |
| **ISSUE-03** | 功能缺失/缺陷 | **Major (重要)** | 洛雪音源同步入口缺乏引导、平台扫码无二维码且缺失 Cookie 登录 | `module:online`, `app/settings` |
| **ISSUE-04** | 交互倒退 | **Major (重要)** | 歌单云同步缺少细粒度勾选、无云端备份预览还原能力 | `module:online`, `app/settings` |
| **ISSUE-05** | 功能未串联 | **Major (重要)** | 混合歌单（本地+网络）未完全打通（缺少UI统计、添加入口与播放队列支持） | `module:data`, `module:player`, `app` |
| **ISSUE-06** | 视觉缺陷 | **Normal (一般)** | 网络歌单与收藏歌单封面无法获取并显示为空白 | `module:data`, `core:ui`, `app` |
| **ISSUE-07** | 体验倒退 | **Normal (一般)** | 网络歌曲播放缺少寻源中状态动效与跨平台搜救转换反馈 | `core:ui`, `app/player` |
| **ISSUE-08** | 逻辑缺陷 | **Major (重要)** | 网络歌单播放时仅起播单曲，播放列表丢失整单上下文 | `core:ui`, `module:player` |

---

## 详细问题分析与定位

### ISSUE-01：播放状态下进入音效设置或拖动进度条导致闪退

- **现象描述**：
  在歌曲正在播放（Playing）状态下，点击进入音效设置页面（`AudioEffectsActivity` 或内嵌 `NavScene.AudioEffects`）会直接引发 App 闪退崩溃；在全屏播放页中拖动进度条（`seekTo`）同样会出现瞬间闪退。若在暂停或未播放状态下进入音效页面则不会闪退。
- **根本原因排查与定位**：
  1. 音效设置页面在初始化（`onCreate` / 渲染）时会强制调用 `playerController?.ensurePEQConnected()`、`ensureGraphicEQConnected()`、`ensureExperimentalGainConnected()` 等一系列底层初始化方法，刷新并绑定 `NativeDSPEngine` 原生指针。
  2. 播放状态下，后台音频解码线程正在通过 FFmpeg / OpenSL / AudioTrack 高频循环处理与灌注 PCM 数据，此时进入音效页面或拖动进度条触发 `dspEngine?.resetReverb()` 及 `RealtimePlaybackPcmProcessorRegistry.reset("seek_to")`，原生 C/C++ 共享上下文在未加互斥锁的情况下被并发修改或重置，极易触发底层空指针解引用或野指针访问（SIGSEGV / Native Crash）。
  3. 由于 P0 阶段针对预编译 `.so` 库采取了直接打包策略（关闭了 native 源码编译），JNI 接口层若存在局部引用泄漏、线程生命周期竞争或跨线程调用缺失保护，会直接导致进程终止崩溃。
- **修复目标**：
  - 在 `FfmpegAudioPlayer`、`PlayerController` 及 `NativeDSPEngine` 的 seek、reset 与 ensureConnected 之间补齐并发同步互斥保护；
  - 完善 `AudioEffectsActivity` 与 `LiquidGlassAudioEffectsScreen` 控制器绑定的异常捕获与安全降级逻辑，杜绝播放中 UI 与音频线程竞争造成的进程崩溃。

---

### ISSUE-02：歌单页重复渲染两个“我喜欢的音乐”歌单

- **现象描述**：
  进入歌单列表主页时，页面上方出现了一个“我喜欢的音乐”卡片，下方默认分组列表中又出现了一个同名的“我喜欢的音乐”歌单卡片。
- **根本原因排查与定位**：
  - 定位文件：`RawS-Music/app/src/main/kotlin/com/rawsmusic/ui/playlist/PlaylistScreen.kt`。
  - 在该文件中，第 136-140 行构建了系统伪歌单 `attitudePlaylists`（包含 `ATTITUDE_FAVORITES_PLAYLIST_ID`），并在第 148-150 行定义了过滤逻辑：
    `val displayedPlaylists = remember(attitudePlaylists, playlists) { attitudePlaylists + playlists.filterNot { it.isFavorites } }`
  - 但是在第 179 行调用 `buildPlaylistGridPlan(...)` 时，传递给网格布局构建器的参数却是未经任何过滤的原始 `playlists`：
    `buildPlaylistGridPlan(attitudePlaylists = attitudePlaylists, playlists = playlists, ...)`
  - 导致系统底层的真实收藏歌单（`id == "favorites"`）未被排除，被当作普通歌单在默认分组中再次渲染，最终界面上出现两个一模一样的“我喜欢的音乐”。
- **修复目标**：
  - 将 `buildPlaylistGridPlan` 的入参修正为过滤掉 `it.isFavorites` 后的歌单列表，确保歌单页面顶部仅保留唯一的系统级“我喜欢的音乐”入口卡片。

---

### ISSUE-03：洛雪音源同步入口缺乏引导、平台扫码无二维码且缺失 Cookie 登录

- **现象描述**：
  1. 用户在歌单同步界面找不到洛雪音源同步的入口；
  2. QQ 音乐和酷狗音乐在点击账号同步扫码时，弹窗持续处于“正在加载”或失败，未能渲染出登录二维码；
  3. 缺乏 OrbitPlayer 原有的手动填入 Cookie 登录功能。
- **根本原因排查与定位**：
  1. **入口孤立**：洛雪音源同步虽然在 P3-04 中实现于“音源导入”门户的第 4 个 Tab，但在统一“云同步”设置页和歌单管理页面中缺乏交叉导航入口与直观跳转，用户习惯于在数据同步区域寻找所有云同步选项；
  2. **扫码无二维码**：
     - QQ 音乐：`QQMusicAuthService` 请求 `ptqrshow` 接口时需正确处理 Set-Cookie 中的 `qrsig` 并返回二进制字节流，若网络请求因客户端超时、OkHttp 未允许重定向或安全策略拦截导致抛出异常，进入 catch 块后返回 null；
     - 酷狗音乐：`KugouMusicAuthService` 调用 `login-user.kugou.com/v2/qrcode` 接口若接口响应结构变化或签名失效，无法解析出有效 Base64 图片，且 `QrCodeGenerator` 缺少鲁棒的容错展示；
  3. **缺失 Cookie 登录**：RawS-Music 的 `PlatformAccountScreen.kt` 仅有扫码弹窗，未移植 OrbitPlayer `PlatformLoginDialog.kt` 中的双 Tab 结构（Tab 0 扫码 / Tab 1 填入 Cookie）以及对 QQ（`uin`, `skey`, `pskey`）和酷狗（`kg_mid`, `token`, `userid`）Cookie 字段的解析与验证逻辑。
- **修复目标**：
  - 修复 QQ 与酷狗二维码网络获取与位图生成逻辑，输出详细日志排查拦截原因；
  - 完整迁移 OrbitPlayer 的 Cookie 登录功能（包含输入弹窗、格式化解析器、登录有效性校验）；
  - 在歌单同步页与统一云同步页中增加音源云同步的快速跳转入口。

---

### ISSUE-04：歌单云同步缺少细粒度勾选、无云端备份预览还原能力

- **现象描述**：
  RawS-Music 当前的歌单云同步仅支持一键全量上传与一键全量还原，用户无法在上传时勾选部分本地歌单或在线收藏，也无法在下载前查看云端备份包含的歌单列表并选择性还原。
- **根本原因排查与定位**：
  - 现行实现：`CloudSyncScreen.kt` 仅提供账号配置和“上传备份 / 还原备份”两个大按钮，底层直接执行全量序列化与反序列化；
  - 对比 OrbitPlayer：OrbitPlayer 的 `CloudPlaylistSyncDialog.kt` 拥有两级 Tab（上传备份 / 从云端下载）：
    - **上传时**：支持多选勾选本地歌单、在线收藏歌单、是否上传“我喜欢”与“不喜欢”，并支持增量合并与全量覆盖；
    - **下载前**：先调用接口拉取云端备份摘要（Preview），在列表中展示云端歌单名、歌曲总数、在线歌单明细，由用户勾选需要同步的具体项目，再确认执行还原。
- **修复目标**：
  - 重构 `CloudSyncScreen.kt` 与对应契约逻辑，恢复细粒度项目多选（本地歌单/在线收藏/喜好偏好）与云端备份预览多选还原能力。

---

### ISSUE-05：混合歌单（本地+网络）未完全打通

- **现象描述**：
  用户不明确 OrbitPlayer 的混合歌单是否已迁移至 RawS-Music，自建歌单中无法直观区分本地与网络歌曲，网络歌曲无法顺利加入自建歌单并无缝播放。
- **根本原因排查与定位**：
  - **已迁移部分**：数据层在 `core:common` 的 `UserPlaylist.kt` 中定义了 `onlineUserPlaylistSong`、`isOnlineEntry()` 及 `online://` 协议，在云同步 JSON 与 M3U8 文件编解码中支持该数据格式；
  - **缺失部分**：
    1. **UI 层面**：歌单详情页（`PlaylistScreen.kt` / `PlaylistDetailPage.kt`）直接调用 `resolveSongs`，由于该方法内部使用 `File(it.path).exists()` 过滤本地文件，导致在线虚拟曲目被全部丢弃，无法展示“共 X 首 (本地 A · 网络 B)”等混合统计信息；
    2. **交互层面**：在线搜索与网络歌单详情中缺少“添加到歌单”动作，无法将在线单曲写入本地 `UserPlaylist`；
    3. **播放层面**：混合列表起播时，当前播放器未实现本地文件（FFmpeg 解码）与网络虚拟曲目（实时寻源起播）之间的自动交替驱动。
- **修复目标**：
  - 在歌单详情 UI 中使用 `playlistEntries` 替代 `resolveSongs`，完整显示混合曲目列表与构成明细；
  - 在在线音乐各界面补齐“添加到歌单”弹窗与业务落盘；
  - 打通播放控制器混合列表的自动跨源解析与连续播放。

---

### ISSUE-06：网络歌单与收藏歌单封面无法获取并显示为空白

- **现象描述**：
  在本地歌单列表或收藏列表中，网络歌单无法获取并展示封面图，卡片只显示空白占位或默认图标，而在 OrbitPlayer 中均能正常显示。
- **根本原因排查与定位**：
  1. **实体字段缺失**：RawS-Music 的 `UserPlaylist` 实体中缺少 `coverArtUri: String?` 字段，无法保存歌单专属的网络封面 URL；
  2. **在线曲目封配置空**：在 `UserPlaylist.kt` 中，`onlineUserPlaylistSong` 将 `albumArtPath` 字段强制留空，避免被本地图片解码器误读取为本地路径；
  3. **拼图逻辑失效**：`PlaylistScreen.kt` 中的封面渲染依赖 `buildPlaylistCollageSongs(songs)`，由于 `resolveSongs` 对纯网络歌单返回空列表，拼图函数找不到任何歌曲，导致无法生成任何封面；
  4. **BitmapProvider 限制**：部分在线平台的图片 CDN（如特定没有后缀名或包含重定向防盗链的 URL）在通过 `BitmapProvider` 过滤时被误拦截或解析失败。
- **修复目标**：
  - 扩展 `UserPlaylist` 实体支持可选的 `coverUrl`，导入或收藏网络歌单时持久化该封面；
  - 改造 `buildPlaylistCollageSongs`，当歌曲为在线曲目时从在线元数据中提取远程图片 URL 供 `BitmapImage` 加载。

---

### ISSUE-07：网络歌曲播放缺少寻源中状态动效与跨平台搜救转换反馈

- **现象描述**：
  播放网络歌曲时，界面没有清晰的“正在寻源 / 加载中”反馈；当触发跨平台多源搜救时，缺乏像 OrbitPlayer 那样的来源平台转换标识（例如“网易云 ➔ QQ音乐”），且无法直观查看和切换当前音质。
- **根本原因排查与定位**：
  - 在 OrbitPlayer 中，`NowPlayingScreen.kt` 密切监听歌曲的 `originalPlatform` 与 `sourcePlatform`，在发生搜救降级时动态展示如 `原源 ➔ 搜救源` 的彩色徽标，并提供点击弹窗切换音质；同时在寻源过程中播放器底栏和进度条有明确的加载动效；
  - 在 RawS-Music 中，`OnlinePlaybackLauncher` 寻源完成后直接调用 `MusicSourcePlaybackController.playResolved`，没有将原始平台（`originalPlatform`）、最终命中平台（`sourcePlatform`）以及寻源中的中间状态（`Resolving`、`Searching`）反馈给全局播放 UI。
- **修复目标**：
  - 在播放状态模型中补齐 `originalPlatform` 与 `sourcePlatform`，并在全屏播放页与底栏 MiniBar 增加平台徽标、平台转换标签（`A ➔ B`）与音质选择弹窗；
  - 在寻源与缓冲过程中提供优雅的转圈/加载动效反馈。

---

### ISSUE-08：网络歌单播放时仅起播单曲，播放列表丢失整单上下文

- **现象描述**：
  在网络歌单详情页点击单曲播放或点击“播放全部”后，打开正在播放列表，发现列表中只有当前正在播放的这一首歌曲，歌单中的其余歌曲全部丢失，无法顺序切歌或列表循环。
- **根本原因排查与定位**：
  - 定位文件：`RawS-Music/core/ui/src/main/kotlin/com/rawsmusic/core/ui/scene/pages/OnlinePlazaDetailPage.kt`。
  - 第 207 行：“播放全部”仅执行了 `onPlayAll = { songs.firstOrNull()?.let(::playSong) }`，即只播放第一首；
  - 第 222 行：单曲点击同样只调用了 `playSong(song)`；
  - `playSong` 仅将单一的 `OnlineSongItem` 传递给 `OnlinePlaybackLauncher.play(...)`，完全没有将当前歌单的全部歌曲构造成播放队列交由 `PlayerController` 或队列协调器管理。
- **修复目标**：
  - 实现网络歌单整单起播接口（`playOnlineSongList(songs, startIndex)`），在起播目标歌曲的同时将歌单所有曲目载入播放队列，支持自动顺序播放、随机播放与切歌。
