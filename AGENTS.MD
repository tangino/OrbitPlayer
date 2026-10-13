# AGENTS.MD — OrBitPlayer → RawS-Music 功能移植工程规范与计划

> **本文件是移植工程的权威规范。** 动 `RawS-Music/` 下的代码前，必须先读本文件对应章节。
> - 任务清单 / 进度：同目录 [`TODO.md`](TODO.md)（唯一进度来源）
> - 云同步接口契约（**做同步前必读**）：[`docs/cloud-sync-contract.md`](docs/cloud-sync-contract.md)
> - 源→目标映射与文件索引（**开工前必读**）：[`docs/porting-reference.md`](docs/porting-reference.md)
> - `RawS-Music/AGENTS.md` 是精简版规则指针，指回本文件。

版本：v1.0

---

## 0. 阅读顺序

| 你要做什么 | 必读 |
| --- | --- |
| 第一次接手 | 全文，重点 §1、§3、§4 |
| 开始写代码 | §1.3 铁律、§4 UI 规范、§5 代码规范 |
| 配置环境 / 编译 | §2 |
| 移植某功能 | **[`docs/porting-design.md`](docs/porting-design.md) 对应小节**（§6.1~§6.5 详案）+ 本文件 §6.0 红线 + §4 UI + §5 代码 + `docs/porting-reference.md` |
| 写云同步 | [`docs/porting-design.md`](docs/porting-design.md) §6.5 + `docs/cloud-sync-contract.md` |
| 提交前自检 | §8 + §10 |

---

## 1. 工程目标

### 1.1 三个位置

| 代号 | 绝对路径 | 说明 | 角色 |
| --- | --- | --- | --- |
| **OrBit** | `D:\development\OrBitPlayer`（工作区根，含 `app/`） | 用户自研播放器，`com.orbit.music`，根工程名 `MusicEqualizer` | **源码来源（只读）** |
| **RawS** | `D:\development\OrBitPlayer\RawS-Music` | GitHub 开源播放器（`tangino/RawS-Music`，`main`），`com.rawsmusic` | **移植目标（唯一可写）** |
| **Server** | `D:\development\OrBitPlayer\server\orbit` | Cloudflare Worker + D1（worker `orbit`，D1 `orbit_db`） | 同步后端，**复用，默认不改** |

其他目录（`poweramp/`、`qqmusic/`、`yaoyin/`、`yunting/`、`HotDownloader-main/`、`installer/`、`ghidra_*`、`tmp_*`）与本移植无关，不要动。

### 1.2 移植目标：四大功能（完整移植，不做精简版）

1. **在线歌单广场** — 全平台热门在线歌单 + 歌手库 + 全网搜歌
2. **歌单管理** — 我喜欢的歌 / 我不喜欢的歌 / 歌单同步 / QQ音乐与酷狗音乐登录歌单导入 / 其他歌单管理能力
3. **洛雪歌源同步** — LX 音源的云端同步与多端共享
4. **频谱可视化** — 移植 OrBit 的可视化形态与视觉参数体系

**所有数据同步统一走 Cloudflare D1**（`server/orbit`）。

### 1.3 铁律（Hard Rules）

1. **只写 `RawS-Music/` 下的代码。** `app/`（OrBit）只读参考。
2. **UI 必须遵循 RawS-Music，不得抄 OrBit 的 UI。** OrBit 的 Compose 界面**一律不移植**；只移植**数据 / 逻辑 / 算法**，界面 100% 用 RawS 的 Miuix 体系重写。详见 §4。
3. **云同步契约必须与 `server/orbit` 现有实现逐字一致**（字段名、JSON 形状、`mode` 语义）。客户端适配后端，**不改后端**。详见 `docs/cloud-sync-contract.md`。
4. **不得破坏 RawS 既有功能。** 数据库变更必须提供 `Migration`；新页面必须注册进 `NavScene` + `ComposeNavHost`（+ 必要时 `AppSideRail`）。
5. **RawS 已有能力优先复用，禁止重复造轮子。** 移植前必查 §3.5。
6. **文案必须走 `stringResource`，且两个模块默认语言不同**（§4.7）。禁止硬编码中文。
7. **不许把 OrBit 的 `SQLiteOpenHelper` 直连代码带过来。** RawS 用 Room：Entity + DAO + Migration。
8. **不许引入新的 DI 框架 / Retrofit / Coil / Navigation-Compose / Room 替代品。** RawS 是手工单例 + OkHttp + Room，见 §5.2。
9. **每个任务收尾必须能编过**：`:app:assembleDebug` 通过 + 跑通该功能的 §8 验收项。
10. **唯一事实来源是本文件 + `TODO.md`。** 改了架构决策必须同步更新本文件 §9。

---

## 2. 环境与构建

### 2.1 SDK / JDK

| 平台 | Android SDK | JDK |
| --- | --- | --- |
| Windows（本机） | `E:\softwares\Android\sdk` | **`D:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`** |
| macOS | `/Volumes/BOOTCAMP/Android/sdk` | Android Studio 自带 jbr（21） |

⚠️ 本机的 `E:\softwares\Android_Studio\jbr` 是 **JDK 25**，**Gradle 8.11.1 不能跑在它上面**；RawS 要求 **JDK 21**（见 `docs/BUILDING.md`）。另一个 Temurin 25 在 `D:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`，同样不可用。

本机 SDK 组件：`platforms/android-37.0`、`build-tools 34/35/36`、`ndk 25.1.8937393 / 26.1.10909125 / **27.0.12077973** / 28.2.13676358`、`cmake 3.22.1`。

### 2.2 环境状态（**本机已配置完毕，可直接构建**）

> **快照基线**：本规范的路径以 **RawS-Music `main` @ `8680d85`**（含 Release 1.0.13）为准，实测构建通过。
> 上游提交活跃，**文件/符号名是权威依据，行号仅作参考**（漂移时以符号搜索为准）。

**已完成（本机）**：删掉 `gradle.properties` 里失效的 `org.gradle.java.home`（原值 `D:\ASTUDIO\jbr` 不存在）；创建 `local.properties`（`sdk.dir=E\:\softwares\Android\sdk`，macOS 用 `/Volumes/BOOTCAMP/Android/sdk`）；JDK 21 配在**用户级** `~/.gradle/gradle.properties`（仓库保持可移植）；wrapper `8.11.1` 首次运行自动下载、Daemon JVM 已实测为 JDK 21；NDK `27.0.12077973` 由 AGP 自动安装；`jniLibs` 预编译库就位；`focus-api`/`shizuku` catalog 条目上游已补齐。

**验证结果**：`.\gradlew.bat :app:compileReleaseKotlin --console=plain` → **BUILD SUCCESSFUL**（约 10 分钟，含全部模块）。

**原生库策略（决定本工程的构建方式）**：RawS 公开仓库**缺少 70 个 USB 独占原生源文件** —— `CMakeLists.txt` 引用 144 个 `.cpp`，实际只有 74 个存在，缺的**全部**是 `usb_audio_engine.cpp`、`usb_hardware_volume.cpp`、`raw_usb_*.cpp`。这正是官方 `docs/BUILDING.md` 所说的「完整 USB 独占 Native 核心不在当前公开仓库中」。（CMake 在 `add_library(rawsmusic_usb ...)` 处直接报 `Cannot find source file`。）

→ **已把 `app/build.gradle.kts` 的两处 `externalNativeBuild` 注释掉**，改为**直接打包 `src/main/jniLibs/arm64-v8a/` 下的预编译 `.so`**：`librawscoreservice.so`、`librawsmusic_ffmpeg.so`、`librawsmusic_ffmpeg_live.so`、`librawsmusic_audio_transcode.so`、`librawsmusic_sacd.so`、4 个 FFmpeg 拆分库，以及 AAR 自带的 `libmmkv.so` / `libonnxruntime4j_jni.so` / `librenderscript-toolkit.so` / `libandroidx.graphics.path.so`。
这套 `.so` 取自官方 **1.0.13 release**，JNI 与本源码同版本，且**已包含 USB 独占实现**（比从源码编译时砍掉 USB 更完整），也正是 CMake 原本会产出的最终产物集合（其余目标都是 OBJECT/STATIC，不进 APK）。

→ **取舍**：优点是 APK 可构建、USB 独占可用、构建快（跳过 NDK）；约束是**原生代码被冻结**（改 `app/src/main/cpp/` 不生效）。**本移植的 4 个功能都不需要改原生代码**（可视化复用既有 FFT 管线，其余纯 Kotlin），因此不受影响。若将来必须改原生代码，需先补齐缺失的 USB 源文件再恢复 `externalNativeBuild`。见 §9 D13。

### 2.3 构建命令

```powershell
$env:ANDROID_HOME = "E:\softwares\Android\sdk"
cd D:\development\OrBitPlayer\RawS-Music

# Kotlin-only 检查（官方推荐，跳过 NDK；全模块约 10 分钟）
.\gradlew.bat :app:compileReleaseKotlin --console=plain

# 单模块快速校验
.\gradlew.bat :core:ui:compileReleaseKotlin --console=plain

# 完整构建（含 NDK/CMake）
.\gradlew.bat assembleRelease --console=plain
```

macOS：把 `ANDROID_HOME` 换成 `/Volumes/BOOTCAMP/Android/sdk`，其余相同。

注意：

- RawS 用 **Gradle wrapper 8.11.1**（OrBit 是 8.4，勿混用）；**必须 JDK 21**。
- 本机 `org.gradle.java.home` 已配在**用户级** `~/.gradle/gradle.properties`，所以直接跑 `gradlew` 即可，**不需要**先设 `JAVA_HOME`。
- `:app` 走 NDK/CMake 原生构建（`app/src/main/cpp/CMakeLists.txt`，cmake 3.22.1）。只改 Kotlin 时用上面的 `compileReleaseKotlin` 可完全跳过。
- 产物：`app/build/outputs/apk/release/app-release.apk`（release 用 debug 签名，官方也这么说明）。
- ⚠️ 官方 `docs/BUILDING.md` 已声明：公开仓库**不是**发行 APK 的完整可复现构建。本工程按上一节的「原生库策略」用预编译 `.so` 绕过该限制。

### 2.4 版本基线（勿随意升级）

RawS：Kotlin `2.3.20`、AGP `8.10.0`、Gradle `8.11.1`、CompileSdk/TargetSdk `37`、MinSdk `24`（backdrop/lyric 为 23）、Compose BOM `2025.10.01`、Miuix `0.9.2`、OkHttp `4.12.0`、Room `2.7.1`、Gson `2.10.1`、MMKV `1.3.3`、Coroutines `1.7.3`、applicationId `com.rawsmusic`。

OrBit（仅对照，**不要引入**）：Compose BOM `2024.04.01`、Media3 `1.3.1`、Coil `2.6.0`、Haze `0.7.3`、ZXing `3.5.3`、coreLibraryDesugaring `2.0.4`。

> 唯一建议新增的第三方依赖：**ZXing core**（QQ / 酷狗扫码登录），加到 `:module:online`。

---

## 3. RawS-Music 架构基线

### 3.1 模块图

```
:app                  com.rawsmusic              — Activity、页面装配、MainActivity(数千行)、settings/*
:backdrop             com.kyant.backdrop         — 第三方毛玻璃库
:core:common          com.rawsmusic.core.common — 模型、工具、source 协议、ffmpeg/taglib 桥、AppLogger、AppNoticeBus
:core:ui              com.rawsmusic.core.ui     — 主题、场景/导航、全部 Compose 页面与控件
:module:data          com.rawsmusic.module.data — Room、DAO、AppPreferences(MMKV)、音源插件存储、在线播放控制
:module:player        com.rawsmusic.module.player   — FFmpeg 播放、DSP、USB DAC、频谱分析
:module:scanner       com.rawsmusic.module.scanner  — 媒体扫描、WebDAV 客户端
:module:system-api-stubs — 隐藏系统 API 的 Java stub（IConnectivityManager / INetworkManagementService），仅供反射编译期使用
:lyric:model / :lyric:bridge:provider — 歌词模型与系统歌词 Provider
```

依赖方向：`app → core:ui → module:data → core:common`；`module:player → module:data, module:scanner`。
`settings.gradle.kts` 显式 `include(...)`，**新增模块必须在此登记**。

### 3.2 导航与页面注册（新增页面的标准流程）

RawS **不用** Navigation-Compose 的 route 字符串，用自研枚举 + 单一 NavHost：

| 文件 | 作用 |
| --- | --- |
| `core/ui/.../scene/NavScene.kt` | 场景枚举：`id`(Int 唯一)、`tag`、`label`、`icon`。**当前最大 id = 53**（`ARTIST_ALL_SONGS(52)`、`HDR_SETTINGS(53)`）→ **我们的新场景从 54 起**。还有 `topLevel` / `bottomNavigationEntries` / `homeEntries` / `isDetail()` |
| `core/ui/.../scene/NavigationState.kt` | 导航栈与 `navigateTo(scene, argument)` |
| `core/ui/.../scene/ComposeNavHost.kt` | 巨型 `when (scene) { ... }` 映射场景→页面（约 862 行） |
| `core/ui/.../scene/AppSideRail.kt` | 侧栏 `AppSideRailDestination` 枚举 + 条目表 |
| `app/.../MainActivity.kt` | 装配、状态透传、侧栏跳转 |
| `app/.../AppPageRenderer.kt` | 部分 NavScene → 独立 `*Activity` |

**新增页面的完整改动清单**（照抄 `SOURCE_IMPORT`）：

1. `NavScene` 加常量（**id 顺延，勿复用/插队**）。
2. 按需登记进 `topLevel` / `bottomNavigationEntries` / `homeEntries`。
3. 页面放 `core/ui/src/main/kotlin/com/rawsmusic/core/ui/scene/pages/XxxPage.kt`，签名 `internal fun XxxPage(..., onBack: () -> Unit)`。
4. `ComposeNavHost.kt` 的 `when` 加 `NavScene.XXX -> XxxPage(...)`。
5. 入口：`AppSideRailDestination` + `AppSideRail.kt` 条目 + `MainActivity` 的 `when (destination)` 分支。
6. 状态/回调按现有 `data.xxx` / `callbacks.xxx` 模式透传。
7. 若需独立 Activity：`AppPageRenderer.kt` 注册 + `AndroidManifest.xml` 声明（`@style/SettingsActivityTheme`、`launchMode="singleTop"`、`enableOnBackInvokedCallback="true"`）。

> **新功能尽量只加 1 个 NavScene**，内部用二级 tab 切换，避免污染底部导航。
> 现成范例：`SourceImportPage`（`NavScene.SOURCE_IMPORT`）自带 `SourcePortalTab { Sources, Configuration, Downloads }` + `SourcePortalBottomNavigation` + 独立返回栈 `SourcePortalBackRuntime`。

### 3.3 数据层

**Room —— 唯一持久化结构化数据的地方**

- `module/data/.../db/MusicDatabase.kt`：`DB_NAME = "rawsmusic.db"`，**当前 version = 6**（`MIGRATION_5_6` 已被 `scan_directory` 占用 → **我们的新表必须走 `MIGRATION_6_7`**），`exportSchema = true`，schema 输出到 `module/data/schemas/com.rawsmusic.module.data.db.MusicDatabase/`。
- 现有实体：`FolderEntity`、`FolderFileEntity`、`PlaylistEntity`、`PlaylistEntryEntity`、`SearchHistoryEntity`、`PlaybackStatEntity`、`PlaybackHistoryEntity`、`DailyListenEntity`、`UserPlaylistEntity`、`UserPlaylistSongEntity`、`ScanDirectoryEntity`。
- 现有 DAO：`FolderDao`、`FolderFileDao`、`PlaylistRoomDao`、`PlaylistEntryDao`、`SearchHistoryDao`、`PlaybackStatsDao`、`UserPlaylistDao`。
- 迁移：手写 `Migration(n, n+1)` + `db.execSQL(...)`，在 `getInstance()` 的 `.addMigrations(...)` 注册。**SQL 必须与 Room 生成的 schema 完全一致**（列名/类型/NOT NULL/索引名 `index_<table>_<cols>`）。改完必须比对 `module/data/schemas/*.json`。
- Room 编译器：`module:data` 用 **KSP**（`ksp { arg("room.schemaLocation", ...) }`）；`app`/`core:ui`/`module:player` 用 kapt。

**偏好设置 —— 非结构化键值**

- `module/data/.../prefs/AppPreferences.kt`（1984 行）是**唯一偏好入口**：`object AppPreferences` + 嵌套 `object Player / UI / Library / Scanner / AlbumArt / Equalizer / Lyrics / WebDav / ...`，字段是读写 `MMKV.defaultMMKV()` 的 `var xxx: T`。
- 新增偏好：加嵌套 object + 字段，**不要新建 SharedPreferences**（OrBit 用 SharedPreferences，移植时必须改）。
- `core/common/.../prefs/UIPreferences.kt` 是另一处 UI 偏好（主题相关）；MMKV 初始化在 `core/common/.../CoreInit.kt`。

**Repository / 状态**

- 主仓库 `module/data/.../repository/MusicRepository.kt`（41 KB）；歌单 `module/data/.../prefs/PlaylistStore.kt`（21 KB）。
- 约定：`companion object` + `@Volatile INSTANCE`，或纯 `object`。**无 DI**。

### 3.4 网络层

- **只有 OkHttp 4.12.0。没有 Retrofit / Ktor / Coil 网络栈。**
- 各模块依赖现状（**重要**）：

| 模块 | 有 OkHttp？ | 备注 |
| --- | --- | --- |
| `:app` | ✅ | |
| `:module:scanner` | ✅ | `webdav/WebDavClient.kt` |
| `:core:common` | ❌ | |
| `:module:data` | ❌ | 只有 Gson/MMKV/Room |
| `:module:player` | ❌ | |

→ 网络代码若落在 `:module:data`，**必须给该模块加 `implementation(libs.okhttp)`**；若新建 `:module:online`（推荐），在模块内加即可（§6.1）。

- JSON：RawS 以 **Gson** 为主，也有 `kotlinx-serialization-json`。移植过来的平台解析代码大量用 **`org.json`**（Android 内置，零依赖）。
  **约定**：平台接口解析用 `org.json`（降低移植风险）；持久化与云同步 DTO 用 **Gson**（与 `module:data` 一致）。

### 3.5 可复用能力清单（**移植前必查**）

| 能力 | 现有实现（相对 `RawS-Music\`） | 结论 |
| --- | --- | --- |
| 在线音源插件协议 | `core/common/.../source/RawMusicSourceProtocol.kt`（`RawMusicSourcePlugin` / `RawSourceMediaItem` / `RawSourceQuality` / `RawSourceCapability`） | **复用**，新平台适配层与之对接 |
| 洛雪 LX 兼容 | `core/common/.../source/lx/LxCompatProtocol.kt`、`LxSourceScriptParser.kt` | **已有，勿重写** |
| MusicFree 兼容 | `core/common/.../source/musicfree/*` | 已有 |
| 音源安装/存储 | `module/data/.../source/LxSourcePluginStore.kt`、`MusicSourcePluginStore.kt`、`MusicSourcePersistenceJson.kt`（`InstalledLxSource` / `InstalledMusicSource`） | **复用**（云同步在其上做序列化） |
| LX / 音源运行时 | `module/data/.../source/runtime/MusicSourceRuntimeService.kt`（109 KB）、`MusicSourceRuntimeClient.kt`、`LxCatalogSearchService.kt`、`LxCatalogLyricService.kt` | **复用**：搜索/歌词/解析已具备 |
| 在线播放解析 | `module/data/.../source/playback/MusicSourceAudioResolver.kt`、`MusicSourcePlaybackController.kt`、`OnlineAudioFileCache.kt`、`MusicSourceDownloadController.kt` | **复用**：广场歌曲直接接入 |
| 实时频谱 | `module/player/.../dsp/RealtimeSpectrumPipeline.kt`、`NativeStereoSpectrumAnalyzer.kt` | **复用**：可视化只做渲染 |
| 可视化控件 | `core/ui/.../widget/player/ComposeAudioVisualizer.kt`（44 KB，6 形态 `Spectrum / Particle / ParticleTrails / PulseRings / EnergyRibbon / Ripple`） | **扩展**，不是重写 |
| 离线频谱分析 | `core/common/.../spectrum/AudioSpectrumAnalysis.kt` + `app/.../ui/analysis/AudioSpectrumAnalysisScreen.kt` | 已有（与实时可视化无关） |
| **云同步页 UI 模板** | `app/.../ui/webdav/WebDavPage.kt`（632 行）、`ui/settings/WebDavBackupScreen.kt`、`module/data/.../prefs/BackupArchiveManager.kt` | **照抄这个风格** |
| 用户提示 | `core/common/.../ui/AppNoticeBus.kt`：`post(msg, icon)` / `error(msg)`，图标 `AppNoticeIcon{PLAYLIST, DOWNLOAD, SHARE, ERROR, INFO, ...}` | **必须用** |
| 日志 | `core/common/.../utils/AppLogger.kt`：`d / i / w / e(tag, msg[, throwable])` | **必须用**，不要 `Log.*` |
| 全局搜索 | `core/ui/.../scene/pages/GlobalSearchScreen.kt`、`scene/GlobalSearchScope.kt` | **扩展**（全网搜歌可作新 scope） |
| 歌单 | `module/data/.../db/UserPlaylistDao.kt`、`entity/UserPlaylistEntities.kt`、`prefs/PlaylistStore.kt` | **扩展** |
| 平台账号登录 | **不存在** | 需完整移植（QQ / 酷狗扫码） |
| 原生平台曲库（网易/QQ/酷狗/酷我/咪咕） | **不存在**（RawS 只支持用户导入的 LX/MusicFree 脚本） | 需完整移植 |
| 歌曲喜欢/不喜欢 | ⚠️ **「喜欢」其实已经存在**：`FAVORITES_PLAYLIST_ID = "favorites"` 系统歌单（名「我喜欢的音乐」）+ `PlaylistStore.favoriteSongKeys/isFavorite/toggleFavorite`，**已打通**播放器（`PlayerService`）、长按菜单（`SongActionSheetHelper`）、歌单页（`PlaylistScreen` 心形与改名/删除保护）、WebDAV 备份（`BackupAndroidPersistence`/`BackupArchiveCodec`）。**「不喜欢」确实不存在。** | **不要照搬 §6.2(A) 的字面计划**：本地曲的喜欢沿用既有歌单，不喜欢与在线曲喜欢走新表。见 §9 **D14** |

---

## 4. UI 设计规范（强制）

> **OrBit 的任何 Compose 代码都不得直接复制。** 界面 100% 用 Miuix 重写。

### 4.1 主题与配色

- 全局包装 `core/ui/.../theme/RawSMusicTheme.kt`，两种配色模式 `ColorThemeMode`：
  - `MIUIX`（默认）：`MiuixTheme` + `ThemeController(colorSchemeMode = ColorSchemeMode.System, keyColor = accentColor, isDark)`
  - `MONET_*`：外层 `RawMonetTheme` 提供 Material3 `colorScheme` + `LocalRawMonet`，内层 `MiuixTheme` 用 Monet accent 作 `keyColor`
- 强调色：`core/ui/.../theme/ColorScheme.kt` — `accentColors`（Blue/Purple/Red/Green/Orange/Pink/Cyan/Teal）、`getCurrentAccentColor()` / `setAccentColor(...)`。
- 主题模式：`ThemeManager.ThemeMode{DARK, LIGHT, SYSTEM}`。
- **页面取色统一走 `themeColors()`**（`core/ui/.../scene/pages/SettingsComponents.kt`），返回 `ThemeColors{background, surface, onSurface, onSurfaceVariant, outline, primary, onPrimary, primaryContainer, onPrimaryContainer, secondaryText}`，已兼容 Miuix 与 Monet 两种模式。
  → 新页面**禁止**直接读 `MiuixTheme.colorScheme` 配硬编码 alpha。
- 页面背景 = `colors.background`；卡片 = `colors.surface`；次要文字 = `colors.secondaryText`；分隔线 = `colors.outline.copy(alpha = 0.15f)`。
- 深浅色由 `isDark` / `luminance()` 推导，**不要用 `isSystemInDarkTheme()` 自行判断**（要跟随用户设置）。
- 改主题后必须 `RawThemeRuntimeState.invalidate()` / `invalidateRawTheme()`（MMKV 不是 Compose State）。

### 4.2 排版、形状与动效 tokens（**没有集中 tokens 文件，按下列实测值对齐**）

**字号**

| 用途 | 值 |
| --- | --- |
| 列表主标题 / 设置项标题 | `15.sp`，`FontWeight.Normal`（**不是 Bold**） |
| 说明 / 摘要 / 元信息 | `11.sp`，`colors.secondaryText` |
| 底部 tab 文字 | `10.sp`，`FontWeight.SemiBold` |

字体：所有 `Text` 显式传 `fontFamily = appFontFamily()`（跟随用户选择的全局字体）。

**圆角**：`8/10/12/14/16/18/20/24.dp`，`999.dp` = 药丸形。`SettingsCard` 固定 `RoundedCornerShape(18.dp)`；列表缩略图 `12.dp`；小图标容器 `8~12.dp`。

**间距**：页面水平 `padding(horizontal = 12.dp)`；卡片内项水平 `16.dp`、垂直 `12.dp`；分组标题 `padding(top = 12.dp, bottom = 4.dp)`；页面底部 `Spacer(Modifier.height(180.dp))`（避免被迷你播放器/底栏遮挡）。

**动效**（house style：decelerate 贝塞尔，无回弹）

| 场景 | 值 |
| --- | --- |
| 共享缓动曲线 | `CubicBezierEasing(0f, 0f, 0.2f, 1f)`（`SelectionTransitionEasing`，被多处重复声明为 `RouteDecelerate` / `Decelerate2` 等） |
| 常规 UI 过渡 | `tween(120..220)` |
| 整屏场景切换 | `NORMAL_ANIM_MS = 320`（`SceneTransitionEngine.kt`），`ROUTE_TRANSITION_MS = TAB_TRANSITION_MS = 320` |
| 提示条 | `NOTICE_ANIMATION_MS = 250`，`NOTICE_VISIBLE_MS = 2000L`（`AppNoticeHost.kt`） |
| 预测性返回 | `PREDICTIVE_BACK_COMPLETE_MS = 220` / `CANCEL_MS = 180`（`SourcePortalPredictiveBack.kt`） |

统一复用 `module/data/.../prefs/TransitionPreferences.kt` + `core/ui/.../scene/SceneTransitionEngine.kt` 的常量，**不要自己拍时长**。

**边到边 inset**：用 `core/ui/.../systemui/RawEdgeToEdgeInsets.kt` 的 `rawStableStatusBarTopPadding()` / `rawStableNavigationBarsPadding()` / `rawStableNavigationBottomPadding()`；在 scaffold 内部用 `unconsumedStatusBarTopPadding()`（`LibraryListChrome.kt`）。**不要直接读裸 `WindowInsets`。**

### 4.3 组件库（Miuix 0.9.2）

**新页面只用 Miuix，不要引入 Material3 组件**（现有少量 Material3 属历史遗留，不要扩散）。

> ⚠️ **Miuix 0.9.2 已没有 `SuperXxx` 系列组件**（`SuperSwitch` / `SuperArrow` / `SuperDialog` / `SuperCard` … 全仓 0 引用）。
> **不要照抄网上 Miuix 0.3~0.5 的示例代码。** 现用名为 `SwitchPreference` / `ArrowPreference` / `SliderPreference` / `OverlayDialog` / `SmallTopAppBar` / `TextButton` 等。

| 用途 | 组件（`top.yukonga.miuix.kmp.*`） |
| --- | --- |
| 页面标题栏 | `basic.SmallTopAppBar(title, color, titleColor, navigationIcon = { IconButton { Icon(MiuixIcons.Regular.Back, ...) } })` |
| 分组标题 | `basic.SmallTitle(text, modifier)` |
| 文本 / 图标 | `basic.Text`、`basic.Icon`、`basic.IconButton` |
| 按钮 | `basic.TextButton`、`basic.Button` |
| 卡片 | `basic.Card`、`basic.CardDefaults` |
| 设置项 | `preference.ArrowPreference` / `SwitchPreference` / `SliderPreference` / `RadioButtonPreference` / `WindowSpinnerPreference` |
| 图标集 | `icon.MiuixIcons` + `icon.extended.*`（`Back`、`Search`、`Music`、`ListView`、`Sort` 等） |
| 下拉 | `basic.DropdownItem` / `basic.DropdownEntry` / `popup.WindowDropdownPopup` / `window.WindowCascadingListPopup` |
| 底部导航 | `core/ui/.../widget/bottombar/LiquidBottomTabs` + `LiquidBottomTab`（见 `SourcePortalChrome.kt`） |
| 毛玻璃 / flow 背景 | `com.kyant.backdrop` + `core/ui/.../widget/flow/RawFlowBackground` / `rememberCurrentRawFlowMode()` |

**三种页面外壳，按场景选一个**（**页面内不要再建 `Scaffold`** —— 全应用只有 `MainActivity.kt` 根部一个 Miuix `Scaffold`）：

| 外壳 | 位置 | 用于 |
| --- | --- | --- |
| `SimplePageScaffold(title, onBack, actions, content)` | `core/ui/.../scene/pages/PageTheme.kt` | 简单返回页 |
| `SettingsPage` + `SectionHeader` + `SettingsCard` + `SettingsNavigationEntry` / `SettingsInfoEntry` | `core/ui/.../scene/pages/SettingsComponents.kt` | 所有设置页 |
| **`LibraryListScaffold(...)`** | `core/ui/.../scene/pages/LibraryListChrome.kt`（约 1244 行） | 所有曲库/集合列表页（已有 16 处调用） |

`LibraryListScaffold` 的 `content` lambda 是 `@Composable BoxScope.(contentTopPadding: Dp, backdropSource: Modifier) -> Unit`：
**必须把 `backdropSource` 链到你的列表/内容根上**，否则毛玻璃顶栏会错位（范例 `PlaylistDetailScreen.kt:46,61`）。

**页面骨架模板**（设置型）

```kotlin
@Composable
internal fun XxxPage(/* 状态入参 */ onBack: () -> Unit) {
    SettingsPage(title = stringResource(R.string.xxx_title), onBack = onBack) {
        SectionHeader(stringResource(R.string.xxx_section))
        SettingsCard {
            SettingsNavigationEntry(
                title = stringResource(R.string.xxx_entry_title),
                description = stringResource(R.string.xxx_entry_summary),
                onClick = { /* ... */ },
            )
        }
        Spacer(Modifier.height(180.dp))
    }
}
```

（`SettingsPage` 内部已处理背景色、`SmallTopAppBar`、`verticalScroll`、`rawStableNavigationBarsPadding()`、`horizontal = 12.dp`、底部 `180.dp` 间距。）

### 4.4 页面状态规范

RawS 页面**普遍不用 ViewModel**：Composable 内 `remember { mutableStateOf(...) }` + `rememberCoroutineScope()`；长生命周期状态放单例 Store/Repository（`StateFlow` + `collectAsState()`）。
→ **移植时不要引入 AndroidX ViewModel**（`OnlinePlaylistViewModel` 那套要改成 Store 单例 + `StateFlow`，或页面局部 state）。
→ Repository 侧：**DAO 不返回 `Flow`**（全是 `suspend`），所以**写入后必须调用 Repository 的刷新/发布路径**，否则 UI 不会更新。

状态呈现方式（RawS 的实际做法，**不是**整页替换）：

| 状态 | 表现 |
| --- | --- |
| 加载中 | 场景**保持挂载**，用透明度叠加 + 淡入（`tween(200, CubicBezierEasing(0f, 0f, 0.2f, 1f))`，见 `MainActivity.kt` 启动场景；`STARTUP_SCENE_MIN_MS = 750L`）；局部加载用 `CircularProgressIndicator` 居中 |
| 空 | 页面内联：`if (list.isEmpty()) Box(Modifier.fillMaxSize().then(backdropSource), contentAlignment = Alignment.Center) { Text(stringResource(R.string.xxx_empty), color = colors.secondaryText, fontSize = 15.sp) }`（范例 `PlaylistDetailScreen.kt:44-65`） |
| 错误 | 页面内错误文案 + 重试；同时 `AppNoticeBus.error(msg)` |

> 全仓**没有**共享的 `LoadingState` / `EmptyState` / `ErrorState` 组件，四态一律**页面内联**实现。

### 4.5 列表 / 卡片 / 缩略图

- **曲库/歌曲/歌单类长列表不用 `LazyColumn`**，用 `LibraryListScaffold` + 自研虚拟列表：
  - 简单用法：`ComposeVirtualListFull(songs, playingSongId, state, contentTopPadding, modifier, onSongClick, onSongLongClick)`（`core/ui/.../widget/virtuallist/ComposeVirtualListFull.kt`）
  - 完整用法：`ComposeVirtualList(songs, state, ...)`（`ComposeVirtualList.kt`，支持选中态、`revealIndexRequest`、分组头、双指缩放、共享元素封面、`VirtualListCustomProvider` 自定义行）
  - 状态必须用 `rememberComposeVirtualListState("<稳定的每页 namespace>")` 并与场景一起在 `ComposeNavHost` 中 hoist（`ComposeNavHost.kt:197-228`），否则场景切换会丢滚动位置。
- **普通内容列表用 `LazyColumn`（86 处，事实标准）**，例如设置页、音源门户、搜索结果（`SourceSearchPage.kt` 就是 `LazyColumn`）。
- **网格的既有做法**：`List.chunked(N)` + `VirtualListCustomProvider(itemHeight, content, handles)`（`core/ui/.../widget/virtuallist/VirtualListCustomProvider.kt`）+ 由 `screenWidthDp` 算出的行高。
  权威范例 `app/.../ui/playlist/PlaylistScreen.kt`：`playlists.chunked(2)` → `playlistRows` → `playlistRowHeight = (cardWidth / 0.86f) + 14.dp` → `VirtualListCustomProvider`。
  > ⚠️ **全仓 `LazyVerticalGrid` 使用次数为 0。** 新页面里若要用它，必须在 §9 登记为**新增约定**（并同步改本节），不要默默引入。
- 封面加载：用现有 `core/ui/.../widget/bitmaps/BitmapImage` + `BitmapProvider`（内存/磁盘分层缓存），**不要引入 Coil/Glide**。远程图片需先确认 `BitmapProvider` 是否支持 http(s)；不支持则自行用 OkHttp 下载 + 本地缓存（参考 `MusicSourceArtworkRepository.kt`）。
- 弹窗一律 `RawMiuixOverlayDialog(show, title, summary, onDismissRequest, content)`（`core/ui/.../widget/RawMiuixOverlayDialog.kt`）—— 内部包了 Miuix `OverlayDialog` 并注册预测性返回归属。**不要**新写裸 `Dialog` / `AlertDialog`。
- 底部动作面板用 `Modifier.predictiveBottomSheetMotion` / `predictiveBottomSheetScrim`（`PredictiveDialogMotion.kt`）+ 手写 overlay（**不是** Material3 `ModalBottomSheet`）。
- 长按菜单参考 `SongActionSheetHelper.kt` / `LibraryMoreDialog.kt`。

### 4.6 图标

- 优先 `MiuixIcons`。
- 自定义图标放 `app/src/main/res/drawable/`，命名 `ic_<domain>_<name>.xml`，vector drawable，tint 跟随 `LocalContentColor`。

### 4.7 文案与多语言（**坑：各模块默认语言不一致**）

| 模块 | 默认（`values/`） | 其他 |
| --- | --- | --- |
| `core:ui` | **中文**（62 KB） | `values-en/` 英文（27 KB，**只翻译了一部分**） |
| `app` | **英文**（199 KB） | `values-zh/` 中文（193 KB） |
| `module:player` | **英文** | `values-zh/` 中文（仅 1 条） |
| `module:scanner` | **英文** | `values-zh/` 中文 |
| `core:common` / `backdrop` / `lyric:*` | 无资源 | — |

规则：
- 新增 `core:ui` 文案：**中文**写 `core/ui/src/main/res/values/strings.xml`，**英文**写 `values-en/strings.xml`。
- 新增 `app` 文案：**英文**写 `app/src/main/res/values/strings.xml`，**中文**写 `values-zh/strings.xml`。
- 新增 `module:player` / `module:scanner` 文案：**英文**写 `values/`，**中文**写 `values-zh/`（**注意与 core:ui 相反**）。
- 新建 `:module:online`：沿用 `core:ui` 的习惯 —— **中文** `values/`、**英文** `values-en/`。这是**新约定**，与 `module:player`/`module:scanner` 不一致，属有意为之（模块本身以中文为主要市场）。
- key 命名：`<domain>_<thing>[_summary]`，如 `online_plaza_tab_recommend`、`online_sync_upload_success`。
- **禁止硬编码中文**（RawS 现有零星硬编码是历史遗留，不要效仿）。
- 已知噪声：`app` 的英文 `values/strings.xml` 里仍有约 214 行含中日韩字符（历史未翻译残留）。**测试时看到中英混排不是你的 bug**，不要顺手去"修"。

### 4.8 明确禁止

1. ❌ 复制 OrBit 的 Compose 页面 / 组件。
2. ❌ 引入 Coil / Glide / Hilt / Koin / Retrofit / Navigation-Compose 作为新基建。
3. ❌ 新建 SharedPreferences。
4. ❌ 硬编码颜色（用 `themeColors()`）、硬编码中文字符串、无谓的尺寸魔法数。
5. ❌ 直接 `Log.*`（用 `AppLogger`）。
6. ❌ 在主线程做网络 / 阻塞解析。

---

## 5. 代码规范

### 5.1 包与文件

- 包名前缀固定 `com.rawsmusic.*`，**禁止出现 `com.orbit.*`**。
- 目录约定：
  - 页面：`core/ui/src/main/kotlin/com/rawsmusic/core/ui/scene/pages/XxxPage.kt`（`internal fun XxxPage`），私有子组件同文件 `private fun`
  - 独立 Activity 页面：`app/src/main/kotlin/com/rawsmusic/ui/<domain>/XxxPage.kt` + `ui/settings/XxxActivity.kt`
  - 数据：`module/data/src/main/kotlin/com/rawsmusic/module/data/<domain>/`
  - 跨模块模型：`core/common/src/main/kotlin/com/rawsmusic/core/common/model/`
- 命名：类 `PascalCase`；函数/字段 `camelCase`；常量 `UPPER_SNAKE`；Composable `PascalCase`；StateFlow 私有 `_xxx` / 公开 `xxx`。
- 注释：**中文注释**，解释「为什么」而非「做了什么」，尤其标注规避坑点之处（RawS 大量使用这种注释）。
- 文件规模：RawS 有 80 KB+ 单文件，但**新代码单文件建议 ≤ 800 行**，超出按职责拆分（如 `OnlinePlazaPage.kt` / `OnlinePlazaSections.kt` / `OnlinePlazaDialogs.kt`）。

### 5.2 依赖注入 / 单例

```kotlin
class XxxManager private constructor(context: Context) {
    companion object {
        @Volatile private var INSTANCE: XxxManager? = null
        fun getInstance(context: Context): XxxManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: XxxManager(context.applicationContext).also { INSTANCE = it }
            }
    }
}

// 无状态时
object XxxStore { /* ... */ }
```

### 5.3 协程与线程

- 网络 / 解析 / 文件 IO：`withContext(Dispatchers.IO)` 或 `CoroutineScope(Dispatchers.IO)`。
- Compose 内用 `rememberCoroutineScope().launch`。
- 捕获 `CancellationException` **必须 rethrow**（RawS 现有写法）。
- **禁止**主线程调用阻塞 API。例：OrBit 的 `LxSourceEngine.resolveMusicUrl` 是 `CountDownLatch` 阻塞实现，移植时必须包到 IO 调度器，或改用 RawS 的挂起式 `MusicSourceRuntimeClient`。

### 5.4 错误处理与提示

- 可恢复错误 → 页面内错误文案 + 重试按钮 + `AppNoticeBus.error(...)`。
- 网络失败 → 给明确文案，**不要暴露原始异常堆栈**。
- 日志：`AppLogger.e("OnlinePlaza", "fetch failed", t)`。日志 TAG 用领域名：`OnlineSource`、`OnlinePlaza`、`PlatformAuth`、`CloudSync`、`PlaylistTransfer`、`Visualizer`。
- **严禁** `catch (e: Throwable) {}` 静默吞异常（RawS 少数历史代码有此问题，不要扩散）。

---

## 6. 四大功能移植设计（**详案见 [`docs/porting-design.md`](docs/porting-design.md)**）

> 本节原先约 25 KB，为把 `AGENTS.MD` 压回 65536 B 指令预算内（否则加载时尾部被截断、
> §10/§11 不可见），**完整正文已移到 [`docs/porting-design.md`](docs/porting-design.md)**。
> 那边与本节同等权威；下面的红线**无论是否打开详案都必须遵守**。

| 小节 | 内容 | 详案位置 |
| --- | --- | --- |
| §6.1 | 在线歌单广场（`:module:online`、5 平台适配器、仓库、播放解析适配层、收藏表、UI） | `porting-design.md` §6.1 |
| §6.2 | 歌单管理（(A) 喜欢/不喜欢 (B) 分组 (C) 导入导出 (D) QQ/酷狗登录 (E) 同步 (F) 其他） | 同上 §6.2 |
| §6.3 | 洛雪歌源同步（云端同步 + 可选社区音源广场） | 同上 §6.3 |
| §6.4 | 频谱可视化（5 个 GL 形态 + 3 个 Canvas 形态 + 配色/参数体系） | 同上 §6.4 |
| §6.5 | 云端同步统一接入（D1） | 同上 §6.5 |

### 6.0 红线（不看详案也不能违反）

- **不移植 OrBit 的 `OnlineAudioSourceManager`**（D5）：只摘「匹配打分 / 音质阶梯 / 直链探活」算法，
  播放解析**复用** RawS 既有的 `MusicSourceSearchCoordinator` → `MusicSourceAudioResolver` →
  `MusicSourcePlaybackController`，**不要替换**它们。
- **不移植 QuickJS / `user-api-preload.js`**（D3）：RawS 已有 `MusicSourceRuntimeService`。
- **不移植 `AudioVisualizerManager`**（D4），**不用** `android.media.audiofx.Visualizer`，**不新写 FFT**，
  **不加第二个** `setWaveformConsumerActive(true)` 消费者，**不为可视化申请 `RECORD_AUDIO`**。
- **可视化枚举只追加 `6..13`，严禁重排或复用 `0..5`**（D10）——它是 Int 持久化，重排会让老用户偏好错位。
- **喜欢 / 不喜欢按 D14 的混合方案**：**本地曲的「喜欢」走既有的 `PlaylistStore`**（`FAVORITES_PLAYLIST_ID`
  系统歌单），**不要**再建第二套；「不喜欢」（本地+在线）与「在线曲的喜欢」才走 `song_attitude`。
- **云同步契约必须与 `server/orbit` 逐字一致**（铁律 3），字段名不许猜；**服务器地址用户自填，不内嵌**（D9）；
  喜欢/不喜欢上传时用系统歌单名 `我喜欢的音乐` / `不喜欢的音乐` + `groupName = 系统`。
- **网格沿用 `chunked(N)`**（D11），**不要 `LazyVerticalGrid`**。
- 洛雪音源上传**必须内联脚本正文**（RawS 的 `InstalledLxSource` 只存路径 + sha256）；
  缺正文的备份要**明确报错**，不静默失败。

## 7. Cloud Sync 后端（摘要）

> **完整契约（路由表 / DDL / JSON / 合并语义 / 已知问题）见 [`docs/cloud-sync-contract.md`](docs/cloud-sync-contract.md)。动手前必须读。**

- Worker `orbit` + D1 `orbit_db`（`43bc9f93-4145-4d83-8a09-6f2047f32ff6`），compat date `2026-10-06`，无 KV/R2/DO/Queues。**仓库里查不到线上地址**，必须用户自填。
- **鉴权极简且不安全**：`username` + **明文 `password`** 在每个请求 body；省略 `password` 即**匿名放行**；`/api/user/auth` 自动注册；`SHA-256(password + "_orbit_salt_2026")`。**无 token / session / 签名 / 限流。**
- 本移植只用：`/api/user/auth`、`/api/user/sources/{backup,restore,delete}`、`/api/user/playlists/{backup,restore,delete}`、`/api/user/data/clear`。
- D1 仅 6 张表：`users`、`user_playlists`、`user_playlist_backups`、`source_scripts`、`user_source_backups`、`user_settings`。时间戳为 **epoch 毫秒**，`user_id` == `username`。**无广场表、无 like/dislike 表、无平台 token 表。**
- **喜欢/不喜欢无独立接口**，作为两个系统歌单塞在 `playlists_json`（名 `我喜欢的音乐` / `不喜欢的音乐`，`groupName="系统"`，每首歌带 `isFavorite` / `isDisliked`）。
- 合并语义（默认 `mode="merge"`）：本地歌单按 `name` 合并，歌曲按 `path || "${title}__${artist}"` 去重且**上传值覆盖**；在线收藏按 `id` 合并；分组并集后**永远丢弃 `"默认"`**（`"系统"` 保留）。无分页、无版本控制、last-write-wins。
- **客户端必须容错的服务端缺陷**：① 三个 JSON 列的 `JSON.parse` 未保护，非法 JSON → 500；② `/api/user/playlists/delete` 若不传 `deleteAll` 也不传数组 → **清空全部云端歌单**，必须显式传参；③ 选择性删除不回写 `groups_json`；④ 未知用户名 + `password` 调 restore/delete 会先建 `users` 行再 404。
- **改后端（鉴权/加表/加字段）必须先取得用户明确同意**，并登记到 §9。

---

## 8. 验收标准

### 8.1 通用（每个任务都满足）

- [ ] **编译验证**：`.\gradlew.bat :app:compileReleaseKotlin --console=plain` 通过；改到打包/资源时再跑 `assembleRelease`，并在 `TODO.md` 记录结果。
- [ ] 新增/修改的字符串在**正确的两个语言文件**里都补齐（§4.7，注意 `core:ui` 与 `module:player/scanner` 默认语言相反）。
- [ ] 未出现 `com.orbit.*` 包名；未引入 Coil / Hilt / Retrofit / Navigation-Compose / `LazyVerticalGrid`。
- [ ] 未新增 SharedPreferences；新偏好都在 `AppPreferences`。
- [ ] 数据库变更都有 `Migration`，Room schema 校验通过，`module/data/schemas/*.json` 已更新。
- [ ] 用户可见错误经 `AppNoticeBus`；关键路径有 `AppLogger` 日志。
- [ ] Repository 侧：DAO 写入后已调用刷新/发布路径（否则 UI 不更新）。
- [ ] `TODO.md` 中对应任务状态已更新。

### 8.2 功能级验收

**① 在线歌单广场**
- [ ] 5 个平台都能拉到：推荐/分类歌单、官方榜单、tags。
- [ ] 歌单详情能列出曲目；点击能播放（经 RawS 解析层拿到可播放 URL）。
- [ ] 歌手库：分类浏览、搜索、歌手详情（热门歌曲 / 专辑）可用。
- [ ] 全网搜歌：一次搜索返回多平台结果、可按平台过滤、分页加载、搜索历史持久化。
- [ ] 收藏的在线歌单能持久化（杀进程后仍在），能分组。
- [ ] 单平台接口失败时其它平台仍可用，并给出提示。

**② 歌单管理**
- [ ] 喜欢 / 不喜欢：可从长按菜单设置、可作为伪歌单查看与播放、`clearAllDislikes` 有二次确认。
- [ ] 歌单分组：新建 / 改名 / 删除 / 移动歌单；删分组后歌单回落「默认」。
- [ ] 歌单导出：JSON / M3U8 / 分享 三种成功；导入：链接 / JSON / M3U8 / 纯文本 四种成功。
- [ ] QQ 扫码登录成功，能列出并导入「我创建的」与「我收藏的」歌单。
- [ ] 酷狗扫码登录成功，能列出并导入歌单（fallback 链生效）。
- [ ] 平台凭据落盘为加密存储（不可明文读出）。
- [ ] 退出登录后不再携带旧 cookie 请求。

**③ 洛雪歌源同步**
- [ ] 服务器地址可配置并持久化；账号登录/注册成功。
- [ ] 上传：本机已安装的 LX / MusicFree 音源（**含脚本正文**）上传成功，云端条目数正确。
- [ ] 下载：换设备（或清数据后）能从云端还原音源，**脚本能被 LX 运行时加载并成功解析出播放地址**。
- [ ] 删除单个备份 / 清空云端数据可用。
- [ ] 能识别并兼容 OrBit 写下的 `sources_json`（含内联脚本）。
- [ ] 缺失脚本正文的备份给出明确错误提示，不静默失败。

**④ 频谱可视化**
- [ ] 原有 6 种形态行为不变（回归）。
- [ ] 新增 8 种形态全部可用：`BarsWithPeaks`、`AuroraMountain`、`MirroredBars`、`TimeTunnel`、`Octgrams`、`SoundCity`、`FractalGalaxy`、`QuantumVortex`。
- [ ] 配色方案 5 种生效；自定义双色生效。
- [ ] 参数化生效：柱宽 / 透明度 / 描边（宽、色、透明度、仅描边）/ 单色模式 / 峰值衰减开关。
- [ ] 全屏最大化可视化可用，返回键 / 点按能退出。
- [ ] 老版本偏好（style 0..5）升级后不错位。
- [ ] 可见性切换时 GL 渲染器正确 pause / resume，无内存泄漏；连续切换形态 50 次不崩。
- [ ] 未因可视化新增 `RECORD_AUDIO` 权限请求。

---

## 9. 风险与决策记录

### 决策

| # | 项 | 结论 | 备注 |
| --- | --- | --- | --- |
| D1 | 在线能力放哪 | **新建 `:module:online`**（备选：并入 `:module:data`） | §6.1 |
| D2 | 是否移植 OrBit 的 UI | **否**，只移植数据/算法，UI 全用 Miuix 重写 | 用户明确要求 |
| D3 | 是否移植 LX 运行时（QuickJS） | **否**，RawS 已有 `MusicSourceRuntimeService` | §6.3 |
| D4 | 是否移植 `AudioVisualizerManager` | **否**，RawS 用原生 PCM FFT 管线 | §6.4 |
| D5 | 是否移植 `OnlineAudioSourceManager` | **否**，仅摘匹配 / 探测 / 音质阶梯算法 | §6.1 |
| D6 | 是否修改 Worker | **默认不改**，契约以现有后端为准 | 需改时先取得用户同意 |
| D7 | 平台账号凭据存储 | **加密后存 MMKV**（Keystore + AES-GCM） | §6.2 D |
| D8 | 新增依赖 | 仅 ZXing core（扫码） | §2.4 |
| D9 | 云同步 URL | **用户自填，不内嵌** | 与 OrBit 一致 |
| D10 | 可视化枚举编号 | **只追加 6..13，禁止重排** | §6.4 |
| D11 | 广场/歌手库网格 | **沿用 `chunked(N)` + `VirtualListCustomProvider`**，不引入 `LazyVerticalGrid` | §4.5 / §6.1 |
| D12 | 环境/构建阻塞 | **已全部解决**（见 §2.2）。`docs/ffmpeg-prebuilt.md` 记录的是当时的排查过程 | — |
| D13 | **放弃从源码编译原生库，改用预编译 `.so`** | 公开仓库缺 70 个 USB 原生源文件 → CMake 必然失败；改为打包 `jniLibs/` 中官方 1.0.13 的完整 `.so` 集 | §2.2；代价：**原生代码冻结**（本移植不需要改原生） |
| D14 | **「喜欢」不搬到 `song_attitude`（混合方案，用户拍板）** | 实测 RawS **已有**完整「喜欢」机制（`FAVORITES_PLAYLIST_ID` 系统歌单 + `PlaylistStore`，已打通播放器/长按菜单/歌单页/WebDAV 备份），再建一套会造成**两个事实来源**让心形状态不一致。故：**本地曲的喜欢沿用既有歌单**；**「不喜欢」（本地+在线）与「在线曲的喜欢」**走 `song_attitude` | 修正 §3.5 原「喜欢/不喜欢都不存在」的误判；实现见 `SongAttitudeRepository` |

### 风险

| 风险 | 等级 | 缓解 |
| --- | --- | --- |
| 5 个平台逆向接口随时失效 | 高 | 保留 fallback 链、单平台错误隔离、端点常量集中便于快速修复 |
| 酷狗签名盐 / QQ 登录协议变动 | 高 | 常量集中 + 注释来源；登录失败给可读提示 |
| Worker 无鉴权、密码明文传输 | 高 | 至少 HTTPS + 客户端加密落盘；危险操作二次确认；向用户提示可自行加鉴权（属改后端，需批准） |
| **原生代码冻结**（改用预编译 `.so`） | 中 | 4 个移植功能都无需改原生；若将来需要，先补齐 USB 源文件再恢复 `externalNativeBuild`（§2.2） |
| `sources_json` 结构不兼容（RawS 存路径 / OrBit 存正文） | 中 | 并集 DTO + 双向兼容 + 明确错误提示（§6.3） |
| Room 迁移写错导致 schema 校验失败 | 中 | 每次改 Room 后跑 `:app:assembleDebug` 并比对 `module/data/schemas/*.json` |
| GL 可视化在低端机掉帧/崩溃 | 中 | 限制渲染分辨率、不可见即暂停、同时只保留 1 个存活实例 |
| 工作量大导致上下文丢失 | 中 | 严格按 `TODO.md` 分阶段推进，每个任务独立可交付 |
| `RawS-Music` 是他人仓库，上游可能更新 | 低 | 在独立分支 `feat/orbit-port` 上开发 |

---

## 10. 工作流

### 10.1 分支与提交

```powershell
cd D:\development\OrBitPlayer\RawS-Music
git checkout -b feat/orbit-port          # 不要直接改 main
```

- 提交粒度：一个 `TODO.md` 条目 = 一个**可独立编译通过**的 commit。
- commit message：Conventional Commits + 中文描述，如 `feat(online): 移植网易云歌单广场数据层`。
- **环境适配改动（`gradle.properties` / `local.properties`）单独第一个 commit**，如 `chore(build): 适配本机 SDK/JDK 路径`。

### 10.2 单个任务的执行流程

1. 打开 `TODO.md`，选一个**依赖已满足**的任务，置为「进行中」。
2. 读本文件 §4（UI）、§5（代码）、**§6.0 红线**，以及 [`docs/porting-design.md`](docs/porting-design.md) 里对应的 §6.x 详案；需要时读 `docs/porting-reference.md`（映射与索引）、`docs/cloud-sync-contract.md`（同步契约）。
3. 读 RawS 中**同类**既有实现作为模板（§3.5 对照表）。
4. 读 OrBit 对应源码（只读）摘出算法与常量。
5. 实现 → 编译 → 真机/模拟器验证 §8 验收项。
6. 更新 `TODO.md` 状态 + 勾选验收项。若产生架构决策，更新本文件 §9。
7. 提交。

### 10.3 给 agent 的额外要求

- 「顺手重构 / 优化」**不要做**，只做任务范围内的事。
- 不确定的平台协议细节：**以 OrBit 现有实现为准照搬**，不要「现代化改写」。
- 需要用户决策的事项（改后端、加依赖、改 RawS 既有行为、动 OrBit 源码）：**先问**，不要自己决定。
- 优先按 `TODO.md` 的粒度拆分长任务，避免单次改动过大。

---

## 11. 参考资料

- 源→目标映射 + 文件索引 + 5 平台端点速查 → [`docs/porting-reference.md`](docs/porting-reference.md)
- Cloud Sync 完整契约 → [`docs/cloud-sync-contract.md`](docs/cloud-sync-contract.md)
- 改 Worker 前必读 → [`server/orbit/AGENTS.md`](server/orbit/AGENTS.md)
- RawS 上游说明 → `RawS-Music/README.md`、`RawS-Music/docs/BUILDING.md`、`docs/DEPENDENCIES.md`、`docs/FEATURES.md`
