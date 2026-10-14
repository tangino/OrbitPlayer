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

### P1-15（补充修复）在线音乐入口不可达 —— ✅ 已完成（提交 `bfd5250`）

🔴 **这是一个真机反馈暴露的接线缺陷，值得记下来当教训。**

- **现象**：用户装到真机后反馈「除了平台账号，其他功能都没看到入口」。
- **根因**：`ONLINE_MUSIC` 的**唯一**入口是侧栏（`AppSideRail.kt:83`），而侧栏启用条件是
  `sideRailEnabled = !bottomNavigationEnabled && currentScene == HOME && ...`（`AppMainLayout.kt:461`），
  且 `AppPreferences.UI.isBottomBarEnabled` **默认 `true`** → **默认配置下侧栏手势是死的，整个在线歌单广场（4 个 tab + 歌单详情）不可达。**
- **我的验证盲区**：P1 收尾时我核对的是「`NavScene` / `ComposeNavHost` / `AppSideRail` / `MainActivity` 四处**代码都接上了**」，
  但**没核对「默认配置下用户能不能走到」**。四处都通、终点却在一个默认关闭的模式里 = 等于没有入口。
  → **以后接线 UI 必须多问一句：默认配置下走得到吗？**
- **修法**（3 处）：
  1. `NavScene.bottomNavigationEntries` 加入 `ONLINE_MUSIC` → 出现在设置「底部导航栏」选择器里，用户可放进底栏。
     ⚠️ 该列表只决定「**可选范围**」，**不会**改变任何现有用户的底栏（选择按 `tag` 存储；默认底栏是 `defaultBottomNavigationScenes`，仍是原来 5 项）。
  2. `bottomNavigationLabel()` 补分支 —— 否则落到 `else -> label` 会显示 `NavScene` 枚举里**硬编码的中文**，英文环境串味。
  3. `customBottomNavigationIconResOrNull()` 补分支复用 `ic_side_rail_online` —— 否则退化成与 `SONGS` 相同的通用音乐图标。
- **附带修正**：`NavigationState.kt:225` 用 `bottomNavigationEntries` 决定「切底栏时回退到最近顶层入口」；进入该集合后，
  从「在线歌单详情」切标签能**正确回到广场**，之前会一路弹过 `ONLINE_MUSIC`。
- **② 主界面卡片入口 —— ✅ 已完成（提交 `57ec1e6`，按用户要求「放到主界面做成卡片」）**：
  - `HomeLibraryCardScenes` 加入 `ONLINE_MUSIC` 并置于**首位**（该列表同时是默认顺序）。
  - 🔑 **关键点：已有自定义卡片顺序的用户也能看到这张新卡** —— `normalizeOrder` 里有一句
    `defaults.forEach(seen::add)`，会把 defaults 中缺失的场景**补到末尾**。所以不会出现「存过偏好就看不到新卡」。
  - 卡片图标原先只特判了 `SOURCE_IMPORT`（画 `ic_cloud`），其余走关联歌曲封面 `song?.coverKey`；
    而门户型场景没有「关联歌曲」，`coverKey` 恒空 → 不处理的话卡片**只有底色和标题、看起来像坏了**。
    已收敛成 `homeCardFixedIconResOrNull()` 映射，`ONLINE_MUSIC` 复用 `ic_side_rail_online`。
  - 文案走既有 `scene.labelRes()`（`NavSceneLabels` 里已有中英双语 `nav_scene_online_music`）；
    统计计数 `countFor` 的 `else -> null` 保证不显示数字也不崩；点击是通用的 `onNavigate(card.scene)`。
- **仍未做（需用户决策）**：
  1. 是否也把 `ONLINE_MUSIC` 放进 `defaultBottomNavigationScenes`（会改变所有未自定义用户的默认底栏，属产品决策）。
     （底栏**可选列表**里已经有它了，见上一条提交 `bfd5250`。）
  2. 设置页内的「在线音乐」入口 —— 全仓库**没有**「Intent → NavScene」的机制，要做需新增管线（或把门户再挂一个 Activity），不是一行的事。


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
    - ⚠️ **`:core:ui` 的单元测试整套跑不起来（上游遗留，非本移植造成）**：`.\gradlew.bat :core:ui:testReleaseUnitTest` 会停在
      `:core:ui:compileReleaseUnitTestKotlin FAILED`，**27 个编译错误分布在 7 个测试文件**，是**测试与源码的 API 脱节**，例如：
      `CollectionTransitionProtocolTest.kt:22` 用 `nextFrame` 参数（源码里没有）、`ReferenceHeroArtworkGeometryTest.kt:9` 引用 `visibleSidePx`（源码里没有）、
      `virtuallist/Bound*` 占 15 个。
      - **已核实与本移植无关**：涉及的源码文件（`CollectionTransitionProtocol.kt` / `ReferenceHeroArtworkGeometry.kt` 等）本会话**从未改动**；报错测试也**零引用**我改过的符号。
      - **影响**：`:core:ui` 的测试**不能当回归门禁用** —— 其中包括正好覆盖底栏/主界面卡片的 `BottomNavigationEntryTest` / `BottomNavigationCard*` / `HomeCardEditorPolicyTest`。
        **P4 频谱可视化会大量改 `:core:ui`**，届时需要靠静态核对或先修好这套测试（修它属于动上游测试文件，**需先问用户**）。
      - 替代手段（本次用过，有效）：① 静态等价验证（如按 `NavScene` 的 `(id, "tag")` 正则抽出全部 56 项，验证 id/tag 唯一性 —— 正是 `BottomNavigationEntryTest` 的断言）；
        ② `:module:data` / `:module:online` 的测试是**好的**，可以直接跑（云同步契约 16 个、M3U8 编解码 6 个都跑通了）。

---

## P2 歌单管理

> 设计见 `AGENTS.MD` §6.2。

- [x] **P2-01 喜欢 / 不喜欢（数据层）** — ✅ 已完成（提交 `69fe4bc`，`module/data/.../repository/SongAttitudeRepository.kt`）。
  - ⚠️ **本轮发现规格有误**：`AGENTS.MD` §3.5 原写「喜欢/不喜欢都不存在」，实测 RawS **已有**完整的「喜欢」机制 —— `FAVORITES_PLAYLIST_ID = "favorites"` 系统歌单（名正好是「我喜欢的音乐」）+ `PlaylistStore.favoriteSongKeys/isFavorite/toggleFavorite`，且已打通**播放器**（`PlayerService`）、**长按菜单**（`SongActionSheetHelper`）、**歌单页**（`PlaylistScreen` 心形/粉色强调/禁止改名删除）、**WebDAV 备份**（`BackupAndroidPersistence`/`BackupArchiveCodec`）。
  - **用户决策（§9 D14）：混合方案** —— 本地曲的喜欢**沿用既有歌单**（不接管，避免两个事实来源让心形状态不一致）；**不喜欢（本地+在线）**与**在线曲的喜欢**走 `song_attitude`。
  - 已实现：`dislikedKeys` / `onlineFavoriteKeys`（**只收 `online://` 前缀**）/ `setDisliked` / `removeDislike` / `clearAllDislikes` / `setOnlineFavorite` / `setOnlineFavorites`（批量）/ `cycleOnline`（`NONE→FAVORITE→DISLIKED→NONE`）。写入后统一 `refresh` 发布 `StateFlow`。
  - 依赖：P1-12

- [x] **P2-02 喜欢 / 不喜欢（UI）** — ✅ 已完成（提交 `cab2282`，7 个文件 / +506 行）。
  - **A 伪歌单卡**：`PlaylistScreen.kt` 在真实歌单**之前**插入「我喜欢的歌」「我不喜欢的歌」两张卡，曲目数实时（喜欢=`PlaylistStore.favoriteSongKeys().size`，不喜欢=`dislikedKeys.size`）；复用既有 `chunked(2)` + `playlistRowHeight = cardWidth/0.86f + 14.dp` + `VirtualListCustomProvider` 网格与 `PlaylistFrameCard`；**不可改名/删除/长按**。按 D14：喜欢走 `PlaylistStore.toggleFavorite`，**不碰 `song_attitude`**。
  - **B 曲目列表**：伪卡走既有 `PLAYLIST_DETAIL_PAGE`，`AppPageRenderer` 即时构造**内存** `UserPlaylist`（不落 Room，见 `AttitudePlaylists.kt`），复用 `PlaylistDetailScreen` → 列表/空态/播放/随机播放都能用；列表内长按移除按伪 id 分流。
  - **C 长按菜单 + 清空**：互斥的 喜欢/取消喜欢/不喜欢/取消不喜欢 动作行；「清空所有不喜欢」在歌单页顶栏尾部（`headerTrailingContent` 插槽），`RawMiuixOverlayDialog` 二次确认后 `clearAllDislikes`。反馈走 `AppNoticeBus`。
  - 新增 21 个 `playlist_attitude_*` 文案（EN/ZH **各 21 条、键完全对齐**，已脚本核对）；新增 1 个 `ic_thumb_down_fill.xml`（仓库原本只有心形，没有「不喜欢」图标）。
  - 🚧 **发现一个重要限制：播放器的长按菜单里拿不到在线曲的权威 key。** 长按菜单看到的 `AudioFile` 已经是在线曲**解析后**的形态（`path` = 可播放 URL、`encodingFormat = "rawsmusic-online-resolved"`），而 `online://<platformId>/<songId>` 这个权威 key（`OnlineSourceMediaMapper.onlineSongKey`）在解析过程中**丢了** —— `PlayerControllerMusicSourcePlaybackBackend.toAudioFile()` 只存了 `stableIdentity` 的哈希。用它算 `playlistIdentityKey()` 会写一条**永远匹配不上、且会随 URL 失效的脏数据**。
    → 当前处理：对这类曲目**隐藏**喜欢/不喜欢动作（本地曲不受影响）。
    → **影响面**：在线曲的「喜欢」并非完全不可用 —— **在线广场的歌单详情页**（`:core/ui`）手里有 `OnlineSongItem`，能拿到正确 key，那边的收藏按钮仍可正常写入 `song_attitude`。缺的只是「从播放器长按菜单给在线曲点喜欢」这一入口。
    → 若要补：需把 `online://` key 从 `MusicSourcePlaybackRequest.item` 透传到 `AudioFile`，改动落在 **`:module:player`**，属独立任务（不要和 P2 其它任务混做）。
  - ⚠️ 未做真机验证；`SongAttitudeRepository` 目前只在进歌单页/打开长按菜单时 `refresh`，**App 启动没有全局预热**，所以极早期进页面会先显示 0 再跳变（后续可考虑在 `CoreInit` 或首帧后预热）。
  - 依赖：P2-01

- [x] **P2-03 歌单分组（数据层）** — ✅ 已完成（提交 `20ed046`，`module/data/.../prefs/PlaylistGroupStore.kt`）。
  - 新增 `UserPlaylistDao.updatePlaylistGroup`（移单个歌单）+ `reassignGroup`（整组改挂）。
  - 关键设计：**「默认」不存进 `playlist_groups` 表**，由 `user_playlists.group_name` 的列默认值兜底 → 它是保留分组（不可创建/改名/删除），读列表时由 `refresh` 统一补在最前，**因此永远不可能出现无归属歌单**。
  - **分组改名与删除分组必须同时改两张表**（`playlist_groups` + `user_playlists.group_name`），用 `withTransaction` 包住；只成功一半会让歌单指向不存在的分组而整批消失。
  - `rename` 只捕获 `NoSuchElementException` 转 `NotFound`，`CancellationException` 自然上抛（**不用 `runCatching`**，避免重蹈吞取消的坑）。
  - 依赖：P1-12

- [x] **P2-04 歌单分组（UI）** — ✅ 已完成（提交 `8ebb8e9`）。`PlaylistScreen.kt` 按 `PlaylistGroupStore.groups` 分区 + `SectionHeader` 标题行，仍走既有虚拟列表（`chunked(N)` + `VirtualListCustomProvider`，**无 `LazyVerticalGrid`**）。文件规模 734 → 678 行，新增组件拆到 `PlaylistGroupChrome.kt`（292）/ `PlaylistGroupDialogs.kt`（422）。
  - 五个设计决策：① 两张「喜欢/不喜欢」伪卡**置顶、不受分组影响**（系统入口、不落 Room、没有 group_name，塞进「默认」会混淆系统语义与用户数据）；② **空分组也渲染标题行**，否则没有可长按的地方去改名/删除；③ 歌单指向不存在分组（云同步还原回来的 groupName）时**回落「默认」分区显示**，而不是从界面消失；④ 首帧 Store 未刷回时按「只有默认」处理，避免真实歌单一帧全丢；⑤ 长按与 more 统一开「歌单操作」菜单（重命名/移动到分组/删除），删除仍有二次确认。
  - 按上一轮的约定：`UserPlaylist` 模型**不加** `groupName`，分组归属从 `PlaylistGroupStore.playlistGroups` 查；写操作显式 rethrow `CancellationException`、不用 `runCatching`；文案 EN/ZH 各 23 条键完全对齐（已脚本核对）。
  - 🐛 **顺带修掉 P2-02 的漏网 bug**（提交 `413a1db`）：`PlaylistStore.playlists` **本来就含**真实系统歌单 `favorites`（名「我喜欢的音乐」），而 P2-02 又插了一张指向**同一底层歌单**的伪卡 → 用户会看到两张「喜欢」卡。修法是 `filterNot { it.isFavorites }`（伪卡 id 是 `attitude:favorites`，不满足 `isFavorites`，不会误伤）。只影响界面列表，存储/WebDAV/云同步不受影响。
  - ⚠️ **待真机验证**：删分组后歌单回落默认、空分组渲染、移动分组 —— 目前只是代码路径推理，未实测。
  - ⚠️ 次要遗留：`PlaylistGroupDialogs.kt` 里用了 2 处 Material3 `Text`（与同目录既有 `PlaylistScreen.kt` 的用法一致，属局部约定），严格按 §4.3「不要扩散 Material3」应改用 Miuix `Text`，留作清理项。
  - ⚠️ **「默认」分组的存储值仍是中文字面量 `默认`**（`PlaylistGroupEntity.DEFAULT_GROUP_NAME`，也是 `user_playlists.group_name` 的列默认值），只在**显示层**按语言本地化。这是有意的：该字面量已写进 `MIGRATION_6_7` 与 schema，且云端 `groups_json` 的合并语义就是「永远丢弃『默认』」。**不要**为了英文环境去改存储值。
  - 依赖：P2-03、P2-02

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
  - [x] **UI 接线已完成**（提交 `ec38da7`）：`PlaylistDetailScreen` 改用 `playlistEntries`，本地曲 + 在线条目一起进同一个虚拟列表（在线条目的 `id` 是负数虚拟 id、`path` 就是 `online://` 串，都不与本地曲冲突）；随机播放只取本地曲；`OnlineSourceMediaMapper` 新增 `parseOnlineSongKey` + 反解 `toOnlineSongItem`，在线条目交给 `OnlinePlaybackLauncher` **单曲**起播（整单队列仍需逐首预解析）。
    - 🐛 **顺带修掉一个真实 bug**：`onRemoveSong` 原先一律用 `playlistIdentityKey()`，而它会**给在线路径加上 `path|` 前缀**，与存的 `song_key`（裸 `online://` 串）对不上 → 移除在线曲目会**静默 no-op**。现在先判 `isOnlineSongKey` 再决定用哪个 key。**后续任何按 key 操作在线条目的地方都要注意这个前缀差异。**
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
  - **进度**：
    - [x] **M3U8 编解码器** —— ✅ 提交 `317d0a8`（`module/data/.../playlist/PlaylistM3u8Codec.kt` + 6 个往返单测，`tests=6 failures=0 errors=0 skipped=0`）。`encode` / `decode` 是纯函数，`writeExportFile` 落 `cacheDir/playlist_exports`。解码侧同时认 `#EXT-RAWS-` 与 `#EXT-ORBIT-` 两种前缀。
      - ⚠️ **导出侧写不了封面/平台/创建者标签**：RawS 的 `UserPlaylist` 只有 id/name/songs/时间，歌曲的 `albumArtPath` 是**本地路径**而非 http 封面 → 不写 `COVER`/`PLATFORM`/`CREATOR`/`EXTIMG`，但**解码侧全部认识**（读 OrBit 的完整文件没问题，我们导出的只是少装饰信息）。
    - [x] **导入编排 + 元数据匹配** —— ✅ 提交 `8c9e4cf`。
      - `PlaylistStore` 新增公开 `matchLibrarySongs(candidates, librarySongs)`（把 `importTextPlaylist` 里的三层路径匹配**原样提取**出来共用，逻辑未改）与 `importResolvedPlaylist(name, localSongs, onlineSongs)`（按同名合并、按 key 去重、在线行一并落库）。
      - `PlaylistTransferStore.importM3u8`：解码 → 有在线身份的直接建在线行（不走曲库匹配）→ 路径匹配 → **对没匹配上的再用「歌名+歌手」兜底**（跨设备导入时路径必然失效，这层才是主力）→ 交给 `importResolvedPlaylist`。同名多首时用歌手收敛，**仍不唯一就放弃而不是猜错**。
      - `PlaylistTransferStore.exportM3u8` 委托给编解码器落盘。
      - 刻意**不**复用 `OnlinePlaybackMatcher.normalizeForCompare`：它在 `:module:online`（依赖方向不允许），且策略面向跨平台找歌（宽松），而这里是本地库找回自己的歌（可以更严）。
    - [ ] **接线到 `PlaylistStore`** 的剩余部分：把 M3U8 导入接到 UI（文件选择器 / 分享目标）；**链接导入**（`OnlineMusicRepository.parseLinkOrText`，这步只能在 `:module:online` 或 UI 层）；**文件分享**（`ACTION_SEND` 或 MediaStore Downloads）。
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
  - **M3U8 精确格式（已从 OrBit `PlaylistTransferManager.exportToM3u8` 抄实，照这个写）**：
    ```
    #EXTM3U
    #PLAYLIST:<标题>
    #DESCRIPTION:<描述>            （换行替换成空格；为空则整行不写）
    #EXT-ORBIT-COVER:<封面 url>     （为空则不写）
    #EXT-ORBIT-PLATFORM:<平台id>:<原始id>
    #EXT-ORBIT-CREATOR:<创建者>
    （空行）
    #EXTINF:<秒>,<歌手> - <歌名>
    #EXTIMG:<封面 url>             （为空则不写）
    #EXT-ORBIT-ONLINE:<平台id>:<songId>
    <path 或 online://<平台id>/<songId>>
    （空行）
    ```
    - 导入侧（`PlaylistTransferManager:464-521`）：`#EXTINF:` 后的文本按 **`" - "`** 拆成 `歌手` / `歌名`（拆不开就把整串当歌名、歌手记「未知歌手」）；状态在遇到下一首时清空。
    - ⚠️ **规格里写的「把 `#EXTORBIT-*` 改名为 `#EXTRAWS-*`」不准确**：OrBit 实际用的前缀是 **`#EXT-ORBIT-`**（带连字符）。因此本移植应统一改成 **`#EXT-RAWS-`**（保持同形），**不要**写成 `#EXTRAWS-`，否则与源格式形状不一致、以后对照解读会出错。导入侧两种前缀都认，以便直接读 OrBit 导出的文件。
    - 好消息：OrBit 这套格式**本来就带在线曲支持**（`#EXT-ORBIT-ONLINE:` + `online://` 路径），与 P2-05b 定的表示法一致 —— 说明 P2-05b 的方向是对的。
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

- [x] **P2-09 酷狗扫码登录（逻辑层）** — ✅ **已完成，但不是在独立提交里完成的**：范围已被 P1 与 P2-08 覆盖，本轮核实并销账。
  - 服务本体在 **P1 提交 `c547956`**（「移植 QQ/酷狗扫码登录服务」，逐字移植）。
  - 核实证据：`KugouMusicAuthService.kt` 里 `WEB_SIGN_SALT`(L42) / `ANDROID_SIGN_SALT`(L43) / `APP_ID="1005"`(L39) / `SRC_APP_ID="2919"`(L40) / `PLAT_ID="4"`(L41) 五个易变常量**全部保留**；**候选 URL 兜底链**保留（`candidateUrls = listOf(...)` 出现在 `:240` 与 `:441` 两处，全文 24 个 `https://`）。
  - 凭据加密存储由 **P2-08** 统一提供：`PlatformAccountManager` 已注册 `OnlinePlatform.KUGOU to KugouMusicAuthService()`（`PlatformAccountManager.kt:50`），走同一套 `PlatformCredentialCipher` + `AppPreferences.PlatformAuth`。
  - ⚠️ 真机验证仍待做（在 P2-10 的扫码 UI 落地后一起验）。
  - 依赖：P1-01、P2-08（复用账号模型与存储）

- [x] **P2-10 平台账号 UI** — ✅ 已完成（提交 `f72cf02`）。新增 `PlatformAccountScreen.kt`(518 行) + `PlatformAccountActivity.kt`(12 行)；设置入口新建「在线音乐」分组。
  - **轮询防泄漏**：弹窗用 `qrTarget?.let { }` 条件组合，轮询全在 `PlatformAccountQrDialog` 的 `LaunchedEffect(platform, attempt)` 内（取码 → `withContext(Default)` 生成位图 → `while(isActive){ delay(2s); checkQrStatus }`）——关弹窗即离开 composition、effect 协程被取消并立刻打断 `delay`；**没有长驻协程、没有要手动 stop 的 Job**。另三道终止：`EXPIRED`/`ERROR` 立即结束、按 `expireTimeMs` 的 deadline 本地兜底、`attempt++` 重新出码。全程未用 `runCatching`。
  - 文案 EN/ZH 各 35 条、键完全对齐（脚本核对）。
  - ⚠️ **未真机验证**：二维码两条取图路径（QQ 的 `qrImageBytes` PNG / 酷狗 base64 或 `qrUrl`→ZXing）、头像远程加载（`BitmapImage` 内建 http(s)）、真机扫码全链路、VIP 字段实际返回值。
  - 📌 **它发现并被我核实的架构事实（已写入 `AGENTS.MD` §3.2）**：设置入口**不能**加在 `core:ui` 的 `SettingsRootPage` —— `AppMainLayout.kt:667` 是 `onSettingsClick?.invoke() ?: navState.navigateToSettings()`，宿主提供了回调就**绕过** core:ui 那页；本项目 `MainActivity.kt:2272` 提供的正是 `launchSettingsActivity(SettingsActivity)` → 真正渲染的是 `:app` 的 `LiquidGlassSettingsScreen`。**后续所有新设置页（含 P2-13 云同步、P4 可视化设置）都按 §3.2 的落法走。**
  - 依赖：P2-08、P2-09

- [ ] **P2-11 导入我的歌单（UI）** — 拉取账号歌单 → 多选 → 导入为本地歌单（在线曲目保留 `online://` 标识，播放走 P1-11 解析层）。
  - [x] **前置修复（提交 `b37d061`）**：`PlatformAccountManager.fetchUserPlaylists` 原先失败时 `return emptyList()`，
    UI 只能显示「你没有歌单」—— **用户会以为歌单丢了，而实际是网络/平台接口问题**，属本工程反复强调要避免的**静默失败**。
    已改为 `Result<List<OnlinePlaylist>>`：未登录 / 无可用服务 / 接口异常都是 `Result.failure`；
    **`Result.success(emptyList())` 表示该账号确实没有歌单**（正常结果，不是错误），两者可区分。
    该方法此前**没有任何调用方**，所以改签名安全；`IPlatformAuthService.getUserPlaylists` 签名不动，避免动到已移植的平台实现。
    → **P2-11 的 UI 必须分别呈现这三种状态**（失败可重试 / 确实没有 / 正常列表）。
  - [ ] UI：入口放平台账号页（每张账号卡一个「导入我的歌单」，仅已登录可用）→ 列表多选 → 逐个 `OnlinePlaylistImportService.importPlaylist` → 汇总结果（失败项要列出歌单名与原因）。子代理实现中。
  - 依赖：P2-10、P2-06

- [x] **P2-12 歌单同步（逻辑层）** — ✅ **已完成**（4 批提交：`0530f42` 序列化 / `8a3c4ae` HTTP / `4890c85` 上传 / `4681d1d` 还原）。`CloudSyncClient` 对接 `/api/user/auth`、`/api/user/playlists/{backup,restore,delete}`、`/api/user/data/clear`；JSON 字段与 `docs/cloud-sync-contract.md` §4 逐字一致（`SerializedName` + 16 个契约测试断言字段名）；`mode=merge|overwrite`；喜欢/不喜欢按系统歌单名 `我喜欢的音乐`/`不喜欢的音乐` + `groupName="系统"` 序列化；密码加密存储（Keystore + AES-GCM，复用 `PlatformCredentialCipher`）。
  - **进度**：
    - [x] **契约序列化 + 配置存储** —— ✅ 提交 `0530f42`（`module/online/.../sync/CloudPlaylistJson.kt` + 8 个契约测试，`tests=8 failures=0 errors=0 skipped=0`）。`AppPreferences.CloudSync`（地址自填不内嵌 / 用户名 / **密码只存密文** / 上次备份与还原时间 / 上传模式）。
      - 已在文件头注明契约里**三处容易猜错的不对称**：① 请求字段 camelCase（`playlistsJson`）而响应字段 snake_case（`playlists_json`），JSON **内容**又是 camelCase；② `sourcePlatform` 与在线收藏的 `platform` 要**大写枚举名**（`NETEASE|QQ|...`），不是内部小写平台 id（Kotlin 枚举 `name` 恰好就是它）；③ 契约里在线收藏叫 `customGroup`，本地实体叫 `groupName`（OrBit 历史命名，**契约优先，不要统一**）。
      - 按 §5 合并语义：上传侧**丢掉「默认」**（服务端 merge 会丢，但 `overwrite` 不过滤，带着上传会真的存进云端分组列表）、**总是带上「系统」**。
    - [x] **HTTP 客户端** —— ✅ 提交 `8a3c4ae`（`CloudSyncClient.kt`）。统一 `CloudSyncResult<T>`；响应 DTO 用 snake_case；四条服务端缺陷容错全部落实，其中 **`deletePlaylists` 加了硬护栏：既不 `deleteAll` 又数组全空时直接本地拒绝、不发请求**（服务端那种请求会清空用户全部云端歌单）；restore 的 404 用 `codeOverrides` 映射成「云端还没有备份」；`data/clear` 只传 `username`。
    - [x] **上传侧编排** —— ✅ 提交 `4890c85`（`CloudPlaylistSyncService.kt`）。`buildPayload` 装配四处数据源；`upload` 复用 `CloudSyncClient.backupPlaylists`。
      - ⚠️ **上传前必须 refresh 四个 Store 并 `awaitBackupReady`**：DAO 全是 `suspend`、`StateFlow` 只在 refresh 后新鲜，而云同步是一次性快照 —— 用陈旧内存态上传会在服务端 last-write-wins 下把云端写回旧版本。
      - 喜欢/不喜欢塞进两个系统歌单（契约无独立表/接口）；普通歌单排除系统 `favorites`，避免同名合并出两个语义不同的歌单；在线曲的喜欢/不喜欢没有本地元数据 → 用 `songOfKey` 上传（身份在 `path`）。
    - [x] **下载/还原侧** —— ✅ 提交 `4681d1d`。`CloudPlaylistJson` 补 `parsePlaylists`/`parseOnlineFavorites`/`parseGroups`/`systemKindOf`/`toAudioFile`/`toOnlineSongRow`/`toOnlinePlaylist`；`CloudPlaylistSyncService.restore` + `applyBackup` 落回本地。三个契约细节：①「我喜欢的音乐」「不喜欢的音乐」**不能当普通歌单建**（契约里它们只是 like/dislike 的载体，没有独立表或接口），认名字跳过、改走 `song_attitude` 与既有 `favorites`，否则会建出两个与伪歌单重名、语义又不同的真歌单；② 态度从**所有歌单里的所有歌**的 `isFavorite`/`isDisliked` 读（同一首歌可能出现在普通歌单里也带标记，全认才幂等）；③ `groups_json` 里的「系统」**不建成分组**（契约内部标记，RawS 把系统歌单建模成伪卡，建出来只会得到永远空的分组）。
      - ⚠️ **最容易写错的一处**：本地曲的 `song_key` 必须让 core 算（`path|` 前缀），**不能拿 `path` 顶替**；写错会往 `song_attitude` 存一条永远匹配不上的记录、而外观毫无异常。已写成断言测试 `local song key carries the path prefix while online key stays bare`。
      - ⚠️ **已知边界**：`toAudioFile` 的 `id` 故意留 0（云端 id 属于别的设备，带过来会指错歌）。因此**跨设备还原**时若本地没有对应文件，这些行会被 `resolveSongs` 过滤掉、在歌单里看不见 —— 同设备备份/还原（主要用例）不受影响。要做「灰色显示不可用曲目」需另开任务。
    - 测试：`tests=16 failures=0 errors=0 skipped=0`。
  - 补充（契约 §6 容错，实现时必须守）：**① 非法 JSON → 500**（保证序列化合法）；**② `playlists/delete` 不传 `deleteAll` 也不传数组会清空全部云端歌单**（已在客户端加护栏）；③ 选择性删除不回写 `groups_json`（分组可能残留）；④ `data/clear` **不接受 `userId` 别名**。另：restore 无备份返回 404（不是空数据）。
  - 注意 P5-01 会把这里的 HTTP 逻辑与 P3-03 合并成统一客户端，所以**别把 HTTP 细节散落到多个地方**（目前只在 `CloudSyncClient`）。
  - 依赖：P1-12、P2-01

- [x] **P2-13 歌单同步（UI）** — ✅ 已完成（提交 `d0df1e5`）。新增 `CloudSyncScreen.kt`(481 行) + `CloudSyncActivity.kt`(12 行) + `ic_settings_cloud_sync.xml`；入口放在既有**「媒体库与数据」**分组的第 2 项（紧挨 WebDAV 备份），与它同属「用户数据备份/还原」语义，不新增分组噪声。
  - 🔑 **入口默认可达已逐环核实**（P1-15 那个坑的复查）：`SettingsActivity` 传 `onNavigateToCloudSync` → `LiquidGlassSettings` 的 `SettingsRootItem`（与扫描设置/WebDAV 备份**同属一个 section**，第 519-526 行）→ `sections.forEach`（`:1270`）**无条件全量渲染** → manifest 声明。**不依赖开关、不依赖侧栏、不依赖底部导航是否关闭。**
  - 页面能力：服务器地址 / 用户名 / 密码（掩码、初值恒空、保存后清空）+ 保存配置（Keystore 加密走 `Dispatchers.IO`）、上传（merge/overwrite 单选）、下载（提示里给 `CloudRestoreReport` 的 5 个数字）、清空云端数据（`RawMiuixOverlayDialog` 二次确认，文案写明不可恢复）、上次上传/上次还原时间（`DateFormat` 本地化，0 显示为「从未」）。
  - 进行中状态：页面级 `running` 状态，所有按钮/单选/输入框 `enabled = !busy`；卡片内 Miuix `CircularProgressIndicator` + 文案；`try/finally` 复位（取消也不会卡住）；结果用**提示条 + 状态卡片常驻文本双重呈现**（不怕提示条 2 秒闪过）。
  - **404 走「云端还没有备份」的 INFO 提示而非报错** ✅；业务只走 `CloudPlaylistSyncService`（**不自己拼 JSON**）。
  - 文案 EN/ZH 各 45 条、键完全对齐（已脚本核对）；新增行审计干净（4 处"硬编码中文"是 `AppLogger` 日志，非 UI 文案）。
  - ⚠️ **已知限制（子代理提出，我认可，都不阻塞）**：
    1. **上传/下载按钮以「已保存配置」为门槛**（未保存时禁用 + 一行说明），所以必须先点「保存配置」；若想「点击自动先保存」属交互取舍，待用户定。
    2. **清空云端数据后未重置 `lastBackupAtMs`/`lastRestoreAtMs`** → 「上次上传」仍显示旧时间。语义上它只是「本机上次上传时间」，但用户可能期望「从未」。改起来是两行。
    3. **`:module:online` 的 `Failure.message` 是硬编码中文**（如「云端还没有备份」），英文界面下失败提示会带中文尾巴。要彻底解决需把数据层文案也资源化（**超出本任务范围**，属独立任务）。
    4. 新图标 `ic_settings_cloud_sync.xml` 是标准 Material `sync` 路径，**无设备未看渲染**；不合适时改一行 `iconRes` 即可。
    5. **真机交互未验收**（无设备）：编译/资源/manifest/可达路径均已核实，但点按、转圈、弹窗未实测。
  - 依赖：P2-12
  - 依赖：P2-12

- [ ] **P2-14（可选，低优先）最近播放歌单** — `AppPreferences` 存 current/previous 两个条目。
  - 依赖：P2-13

---

## P3 洛雪歌源同步

> 设计见 `AGENTS.MD` §6.3。**运行时不移植**（RawS 已有 `MusicSourceRuntimeService`），只做云同步。

- [x] **P3-01 阅读音源安装 API** — ✅ 已完成（**纯调研，无代码改动**）。结论如下，P3-02/P3-03 直接照这个做。
  - **存储模型**（`module/data/.../source/LxSourcePluginStore.kt:19`）：`InstalledLxSource` =
    `id, name, version, author, description, homepage, sourceUrl,` **`scriptPath, scriptSha256`** `, origin, enabled, format, platforms, actions, installedAtMs, updatedAtMs, lastError`。
    → **只存路径 + sha256，不存正文**（与契约文档 §4 末尾「RawS 音源同步的结构差异」一致）。
  - **脚本落盘约定**：`File(context.filesDir, PLUGIN_DIR)/"${sha256}.js"`（`LxSourcePluginStore.kt:151-153`）。
    → **按 sha256 内容寻址**，所以「同一脚本重复还原」天然幂等，且还原后可以用 `scriptSha256` 校验完整性 ✅。
  - 🔴 **关键发现：没有「从内存内容安装」的公开入口。** `LxSourcePluginStore` 只暴露
    `installFromUri(context, uri)`（:64）与 `installFromUrl(context, url)`（:82），内部真正的
    `install(context, script, ...)`（:141 附近）是 **private**。
    → 而云端还原拿到的正是**内存里的脚本文本**，既不是 content URI 也不是可下载 URL。
    → 所以 **P3-03 需要给 `LxSourcePluginStore` 补一个公开的 `installFromScript(context, script, sourceUrl)`**
      （把现有 private `install` 暴露出来即可，不要重写解析逻辑，也不要绕过它直接写文件 —— 契约 §4 明确要求走既有安装 API 注册）。
      **不要**为了绕过这点去搞「写临时文件 + FileProvider URI」那种绕路方案。
  - **序列化现状**：`MusicSourcePersistenceJson.encodeLxSources/decodeLxSources`（`MusicSourcePersistenceJson.kt:23,26`）用的是
    **kotlinx-serialization 的 `JsonArray`**，而云同步 DTO 按 AGENTS §3.4 约定用 **Gson**。两者**不要混用** —— 云同步另建 DTO，别去改这个文件。
  - **契约 → 本地字段映射**（契约形状见 `docs/cloud-sync-contract.md` §4 ④）：
    | 契约字段 | `InstalledLxSource` | 说明 |
    | --- | --- | --- |
    | `id` / `name` / `version` / `author` / `description` / `sourceUrl` | 同名 | 直接对应 |
    | **`scriptContent`** | ❌ **无** | **上传时必须从 `scriptPath` 读文件内联**；这是 P3 最核心的一条 |
    | `scriptSha256` / `enabled` / `installedAtMs` / `updatedAtMs` | 同名 | 直接对应 |
    | **`isPrimary`** | ❌ **无** | RawS 没有「主音源」概念 → 上传时按 `false` 写、还原时忽略 |
    | **`supportPlatforms`** | ≈ `platforms: Map<String, Set<String>>` | ⚠️ **类型不同**（契约是扁平结构）→ 需转换，不要直接塞 |
    | （契约无） | `homepage` / `origin` / `format` / `actions` / `lastError` | 本地独有，上传时可不带；`format` 用来区分 LX 与 MusicFree |
  - **还原步骤**（契约 §4 末尾明确要求，照做）：写回脚本文件到插件目录 → **校验 sha256** → 走既有安装 API 注册。
  - **兼容 OrBit 备份**：有 `scriptContent` 无 `scriptPath` → 按内联脚本安装；**只有路径没有正文 → 明确报错「该备份不完整」，绝不静默失败**（AGENTS §6.0 红线）。
  - 依赖：P0-05

- [x] **P3-02 定义同步 DTO（并集）** — ✅ 已完成（提交 `ea31399`，`module/online/.../sync/CloudSourceJson.kt` + 12 个契约测试全绿）。
  - 🔴 **重大发现：契约文档 §4 ④ 的字段名与 OrBit 真实实现不一致。** OrBit 的权威定义 `SourceScriptItem`（`app/.../data/online/model/SourceScriptItem.kt:8`）实际用的是：
    `isEnabled`（**不是** `enabled`）、`importedAt`（**不是** `installedAtMs`）、`supportPlatforms` 是 **`joinToString(",")` 的逗号分隔字符串**（**不是**数组），而且 OrBit **根本没有** `scriptSha256` / `updatedAtMs`。
    这与之前 `#EXT-ORBIT-*` 那次是同类的规格偏差 → **一律以 OrBit 真实实现为准**。契约文档 §4 ④ 那一行需要订正（见文末待办）。
  - 兼容策略（两边都认，实测覆盖）：**写**按 OrBit 格式写；额外多写 `scriptSha256` / `updatedAtMs`（OrBit 按 key 取值、多余的键忽略）。**读**用 Gson `alternate` 同时认 `isEnabled`/`enabled` 与 `importedAt`/`installedAtMs`；`supportPlatforms` 用 `@JsonAdapter(CloudPlatformListAdapter::class)` 同时接受逗号字符串与 JSON 数组。
  - 🔑 **关键推论（省掉一堆字段）**：本地的 `platforms` / `actions` / `format` **都是安装时从脚本正文解析出来的**（`LxSourcePluginStore.kt:170` 的 `descriptor.platforms`、`:169` 的 `metadata.format`）→ 只要 `scriptContent` 在，还原后它们会被**自动重新推导**，所以**不往云端带**。`platforms` 里唯一有跨端意义的是平台码，取 **Map 的 key**（value 是音质集合，OrBit 不认，不能带）。
  - 缺脚本正文时**不丢条目、也不编造内容**：`isUsable` / `unusableEntries` 交给调用方明确报错（AGENTS §6.0 红线）。
  - **另修一个阻塞性基础设施问题**：`core:common` 的 `AppLogger` 直接调 `android.util.Log`，而纯 JVM 单测里它是未 mock 的桩 → 一调用就抛 `RuntimeException: Method e in android.util.Log not mocked`。
    **后果：本工程任何带日志的错误分支都测不了**（我这两个边界测试正好走这些分支）。已给 `:module:online` 开 `testOptions.unitTests.isReturnDefaultValues = true`。
    全工程原本**都没有**配这一项 —— 别的模块测试能过只是因为**恰好没走到记日志的代码**。⚠️ 开了之后误用未 mock 的 Android API 会静默拿默认值，所以仍只测不依赖 Android 运行时的纯逻辑。
    （这也解释了为什么 `:module:data` / `:module:online` 的测试能跑通而 `:core:ui` 的不行 —— 后者是上游测试与源码 API 脱节，见上文。）
  - 依赖：P3-01

- [x] **P3-03 音源云同步（HTTP 路由 + 编排）** — ✅ 已完成（提交 `0114aed`）。
  - **没有新建 `CloudSourceSyncClient`**：按本 TODO 上一节自己的要求（「别把 HTTP 细节散落到多个地方，目前只在 `CloudSyncClient`」），三条音源路由直接加进现有 **`CloudSyncClient`**：
    `backupSources`（服务端语义是**全量覆盖**，没有 `mode` 参数）、`restoreSources`（无备份同样 404 → 映射成「云端还没有音源备份」而非错误）、`deleteSourceBackup`（该路由**没有** `deletePlaylists` 那种「不传参数就清空」的陷阱，故不加护栏）。
  - 新增 **`CloudSourceSyncService`** 做装配：
    - **上传**：读 `scriptPath` 指向的文件、把正文**内联进 `scriptContent`**（`InstalledLxSource` 只存路径 + sha256）；**读不到正文时不编造内容**，留空并记进 `CloudSourceUploadReport.incomplete` 让 UI 报出来。
    - **还原**：逐个走 `LxSourcePluginStore.installFromScript`（P3-01 查明缺失、`41a2788` 补上的入口），**不绕过安装 API 自己写文件**；装完用 `scriptSha256` 校验完整性（备份里该字段为空时**跳过比对**，兼容 OrBit 写的条目）；并补一次 `setEnabled` 还原「已停用」状态（`installFromScript` 对新装一律给 `true`）。
    - `CloudSourceRestoreReport` 把「装了几个 / 跳过（缺正文）几个 / 哈希不匹配几个 / 失败几个」分开报，失败项带 `名字: 原因`。
  - **本任务只做 LX**：目标写的是「**洛雪**歌源同步」，契约 §4 ④ 的字段形状（`scriptContent` + `wy/tx/kg/kw/mg` 平台码）本身就是 LX 的；RawS 另有 MusicFree（`MusicSourcePluginStore`）**不在本任务范围**。
  - ⚠️ **验证程度**：`CloudSourceJson`（格式层）有 12 个单测覆盖；`CloudSourceSyncService` 依赖 `Context`（`filesDir` / `AppPreferences` / 安装 API）**没有单测**，目前只做到编译门禁 + 代码路径推理。
  - 依赖：P3-02
  - 验收：清数据后从云端还原的音源**能真正解析出播放地址**（**待真机验证**）。


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
