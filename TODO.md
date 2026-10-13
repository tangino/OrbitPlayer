# TODO — 功能移植任务清单与进度

> 本文件是**移植进度的唯一来源**。规范与设计见 [`AGENTS.MD`](AGENTS.MD)。
> 状态图例：`[ ]` 未开始 · `[~]` 进行中 · `[x]` 完成 · `[!]` 阻塞 · `[-]` 取消/不做
> 每个任务 = 一个可独立编译通过的 commit。开工前先读 `AGENTS.MD` §10.2 的流程。

**当前阶段**：`P0 环境与构建准备` —— **已完成**。环境已可直接构建，见 `AGENTS.MD` §2.2。
**当前阻塞**：无。环境/构建阻塞已全部解决。

---

## ✅ 已解决的环境与构建阻塞（原「需用户确认」）

| 阻塞 | 解决方式 |
| --- | --- |
| `gradle.properties` 硬编码失效 JDK 路径 `D:\ASTUDIO\jbr` | **已删除该行**并加注释；JDK 21 改配在**用户级** `~/.gradle/gradle.properties`，仓库保持可移植。本机 JDK 21 = `D:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`（注意 `Android_Studio\jbr` 是 **JDK 25，不可用**） |
| `local.properties` 缺失 | **已创建**，`sdk.dir=E\:\softwares\Android\sdk` |
| catalog 缺 `focus-api` / `shizuku` 条目 | **上游 git pull 已补齐**（`gradle/libs.versions.toml`） |
| 缺 `librawsmusic_ffmpeg.so` | **用户已就位**，且 `jniLibs/arm64-v8a/` 现在有官方 1.0.13 的**完整预编译 `.so` 集** |
| NDK 27.0.12077973 未安装 | **AGP 8.10.0 自动安装** |
| 原生源码缺失导致 CMake 失败（**新发现**） | 公开仓库缺 **70 个 USB 原生源文件** → **已注释掉 `app/build.gradle.kts` 的两处 `externalNativeBuild`**，改为直接打包预编译 `.so`。代价：原生代码冻结（本移植不需要改原生）。详见 `AGENTS.MD` §9 D13 |

**验证记录**：`.\gradlew.bat :app:compileReleaseKotlin --console=plain` → **BUILD SUCCESSFUL**（约 10 分钟）。
`.\gradlew.bat assembleRelease --console=plain` → 已通过原生库合并、符号剥离并进入 R8 压缩阶段（详见 P0-05 记录）。

---

## 进度总览

| 阶段 | 内容 | 任务数 | 完成 | 状态 |
| --- | --- | --- | --- | --- |
| **P0** | 环境与构建准备（解阻塞） | 6 | 5 | ✅ 基本完成（P0-06 待真机） |
| **P1** | 在线歌单广场（含歌手库 + 全网搜歌） | 14 | 0 | 未开始 |
| **P2** | 歌单管理 | 13 | 0 | 未开始 |
| **P3** | 洛雪歌源同步 | 6 | 0 | 未开始 |
| **P4** | 频谱可视化 | 8 | 0 | 未开始 |
| **P5** | 云同步统一入口 + 收尾回归 | 5 | 0 | 未开始 |
| | **合计** | **52** | **0** | |

---

## P0 环境与构建准备（解阻塞）—— ✅ 已完成

> 目标：让 RawS-Music 能在本机编过。**已达成**，四个阻塞的解决方式见上文「已解决的环境与构建阻塞」。

- [x] **P0-01 切换分支** — 在 `RawS-Music` 上创建 `feat/orbit-port`（不要直接改 `main`）。
  - 验收：`git branch --show-current` 输出 `feat/orbit-port`。
  - 状态：待执行（当前仍在 `main`；做功能改动前先切）。

- [x] **P0-02 环境适配（独立 commit）** — 删除 `gradle.properties` 里失效的 `org.gradle.java.home`；创建 `local.properties`；JDK 21 配到用户级 `~/.gradle/gradle.properties`。
  - 验收：`.\gradlew.bat --version` 正常输出，Daemon JVM = JDK 21。**已实测通过。**
  - commit：`chore(build): 适配本机 SDK/JDK 路径`

- [x] **P0-03 补 catalog 条目** — **上游 git pull 已自带**（`libs.versions.toml` 已有 `focus-api` / `shizuku-api` / `shizuku-provider`），无需我们改。
  - 验收：`:app` 配置阶段不再报 unresolved reference。**已通过。**

- [x] **P0-04 补齐预编译原生库** — `jniLibs/arm64-v8a/` 已就位官方 1.0.13 的**完整 `.so` 集**（`librawscoreservice` / `librawsmusic_ffmpeg{,_live}` / `librawsmusic_audio_transcode` / `librawsmusic_sacd` / 4 个 FFmpeg 拆分库 + AAR 自带库）。**路线 A/B 均不再需要**，`docs/ffmpeg-prebuilt.md` 只作排查记录保留。

- [x] **P0-04c（新发现，已完成）关闭原生源码编译** — 公开仓库**缺 70 个 USB 原生源文件**（`CMakeLists.txt` 引用 144 个 `.cpp`，实际仅 74 个），CMake 在 `add_library(rawsmusic_usb ...)` 处必然失败。
  - 处理：注释掉 `app/build.gradle.kts` 的两处 `externalNativeBuild`，改为直接打包 `jniLibs/` 的预编译 `.so`。
  - 代价：**原生代码冻结**（`app/src/main/cpp/` 改动不生效）。4 个移植功能都无需改原生。见 `AGENTS.MD` §9 D13。
  - ⚠️ 恢复原生构建的前提：补齐缺失的 USB 源文件。

- [x] **P0-05 基线构建验证** — 已跑通。
  - 命令：`.\gradlew.bat :app:compileReleaseKotlin --console=plain` → **BUILD SUCCESSFUL**（约 10 分钟）。
  - 命令：`.\gradlew.bat assembleRelease --console=plain` → 已通过 `mergeReleaseNativeLibs`、`stripReleaseDebugSymbols`，进入 R8 压缩。**无重复 `.so` 打包错误**（AGP 对 `libonnxruntime4j_jni.so` 只有 warning，自动择一）。
  - ⚠️ 已知噪声（**不是 bug，不要修**）：R8 大量 `kotlin metadata` 解析警告（Kotlin 2.3.20 比 R8 新）；`compileSdk 37` 超出 AGP 8.10.0 测试范围（`gradle.properties` 已用 `android.suppressUnsupportedCompileSdk=37` 抑制）。
  - 产出：`app/build/outputs/apk/release/app-release.apk`。

- [ ] **P0-06 基线冒烟回归** — 装机验证既有功能，记录改动前状态作为回归基线。
  - 覆盖：本地扫描 / 播放 / 歌单增删 / 音源导入（LX + MusicFree）/ 实时可视化开关 / WebDAV 备份页可打开 / USB DAC（预编译版应可用）。
  - 产出：把结果写进本文件末尾「基线记录」。
  - 依赖：P0-05 产出的 APK + 一台 arm64 真机。

---

## P1 在线歌单广场（含歌手库 + 全网搜歌）

> 设计见 `AGENTS.MD` §6.1；映射与端点见 `docs/porting-reference.md`。

- [x] **P1-01 新建 `:module:online` 模块骨架** — ✅ 已完成。`settings.gradle.kts` 已加 `include(":module:online")`；`module/online/build.gradle.kts`（namespace `com.rawsmusic.module.online`，依赖 core:common / module:data / okhttp / gson / zxing / coroutines）；catalog 新增 `zxing = "3.5.3"` + `zxing-core`。
  - 验收：`.\gradlew.bat :module:online:compileReleaseKotlin` 通过 ✅（1m29s）
  - 说明：`:core:ui` 与 `:app` 的依赖**推迟到 P1-13** 接入，避免留下未使用的依赖。

- [x] **P1-02 移植在线模型** — ✅ 已完成，落在 `module/online/.../model/OnlineMusicModels.kt`（仅在线功能使用，不进 `core:common`）。`OnlinePlatform` 额外加了 `fromId()` 宽松查找。

- [x] **P1-03 定义 `OnlinePlatformSource` 接口** — ✅ 已完成，`module/online/.../source/OnlinePlatformSource.kt`，保留歌手/专辑方法的默认空实现。
  - 依赖：P1-02

- [x] **P1-04 移植网易云适配器** — ✅ 已完成（`source/netease/NeteaseMusicSource.kt` + `NeteaseCrypto.kt`，逐字移植）。
  - 待真机验证：tags / 分类歌单 / 榜单 / 歌单详情。

- [x] **P1-05 移植 QQ 音乐适配器** — ✅ 已完成（`source/qq/QQMusicSource.kt`）。

- [x] **P1-06 移植酷狗适配器** — ✅ 已完成（`source/kugou/KugouMusicSource.kt`）。它调用 `KugouMusicAuthService.getPlaylistTracks`，因此连带移植了 auth 层（见 P2-08）。

- [x] **P1-07 移植酷我适配器** — ✅ 已完成（`source/kuwo/KuwoMusicSource.kt`）。

- [x] **P1-08 移植咪咕适配器** — ✅ 已完成（`source/migu/MiguMusicSource.kt`）。

> ⚠️ 5 个适配器都是**逐字移植**：只改包名（`com.orbit.music.data.online` → `com.rawsmusic.module.online`）、接口名（`IOnlineMusicSource` → `OnlinePlatformSource`）和日志（`android.util.Log` → `AppLogger`，共 24 处）。**端点、请求参数、Referer、fallback 链一律未动**。`:module:online:compileReleaseKotlin` 通过。
> 适配器里的中文字符串是**平台 API 参数值**（如酷我的分类标签 `"全部"/"热歌"`）与数据层标签，属外部契约，不是 UI 文案，不要抽成 stringResource。

- [x] **P1-09 `OnlineMusicRepository`（单平台 + 缓存）** — ✅ 已完成（`repository/OnlineMusicRepository.kt`）：平台映射表与 `switchPlatform`、tags/歌单/榜单/详情/搜索、`LruCache` 详情缓存（50 条）+ 标签缓存。
  - ⚠️ 唯一改动：`bindAuthManager(PlatformAccountManager)` → `bindAuthProviders(cookieProvider, accountProvider)` 函数注入（因 `PlatformAccountManager` 待 P2-08 按 D7 重做）。行为不变。

- [x] **P1-10 全网搜歌（多平台聚合）** — ✅ 已完成（同一文件）：`searchSongsAllPlatforms` 并发 fan-out + `searchSongsAggregated` 轮询合并去重，以及 `searchArtists` / 歌手详情 / 歌手专辑 / `parseLinkOrText`。
  - 依赖：P1-09

- [x] **P1-11a 播放解析算法层（从 `OnlineAudioSourceManager` 摘算法）** — ✅ 已完成（`playback/` 三个文件）：
  - `OnlinePlaybackMatcher`：HTML 清洗 / 归一化 / 核心歌名提取 / 特殊版本标签 / 多歌手拆分 / `isMatched`（5 秒容差）/ `calculateMatchScore`（100 分制）。
  - `OnlineQualityLadder`：音质降级阶梯（原先在 OrBit 里重复出现两处，已抽成一处防漂移）。
  - `OnlinePlaybackProbe`：Range/HEAD 探活 + Content-Type / 流长度 / `MediaMetadataRetriever` 时长**四层试听片段拦截**；咪咕两步直链解析。
  - ⚠️ 阈值与 Referer 映射都是反推出来的经验值，**不要凭直觉调整**。

- [ ] **P1-11b 接入 RawS 既有解析链路** — 把 `OnlineSongItem` 转成 `RawSourceMediaItem`，接在 `MusicSourceAudioResolver` 前后作为候选筛选与兜底；**不要替换** RawS 的解析器。
  - 依赖：P1-11a
  - 注意：`OnlinePlaylistViewModel` 不要移植，改成 Store + `StateFlow`。

- [x] **P1-12 Room 建表 + 迁移（6→7，与 P2-01 同一迁移）** — ✅ 已完成（提交 `6c301d0`），详见下方 P1-12 小节。
  - 依赖：P0-05

- [x] **P1-13 在线门户 UI 骨架** — ✅ 已完成（提交 `b402fc2`）。
  - `NavScene.ONLINE_MUSIC(54)` + `ONLINE_PLAYLIST_DETAIL(55)`；`NavSceneLabels` 补两个 `nav_scene_*` 文案（编译期穷尽性检查抓出了这里，已修）。
  - 侧栏入口：`AppSideRailDestination.ONLINE_MUSIC` + `sideRailItems` 条目 + 新图标 `ic_side_rail_online.xml`；`MainActivity` 的 `when (destination)` 加分支 → `navigateTo(ONLINE_MUSIC)`（同样是穷尽性检查抓出来的，已修）。
  - `ComposeNavHost` 挂载 `OnlineMusicPortalPage`；`core:ui` 与 `app` 各自加了 `implementation(project(":module:online"))`。
  - 门户内容：标题栏 + **平台切换 chip 行**（直接驱动 `OnlineMusicRepository.currentPlatform`，是真功能）+ 四个二级 tab + 底部胶囊 tab 栏 + 新图标 `ic_online_{plaza,artist,search,favorite}.xml`。
  - ⚠️ **有意偏差**：未复用音源专用的 `SourcePortal*` 机制（`SourcePortalChrome`/`SourcePortalTab`/`SourcePortalNavigationState`/预测性返回/路由转场，合计 1000+ 行，且 `SourcePortalTab` 是音源专用枚举）。改为自定义 `OnlinePortalTab` + `rememberSaveable`，只复用通用件。**若后续确实需要同等转场打磨，应把该机制泛化而不是复制。**
  - 编译验证：`:core:ui:compileReleaseKotlin` ✅ 与 `:app:compileReleaseKotlin` ✅。
  - 剩余：四个 tab 的内容目前是占位（`online_section_placeholder`），由 P1-14 填入真实数据。

- [x] **P1-12 Room 建表 + 迁移（6→7，与 P2-01 同一迁移）** — ✅ 已完成（提交 `6c301d0`）。
  - 新增 `online_playlist_favorites` / `song_attitude` / `playlist_groups` 三张表 + `user_playlists.group_name` 列；`MIGRATION_6_7` 已注册，`version = 7`。
  - 三个 DAO 全部 `suspend`、零 `Flow`；建表 SQL 常量化照抄上游 `ScanDirectorySchema` 范式。
  - 已核对自动生成的 `module/data/schemas/.../7.json`：`version=7`、**18/18 列**（列名/类型/notNull/defaultValue）与迁移 SQL 一致、三个索引名满足 `index_<表>_<列>`、`group_name` 的 `defaultValue` 在实体/迁移/schema 三处逐字符一致（`'默认'`）。
  - ⚠️ **待真机验证**：迁移**没有在真实 v6 库上跑过**（`module:data` 无 `MigrationTestHelper`，加测试依赖违反「不新增依赖」）。需要在装有 v6 库的设备上覆盖安装一次，确认不抛 schema 校验异常。

- [x] **P1-11b 接入 RawS 既有解析链路** — ✅ 已完成（提交 `b5853f9`）。新增 `OnlineSourceMediaMapper` + `OnlinePlaybackBridge`，**不替换** `MusicSourceAudioResolver`（AGENTS §6.1-5 / §9 D5）。
  - `onlineSongKey = online://<platformId>/<songId>`（与 OrBit 逐字一致 → `song_attitude` 跨端互认）；`onlinePlaylistKey = <platformId>_<remoteId>`（与 `online_playlist_favorites.playlist_key` 一致）。
  - `selectBestCandidate`：先 `isMatched` **硬过滤**（歌名+歌手+5 秒容差）再按 `calculateMatchScore` 排序 —— 防止「歌名像就播了别人的版本」。
  - `resolveFirstPlayable`：逐个候选尝试，resolver 以函数注入（不直接依赖 `:module:data` 实现，保持可测）。
  - ⚠️ **已知集成约束（P1-14 必须处理）**：映射产出的 `sourceId` 是**平台 id**（身份用途），而 `MusicSourceAudioResolver` 是按「用户已安装的音源 id」查表的 → **不能把映射结果直接交给解析器**。正确顺序：先搜用户音源拿候选 → `selectBestCandidate` → 再调既有 resolver → `validateResolved` 探活。端到端接线的落点在 P1-14 的播放按钮处（那里才有真实调用点）。

- [x] **P1-14 广场/歌手库/全网搜歌/收藏 四个 tab 的完整 UI** — ✅ 已完成（提交 `7399138` / `8b06ec2` / `46643a6`）
  - 验收：见 `AGENTS.MD` §8.2 ① 全部 6 项。
  - 依赖：P1-13、P2-01（喜欢/不喜欢落库）
  - **进度**：
    - [x] **① 歌单广场 tab** —— ✅ 提交 `7399138`（`OnlinePlazaTab.kt`）。
    - [x] **② 歌手库 tab** —— ✅ 提交 `8b06ec2`（`OnlineArtistTab.kt`，355 行）：分类 chip + 两列歌手网格 + 分页 + 四态。
    - [x] **③ 全网搜歌 tab** —— ✅ 提交 `8b06ec2`（`OnlineSearchTab.kt`，728 行）：歌名+歌手**双输入**、平台过滤（全部＝并发搜 5 平台并按平台分组，单平台失败隔离）、mm:ss 时长、分页、内存态搜索历史。
    - [x] **④ 我的收藏 tab** —— ✅ 提交 `8b06ec2`（`OnlineFavoritesTab.kt`，242 行）+ 数据层提交 `e8fca36`（`OnlinePlaylistFavoriteStore`）：分组 chip + 取消收藏。
    - [x] **⑤ 歌单详情页** —— ✅ 提交 `46643a6`（`OnlinePlazaDetailPage.kt`）：头部（封面/标题/创建者/播放量/曲目数）+ 收藏切换 + 曲目列表（序号/歌名/歌手·专辑/mm:ss）+ 四态；`ComposeNavHost` 已注册 `ONLINE_PLAYLIST_DETAIL` 分支读 `state.currentArgument`；广场卡片点击已接到本页。
      - 导航参数编码：`onlinePlaylistDetailArgument(platform, playlistId)` = `"<platformId>|<Uri.encode(playlistId)>"`。
      - **`isDetail()` 成员是有意的**：它决定 `SceneTransitionEngine` 在转场期间是否保留并传递 `currentArgument`，本页依赖该参数；本页没注册共享封面 provider，所以只会退化为普通转场，不会出错。
      - ⚠️ **已知限制**：「播放全部」目前只起播第一首。完整队列续播需要为每首预解析候选（N 次音源检索），代价高，留作后续。
    - [x] **⑥ 播放接线（UI 已接）** —— ✅ 数据层提交 `9136dc0`，UI 接线在 `46643a6`：点歌 / 播放全部 → `OnlinePlaybackLauncher.play` → `Started` / `NoSourceInstalled` / `NoMatchFound` / `Failed` 分别给明确文案（不暴露原始异常）。

> **P1 状态：14 项全部完成**（P1-01…P1-14）。唯一待办是 **P1-12 的迁移真机验证**：需要在装有 v6 库的设备上覆盖安装一次，确认不抛 Room schema 校验异常。
  - **⑥ 的现成基础设施（已确认，不要重造）**：
    - `MusicSourceSearchCoordinator.search(context, sources, lxSources, query, page, type): AggregatedMusicSourceSearch`（`module/data/.../runtime/MusicSourceSearchCoordinator.kt:18`），其 `.items` 已是全部候选；MusicFree 组的 `sourceId` 是**裸 source id**，可被 `MusicSourceAudioResolver` 解析。
    - `MusicSourcePlaybackController.play(context, queue, index)` / `playItem(context, item)`（`:162` / `:183`）。
    - `MusicSourceSearchStore` 已有 `history` / `latest()` / `remember()` / `clearHistory()` —— **搜索历史的持久化其实 RawS 已经做好了**，后续可把 ③ 的内存态历史升级为复用它（不必再动 `SearchHistoryEntity`）。
  - **已建立的约定（后续 tab 照此办理）**：
    - 状态用页面内 `remember` + 单例 Repository 的 `StateFlow`（**不引入 ViewModel**，AGENTS §4.4）；**不要用 `Get-Content` 读含中文的日志/源码**（pwsh 代码页会乱码，但**同时会让行号偏移约 9 行**，用 `read` 工具）。
    - 四态**页面内联**实现（全仓没有共享 Loading/Empty/Error 组件）；错误额外经 `AppNoticeBus.error`。
    - 文案进 `core:ui` 的 `values/`（中）+ `values-en/`（英），key 用 `online_*`。
    - **Miuix `TextButton` 是 `TextButton(text = String, onClick = ...)`**，没有 slot 版本（踩过：`TextButton(onClick=){...}` 会报 `No value passed for parameter 'text'`）。
    - `CircularProgressIndicator` 用 `androidx.compose.material3` 的（全仓 13 处、无 Miuix 等价物）；
    - 封面用 `BitmapImage(key = url, ...)`，其底层 `BitmapProvider` 已支持 http(s) 远程源，**不要引入 Coil**。
    - 网格沿用 `chunked(N)`（决策 D11），**不要 `LazyVerticalGrid`**。
    - ⚠️ **不要用 `.\gradlew.bat --rerun-tasks`**：本工程会炸 `:core:common`（`io.github.proify.lyricon.*` 全部 Unresolved —— KMP 依赖 `:lyric:model` 的 jar 在强制重跑语义下拿不到）。用普通命令或 `:app:compileReleaseKotlin` 即可。
    - ⚠️ **`runCatching` 会吞掉 `CancellationException`**：OrBit 代码大量使用 `runCatching`，逐字移植会把「协程取消」伪装成业务失败（切平台/切分类时闪假错误）。已在仓库层用 `resultOf` 根治（提交 `d1674c3`）；**后续移植/新写的挂起函数一律用 `resultOf` 或显式 rethrow**，不要用裸 `runCatching`。

---

## P2 歌单管理

> 设计见 `AGENTS.MD` §6.2。

- [x] **P2-01 喜欢 / 不喜欢（数据层）** — ✅ 已完成（提交 `69fe4bc`，`module/data/.../repository/SongAttitudeRepository.kt`）。
  - ⚠️ **本轮发现规格有误**：`AGENTS.MD` §3.5 原写「喜欢/不喜欢都不存在」，实测 RawS **已有**完整的「喜欢」机制 —— `FAVORITES_PLAYLIST_ID = "favorites"` 系统歌单（名正好是「我喜欢的音乐」）+ `PlaylistStore.favoriteSongKeys/isFavorite/toggleFavorite`，且已打通**播放器**（`PlayerService`）、**长按菜单**（`SongActionSheetHelper`）、**歌单页**（`PlaylistScreen` 心形/粉色强调/禁止改名删除）、**WebDAV 备份**（`BackupAndroidPersistence`/`BackupArchiveCodec`）。
  - **用户决策（§9 D14）：混合方案** —— 本地曲的喜欢**沿用既有歌单**（不接管，避免两个事实来源让心形状态不一致）；**不喜欢（本地+在线）**与**在线曲的喜欢**走 `song_attitude`。
  - 已实现：`dislikedKeys` / `onlineFavoriteKeys`（**只收 `online://` 前缀**）/ `setDisliked` / `removeDislike` / `clearAllDislikes` / `setOnlineFavorite` / `setOnlineFavorites`（批量）/ `cycleOnline`（`NONE→FAVORITE→DISLIKED→NONE`）。写入后统一 `refresh` 发布 `StateFlow`。
  - 依赖：P1-12

- [ ] **P2-02 喜欢 / 不喜欢（UI）** — **改 `app/.../ui/playlist/PlaylistScreen.kt`（不是 `core/ui/.../PlaylistsPage.kt`，后者是 29 行空壳占位页）**：顶部两个伪歌单卡片（曲目数实时）+ 歌曲长按菜单三项 + 清空不喜欢的二次确认 + 批量喜欢。
  - ⚠️ **按 §9 D14**：本地曲的「喜欢」要调 `PlaylistStore.toggleFavorite(song)`（既有机制），**不要**调 `SongAttitudeRepository`；「不喜欢」与在线曲的「喜欢」才走 `SongAttitudeRepository`。
  - 依赖：P2-01

- [x] **P2-03 歌单分组（数据层）** — ✅ 已完成（提交 `20ed046`，`module/data/.../prefs/PlaylistGroupStore.kt`）。
  - 新增 `UserPlaylistDao.updatePlaylistGroup`（移单个歌单）+ `reassignGroup`（整组改挂）。
  - 关键设计：**「默认」不存进 `playlist_groups` 表**，由 `user_playlists.group_name` 的列默认值兜底 → 它是保留分组（不可创建/改名/删除），读列表时由 `refresh` 统一补在最前，**因此永远不可能出现无归属歌单**。
  - **分组改名与删除分组必须同时改两张表**（`playlist_groups` + `user_playlists.group_name`），用 `withTransaction` 包住；只成功一半会让歌单指向不存在的分组而整批消失。
  - `rename` 只捕获 `NoSuchElementException` 转 `NotFound`，`CancellationException` 自然上抛（**不用 `runCatching`**，避免重蹈吞取消的坑）。
  - 依赖：P1-12

- [ ] **P2-04 歌单分组（UI）** — 歌单页按 `SectionHeader(groupName)` 分区；歌单/分组长按 → 移动分组/改名/删除。
  - 依赖：P2-03、P2-02（同一文件，建议串行）

- [x] **P2-05 先定契约** — ✅ 已完成（勘察结论如下，**动手 P2-06/07 前必读**）。
  - **`song_key` 规则**（权威定义在 `core/common/.../model/UserPlaylist.kt:33`）：`AudioFile.playlistIdentityKey()` =
    有 `path` → `"path|<path 反斜杠转正斜杠并 lowercase>"`；无 `path` → `"media|<id>|<title>|<artist>|<album>|<duration>"`。
    `song_attitude.song_key` **必须**沿用同一函数，否则会出现「歌单里有这首、喜欢状态读不到」的错位。
  - **`UserPlaylistSong`（`UserPlaylist.kt:15`）是「元数据快照」而不是引用**：除了 `key` 之外把 `id/title/artist/album/albumId/duration/path/fileSize/format/sampleRate/bitRate/bitsPerSample/albumArtPath` 全存了一份，并有 `toAudioFile()` 反解。所以即使歌不在曲库里，只要文件还在也能播。
  - ⚠️ **`id` 是 `Long`（非空）** → 在线曲入库必须给一个**合成 Long id**。OrBit 现成算法是 `OnlineAudioSourceManager.generateVirtualSongId(platform, songId): Long`（该文件 `:48`），P2-06 直接照搬这个算法，**不要自己发明编码**（跨端同步要靠它对齐）。
  - 🚧 **在线曲目目前会被静默丢弃 —— 这是 P2-06/07 的关键阻塞点**：`PlaylistStore.resolveSongs`（`:216-227`）只有三层回退：
    ① `byIdentity[stored.key]`（曲库里找）→ ② 按 path 候选匹配 → ③ `stored.toAudioFile().takeIf { File(it.path).exists() }`。
    在线曲既不在曲库、也没有本地文件，**三层全落空 → 该曲目从播放列表里消失**。
    → P2-06 必须补一条分支：`key.startsWith("online://")` 时**不走 `resolveSongs`**，而是交给 `OnlinePlaybackLauncher` 解析播放；UI 侧要么单独维护在线曲目列表，要么把 `resolveSongs` 扩展成能返回「在线曲目占位」。
    **这一步必须先设计好再写导入 UI，否则导入的在线曲目会"看起来成功了但列表里没有"。**
  - **`UserPlaylistDao` 缺 `updateGroup`**：现有方法只有 `upsertPlaylist(s)` / `renamePlaylist` / `touchPlaylist` / `deletePlaylist` / `deleteSong` / `insertSongs` / `upsertSongs` / `replaceAll`。`user_playlists.group_name` 列已在 `MIGRATION_6_7` 加上，但**没有任何 DAO 方法能改它** → P2-03 必须新增 `updateGroup(playlistId, groupName, updatedAt)` + 一个 `PlaylistGroupDao` 的联动事务（改分组名时要同步改其下歌单的 `group_name`，否则歌单会指向不存在的分组）。
  - **`UserPlaylist` 领域模型没有 `groupName` 字段**（`UserPlaylist.kt:5`）→ P2-04 的分组 UI 需要把它**加进 `:core:common` 的模型**，并同步改 `PlaylistStore` 的加载/保存路径。这是对既有模型的增量改字段，注意 `BackupArchiveCodec` 的序列化也要一并带上，否则 WebDAV 备份会丢分组。
  - **系统歌单标识**：`FAVORITES_PLAYLIST_ID = "favorites"`（`UserPlaylist.kt:3`）——**id 是 `favorites`，显示名才是「我喜欢的音乐」**。导出/导入与云端互认时要按 **id** 判断，不要按名字判断。
  - **`insertSongs` 用 `OnConflictStrategy.IGNORE`，而 `upsertSongs` 用 `REPLACE`** —— 重复添加同一首歌时两者行为不同（IGNORE 保留旧行、REPLACE 覆盖）。P2-06 批量导入时要想清楚用哪个，否则重复导入会静默丢数据或覆盖 `addedAt`。
  - 依赖：P1-12

- [x] **P2-05b 在线曲目在本地歌单里怎么表示（P2-06 / P2-07 的前置设计）** — ✅ 数据层已完成（提交 `2d43c4d`）。
  - **已实现**：`core:common` 里唯一定义 `ONLINE_SONG_KEY_PREFIX` / `onlineSongKey(platformId, songId)` / `isOnlineSongKey` / `generateVirtualSongId` / `UserPlaylistSong.isOnlineEntry`；`:module:online` 的 `OnlineSourceMediaMapper.onlineSongKey` 改为**委托**到 core（不再自己拼串）；新增 `OnlineSourceMediaMapper.toUserPlaylistSong(item)`；`PlaylistStore` 新增并列访问器 `onlineSongsOf` + 合并视图 `PlaylistEntries` / `playlistEntries`。
  - **`resolveSongs` 未改动**（16 处调用，改返回类型回归面太大）——在线曲由并列访问器暴露。
  - [ ] **剩余（UI 接线）**：歌单详情页改用 `playlistEntries` 并**分两段渲染**（本地段走既有虚拟列表；在线段点击走 `OnlinePlaybackLauncher`）。这块要改 `:app` 的 `PlaylistDetailScreen.kt`，**等 P2-02 落盘后再做**（避免与它并发改 `:app`）。
  - [ ] 剩余（P2-06）：导入时用 `toUserPlaylistSong` 落库，并决定批量写用 `insertSongs`(IGNORE) 还是 `upsertSongs`(REPLACE)。
  - **背景**：P2-05 查明 `PlaylistStore.resolveSongs` 会把不在曲库、且本地文件不存在的行**静默丢弃**，而导入 QQ/酷狗歌单必然产生这类行。不定好表示法，导入会「看起来成功但列表是空的」。
  - **选定方案（Option B：本地/在线两条路，不新建表）**：仍写进 `user_playlist_songs`，用以下约定区分 ——
    | 列 | 在线曲取值 |
    | --- | --- |
    | `song_key` | `"online://<platformId>/<songId>"`（**与 `song_attitude.song_key` 完全一致**，这样喜欢/不喜欢能对上） |
    | `path` | **同一个 `online://` 串** —— OrBit 的 `isOnlineSong` 就是判 `path.startsWith("online://")`，沿用这个约定可以让「是否在线曲」跟着行走 |
    | `id` | `generateVirtualSongId(platform, songId)`，**照搬 OrBit 算法**（`OnlineAudioSourceManager:48`）：`hash = "<platformId>:<songId>".hashCode().toLong()`，`hash > 0 → -hash`，`hash == 0 → -999999L`，否则原样。**负数保证不与本地 MediaStore id 冲突，且是稳定值**（跨端同步要靠它对齐） |
    | 元数据 | `title/artist/album/duration` 存平台快照；`fileSize = 0`、`albumArtPath` **留空**（在线封面是 http URL，塞进 albumArtPath 会被当作本地文件路径使用而出错；封面由在线解析层按需取） |
  - **`resolveSongs` 不改语义、不改返回类型**（它被 16 处调用，改返回类型会大面积回归）。改为**新增**一个并列访问器：
    `PlaylistStore.onlineSongsOf(playlist): List<UserPlaylistSong>`（过滤 `key.startsWith("online://")`），
    并提供一个合并视图 `playlistEntries(playlist, librarySongs)` 返回 `(local: List<AudioFile>, online: List<OnlineEntry>)`，**歌单详情页改用这个**，分两段渲染：本地段走既有虚拟列表，在线段点击走 `OnlinePlaybackLauncher`。
  - **不新建表**：`playlist_groups` / `song_attitude` 已在 `MIGRATION_6_7` 落地，再建表就得开 `MIGRATION_7_8`，而现有列完全够用（`key` 是 TEXT、`id` 是 INTEGER，负数 Long 放得下）。
  - **播放与队列**：整单播放时本地曲沿用现有队列；在线曲需逐首解析（与 P1-14「播放全部只起播第一首」同一限制），**不要**为它伪造本地路径。
  - 依赖：P2-05

- [ ] **P2-06 歌单导入导出（逻辑层）** — ⚠️ **已重新勘察：不要从零写 `PlaylistTransferManager`，RawS 已有一大半。**
  - **`PlaylistStore` 里已经存在（禁止重写，铁律 5）**：
    | 已有能力 | 位置 |
    | --- | --- |
    | 纯文本/M3U8 路径导入（三层模糊匹配：精确 path → 后缀匹配 → 文件名唯一匹配；按同名歌单**合并**；按 `key` 去重） | `importTextPlaylist(uri, librarySongs)` |
    | JSON 导出 / 导入（`{playlists:[...]}`，含 `playlistToJson` / `jsonToPlaylist`） | `exportJson` / `exportJsonForBackup` / `restoreJson` / `restoreJsonAndVerify` |
    | 编码嗅探、行解析、路径归一化 | `decodeText` / `parsePlaylistLine` / `normalizedPath` / `pathCandidates` |
  - **P2-06 真正要补的（5 件事）**：
    1. **M3U8 导出**：`#EXTM3U` + `#EXTINF:<秒>,<歌手> - <歌名>` + 自定义标签 **`#EXTRAWS-*`**（AGENTS 要求把 OrBit 的 `#EXTORBIT-*` 改名）。
    2. **M3U8 导入的元数据**：现在 `parsePlaylistLine` 直接 `if (line.startsWith('#')) return null` —— `#EXTINF` 里的**歌手/时长被整个丢掉**，只能靠路径匹配。而 OrBit 的 M3U8 主要靠「歌名 - 歌手」匹配 → 需要先用 `#EXTINF` 元数据匹配曲库，失败再回退到现有的路径匹配。
    3. **导出到文件 + 分享**：`cacheDir/playlist_exports` → `ACTION_SEND`，或写 MediaStore `Downloads/RawS-Music`。
    4. **链接/文本导入**：平台分享链接 → `OnlineMusicRepository.parseLinkOrText`。
    5. **在线曲目要能进出**：现在 `importTextPlaylist` 的 `matched` **只从 `librarySongs` 里取**，所以引用在线曲的条目会被静默丢弃；导出时也要用 `isOnlineEntry()` 区分本地/在线，并让 `OnlineSourceMediaMapper.toUserPlaylistSong` 负责在线行的构造。
  - ⚠️ 批量写用 `insertSongs`（`IGNORE`）还是 `upsertSongs`（`REPLACE`）要想清楚：**同曲重复导入时二者行为不同**（IGNORE 保留旧行与旧 `addedAt`，REPLACE 覆盖）。
  - 依赖：P2-05、P2-05b

- [ ] **P2-07 歌单导入导出（UI）** — `ExportPlaylistDialog` / `ImportPlaylistDialog`（Miuix + `RawMiuixOverlayDialog`）；导入支持 链接 / JSON / M3U8 / 纯文本 四种输入。
  - 依赖：P2-06

- [x] **P2-08 QQ / 酷狗扫码登录（逻辑层）** — ✅ 已完成（提交 `b09bcb6`）。
  - `PlatformCredentialCipher`（`module/online/.../auth/crypto/`）：Android Keystore + **AES-GCM**，每次加密用**新的随机 IV**（固定 IV 会让 GCM 彻底失去安全性），IV 与密文拼接后 base64。
  - `PlatformAccountManager`（`object`，不需要 Context）：`getQrCode` / `checkQrStatus`（成功即落盘）/ `saveAccount` / `logout` / `refreshAccount` / `fetchUserPlaylists` / `getCookieHeader` / `isLoggedIn`。
  - `AppPreferences.PlatformAuth`（MMKV）：**只存密文**、不做加解密 —— 否则 `:module:data` 要反向依赖 `:module:online` 成环。
  - ⚠️ **三条不能改的约束**：① **加密失败绝不退化成写明文**，直接放弃保存并记日志；② **解密失败返回 null 而非抛异常**（Keystore 密钥可能在恢复出厂/换机恢复后失效，那时应按未登录处理让用户重扫，不能在启动路径崩溃）；③ 首次访问时**懒恢复**而不是 `init` 里读 MMKV（MMKV 由 `CoreInit` 初始化，避免初始化顺序问题）。
  - 恢复后自动调用 **P1-09 预留的 `bindAuthProviders` 钩子**，把账号接到音源请求上（含避免重入死锁的处理）—— 这条之前一直是空钩子，现在接上了。
  - 待验证（真机）：能拿到二维码、轮询到登录成功、能列出并导入「我创建的」与「我收藏的」歌单；以及**落盘不可明文读出**。
  - 依赖：P1-01

- [ ] **P2-09 酷狗扫码登录（逻辑层）** — `KugouMusicAuthService`，**必须保留 10 条候选 URL 兜底链**与签名盐常量；凭据同样加密存储。
  - 依赖：P1-01、P2-08（复用账号模型与存储）

- [ ] **P2-10 平台账号 UI** — 「平台账号」页（QQ / 酷狗两张账号卡：头像/昵称/VIP/登录/退出/刷新），入口放设置「在线音乐」分组；扫码弹窗（`RawMiuixOverlayDialog` + `DisposableEffect` 取消轮询）。
  - 依赖：P2-08、P2-09

- [ ] **P2-11 导入我的歌单（UI）** — 拉取账号歌单 → 多选 → 导入为本地歌单（在线曲目保留 `online://` 标识，播放走 P1-11 解析层）。
  - 依赖：P2-10、P2-06

- [ ] **P2-12 歌单同步（逻辑层）** — `CloudPlaylistSyncClient`：对接 `/api/user/auth`、`/api/user/playlists/{backup,restore,delete}`；JSON 字段与 `docs/cloud-sync-contract.md` §4 **逐字一致**；`mode=merge|overwrite`；喜欢/不喜欢按系统歌单名 `我喜欢的音乐`/`不喜欢的音乐` + `groupName="系统"` 序列化；密码加密存储。
  - 依赖：P1-12、P2-01

- [ ] **P2-13 歌单同步（UI）** — 云同步页（骨架照抄 `WebDavPage.kt`）：地址 / 账号 / 上传模式 / 选择性上传下载 / 清空二次确认 / 显示 `updated_at`。
  - 依赖：P2-12

- [ ] **P2-14（可选，低优先）最近播放歌单** — `AppPreferences` 存 current/previous 两个条目。
  - 依赖：P2-13

---

## P3 洛雪歌源同步

> 设计见 `AGENTS.MD` §6.3。**运行时不移植**（RawS 已有 `MusicSourceRuntimeService`），只做云同步。

- [ ] **P3-01 阅读音源安装 API** — 读 `LxSourcePluginStore` / `MusicSourcePluginStore` / `MusicSourcePersistenceJson`，确认"从内容安装脚本"的现有入口与目录约定，写下结论。
  - 依赖：P0-05

- [ ] **P3-02 定义同步 DTO（并集）** — `CloudSourceEntry`：`id, name, version, author, description, sourceUrl, scriptContent, scriptSha256, enabled, isPrimary, supportPlatforms, installedAtMs, updatedAtMs, origin, methods`；Gson 序列化。
  - 依赖：P3-01

- [ ] **P3-03 `CloudSourceSyncClient`** — 上传时**读取脚本文件内容内联进 JSON**（RawS 只存 `scriptPath`，不能只传路径）；还原时写回文件 → 校验 sha256 → 走既有安装 API 注册；兼容 OrBit 备份（有 `scriptContent` 无 `scriptPath`）；缺正文时报「该备份不完整」。
  - 依赖：P3-02
  - 验收：清数据后从云端还原的音源**能真正解析出播放地址**。

- [ ] **P3-04 音源门户新增「云同步」tab** — 追加 `SourcePortalTab.CloudSync`；**同步更新 `SourcePortalBottomNavigation` 的 tab 数、`SourcePortalTabIcon` 映射**；内容：地址输入（用户自填、不内嵌）/ 登录 / 上传 / 下载 / 删除 / 清空 / 上次备份时间 / 条目列表。
  - 依赖：P3-03

- [ ] **P3-05 音源门户新增「云同步」文案** — core:ui 中英双语。
  - 依赖：P3-04

- [ ] **P3-06（可选，P3）社区音源广场** — `GET /api/sources` 列表 + 一键安装。**注意该接口无鉴权**；开放发布需先改 Worker（属改后端，须用户批准）。默认关闭。
  - 依赖：P3-04

---

## P4 频谱可视化

> 设计见 `AGENTS.MD` §6.4。**数据源已存在**（`RealtimeSpectrumPipeline`，112 频点）；只做渲染 + 参数 + UI。

- [ ] **P4-01 扩展 `AudioVisualizerStyle` 枚举** — **只追加** `BarsWithPeaks=6, AuroraMountain=7, MirroredBars=8, TimeTunnel=9, Octgrams=10, SoundCity=11, FractalGalaxy=12, QuantumVortex=13`；**严禁重排 0..5**；`from(value)` 兜底保持 `Spectrum`。
  - 验收：老用户偏好（0..5）升级后不错位。
  - 依赖：P0-05

- [ ] **P4-02 新增 `VisualizerColorScheme`** — 5 种配色（primary/secondary/peak 三色），存 `AppPreferences.UI`。
  - 依赖：P4-01

- [ ] **P4-03 视觉参数入 `AppPreferences.UI.Visualizer`** — `barWidthDp` / `barAlpha` / 描边（宽、色、透明度、仅描边）/ `singleColor` / 自定义双色 / `peakDecayEnabled` / 背景亮暗色 / `isVisualizerMaximized`。
  - 依赖：P4-02

- [ ] **P4-04 Canvas 形态：BarsWithPeaks / AuroraMountain / MirroredBars** — 移植物理参数（attack 0.82 / decay 0.68、<0.02 归零、峰值保持 5 帧、重力 0.0035、峰值≥柱高）；复用 RawS 频点，不新造数据源。
  - 依赖：P4-03

- [ ] **P4-05 GL 形态 1/2：TimeTunnel、Octgrams** — `core/ui/.../widget/player/gl/`：`GLSurfaceView.Renderer` 数学照搬 + `AndroidView` 包装（EGL2/3、`RENDERMODE_CONTINUOUSLY`、不可见即 `onPause()`）。
  - 依赖：P4-03

- [ ] **P4-06 GL 形态 2/2：SoundCity、FractalGalaxy、QuantumVortex** — 同上。
  - 依赖：P4-05（复用 GL 包装与生命周期工具）

- [ ] **P4-07 播放器页形态选择器扩展** — 改 `MainActivity.kt`（约 `:3483-3494` 一带）与 `ComposePlayerContainer.kt` 的 `AudioVisualizerStyle` 参数；**不要只改设置页**（现有形态切换在播放器页）。
  - 依赖：P4-04、P4-06

- [ ] **P4-08 设置页参数 + 全屏最大化** — `PlayerInterfaceScreen.kt` 加配色/参数滑块 + 小尺寸实时预览；播放器页加全屏最大化可视化（点按退出 + 返回键处理）。
  - 依赖：P4-07
  - 验收：见 `AGENTS.MD` §8.2 ④ 全部 8 项（含 50 次切换不崩、GL 正确 pause/resume）。

---

## P5 云同步统一入口 + 收尾回归

- [ ] **P5-01 `CloudSyncClient` 统一封装** — 合并 P2-12 与 P3-03 的重复 HTTP 逻辑：统一 OkHttp 实例、`CloudSyncResult<T>`、服务器地址/账号/密码的 `AppPreferences.CloudSync` 与加密、错误映射。
  - 依赖：P2-12、P3-03

- [ ] **P5-02 独立「云同步」设置页** — `NavScene.CLOUD_SYNC` + 入口；集中呈现歌单同步与音源同步两块（骨架照抄 `WebDavPage.kt`）。
  - 依赖：P5-01

- [ ] **P5-03 全量构建 + Room 升级回归** — `:app:assembleDebug` + 覆盖安装升级测试（老版本 → 新版），确认曲库/收藏/播放统计/音源/设置全部保留。
  - 依赖：P0-04、全部功能任务
  - 验收：`AGENTS.MD` §8.1 全部条目。

- [ ] **P5-04 四大功能端到端验收** — 逐条勾选 `AGENTS.MD` §8.2 ①②③④ 的全部验收项，结果记录在本文件末尾。
  - 依赖：P5-03

- [ ] **P5-05 文档收口** — 更新 `AGENTS.MD` §9 决策表（补充实际发生的决策）、`docs/` 两份参考文档；把本文件所有任务置为 `[x]` 或 `[-]`。
  - 依赖：P5-04

---

## 依赖关系（串行链）

```
P0-01 → P0-02 → P0-03 ┐
                      ├→ P0-05 → P0-06
        P0-04 ────────┘
P0-05 → P1-01 → P1-02 → P1-03 → P1-04..P1-08 → P1-09 → P1-10
P0-05 → P1-12 → P1-09 → P1-13 → P1-14
P1-12 → P2-01 → P2-02 → P2-04
P1-12 → P2-03 ─────────┘
P1-12 → P2-05 → P2-06 → P2-07 → P2-11
P1-01 → P2-08 → P2-09 → P2-10 → P2-11
P1-12 + P2-01 → P2-12 → P2-13 → P2-14
P0-05 → P3-01 → P3-02 → P3-03 → P3-04 → P3-05 → P3-06
P0-05 → P4-01 → P4-02 → P4-03 → P4-04 ┐
                        P4-03 → P4-05 → P4-06 ┴→ P4-07 → P4-08
P2-12 + P3-03 → P5-01 → P5-02 → P5-03 → P5-04 → P5-05
```

**可并行开工的三条线**（互不冲突）：
- 线 A：`P1-*` → `P2-*`（在线能力与歌单管理）
- 线 B：`P3-*`（音源云同步，只碰音源门户与独立文件）
- 线 C：`P4-*`（可视化，只碰 `core/ui/.../widget/player/` 与 `PlayerInterfaceScreen`/`MainActivity` 可视化段）

⚠️ **冲突提示**：`P2-02`/`P2-04` 与 `P4-07` 都会改 `MainActivity.kt`；`P2-02`/`P2-04` 会改 `PlaylistScreen.kt`。这些任务**不要并行**。

---

## 基线记录（P0-06 产出，待填）

| 功能 | 改动前状态 | 备注 |
| --- | --- | --- |
| 本地曲库扫描 | 待填 | |
| 本地播放 | 待填 | |
| 歌单增删改 | 待填 | |
| 音源导入（LX） | 待填 | |
| 音源导入（MusicFree） | 待填 | |
| 实时可视化开关 | 待填 | |
| WebDAV 备份页 | 待填 | |

## 验收记录（P5-04 产出，待填）

| 功能域 | 结果 | 日期 | 备注 |
| --- | --- | --- | --- |
| ① 在线歌单广场 | 待填 | | |
| ② 歌单管理 | 待填 | | |
| ③ 洛雪歌源同步 | 待填 | | |
| ④ 频谱可视化 | 待填 | | |
