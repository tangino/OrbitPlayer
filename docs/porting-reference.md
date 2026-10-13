# 移植参考索引（源 → 目标 映射 + 关键文件路径）

> **本文件是从 [`AGENTS.MD`](../AGENTS.MD) §6.0 与 §11 拆出的参考文档。**
> 开工移植任一功能时，先读 `AGENTS.MD` §6 对应小节，再用本文件定位要读/抄/改的文件。

## 1. 源 → 目标 映射总表

`OrBit` = `D:\development\OrBitPlayer\app\src\main\java\com\orbit\music\`
`RawS` = `D:\development\OrBitPlayer\RawS-Music\`

| OrBit 源 | RawS 目标 | 处理方式 |
| --- | --- | --- |
| `data/online/model/OnlineMusicModels.kt` | `core/common/.../model/OnlineMusicModels.kt` | 语义照搬 |
| `data/online/source/IOnlineMusicSource.kt` | `module/online/.../source/OnlinePlatformSource.kt` | 接口改名，方法集不变 |
| `data/online/source/{netease,qq,kugou,kuwo,migu}/*.kt` + `NeteaseCrypto.kt` | `module/online/.../source/<platform>/` | **逻辑照搬**，换 OkHttp 客户端 / `AppLogger` / null 安全 |
| `data/online/repository/OnlineMusicRepository.kt` | `module/online/.../OnlineMusicRepository.kt` | 逻辑照搬，缓存改 RawS 风格 |
| `data/online/repository/OnlinePlaylistFavoriteManager.kt` | `module/online/.../OnlinePlaylistFavoriteStore.kt` + Room 表 | **SharedPreferences → Room** |
| `ui/components/Online{Playlist,Artist,SongSearch}Components.kt` | — | ❌ 不移植，UI 用 Miuix 重写 |
| `ui/viewmodel/Online{Playlist,SongSearch}ViewModel.kt` | `module/online/.../OnlinePlazaStore.kt` 等 | 改写为 Store + `StateFlow` |
| `ui/screens/OnlinePlaylistSquareScreen.kt`、`OnlinePlaylistDetailScreen.kt` | — | ❌ **死代码，绝对不要移植** |
| `data/db/AppDatabase.kt` 的 `song_stats` 表与 `SongDaoImpl` 表态方法 | `module/data` 新表 + DAO + Migration | Room 化 |
| `data/repository/MusicRepository.kt` 的 attitude 方法 | 并入 RawS `MusicRepository` 或新建 `SongAttitudeRepository` | 适配 RawS 模型 |
| `data/playlist/PlaylistGroupManager.kt` | `module/data/.../prefs/PlaylistGroupStore.kt` + Room | SharedPreferences → Room |
| `data/playlist/PlaylistTransferManager.kt` | `module/data/.../playlist/PlaylistTransferManager.kt` | 逻辑基本照搬 |
| `data/repository/PlayedPlaylistManager.kt` | `module/online/.../PlayedPlaylistStore.kt` | 可选 / 低优先 |
| `data/online/auth/**`（`QQMusicAuthService`、`KugouMusicAuthService`、`PlatformAccountManager`、`PlatformAccountModels`、`QrCodeGenerator`） | `module/online/.../auth/**` | 逻辑照搬 + 凭据加密 |
| `ui/components/auth/*`、`ui/components/PlatformLoginDialog.kt`、`PlatformPlaylistSyncDialog.kt` | — | ❌ 不移植，UI 重写 |
| `data/online/engine/CloudPlaylistSyncManager.kt` | `module/online/.../sync/CloudPlaylistSyncClient.kt` | HTTP 契约不变（见 `cloud-sync-contract.md`） |
| `data/online/engine/CloudSourceSyncManager.kt` | `module/online/.../sync/CloudSourceSyncClient.kt` | HTTP 契约不变 |
| `data/online/engine/LxSourceEngine.kt`、`assets/script/user-api-preload.js`、`com/whl/quickjs/**`、`jniLibs/*/libquickjs-android-wrapper.so` | ❌ **不移植** —— RawS 已有 `LxSourceScriptParser` + `MusicSourceRuntimeService` | 只移植云同步 |
| `data/online/engine/OnlineAudioSourceManager.kt` | ❌ **不移植** —— RawS 已有 `MusicSourceAudioResolver` / `MusicSourcePlaybackController` / `MusicSourceRuntimeService` | 只摘匹配/探测/音质阶梯算法 |
| `audio/AudioVisualizerManager.kt` | ❌ **不移植** —— RawS 用 `RealtimeSpectrumPipeline`（原生 PCM FFT，优于 `audiofx.Visualizer`） | 只移植渲染 |
| `data/model/VisualizerStyle.kt` | `AudioVisualizerStyle` 扩展 + 新增 `VisualizerColorScheme` | **只追加**枚举值 6..13 |
| `ui/components/SpectrumVisualizer.kt`（Canvas 3 形态） | 并入或新增 `core/ui/.../widget/player/` 文件 | 算法照搬，样式改 Miuix 取色 |
| `ui/components/*GLRenderer.kt` + `*Visualizer.kt`（5 个 GL 形态） | `core/ui/.../widget/player/gl/` | 照搬 GL 渲染器 + `AndroidView` 包装 |
| `ui/viewmodel/EqualizerViewModel.kt` 中可视化相关字段 | `AppPreferences.UI.Visualizer.*` + `PlayerInterfaceScreen.kt` | 只摘可视化字段 |

## 2. RawS-Music 关键文件路径（移植目标）

### 导航 / 装配

| 路径（相对 `RawS-Music\`） | 内容 |
| --- | --- |
| `settings.gradle.kts` | 模块登记（当前最大 `:lyric:bridge:provider`） |
| `gradle\libs.versions.toml` | 依赖版本目录 |
| `app\build.gradle.kts` | `com.rawsmusic`，compileSdk/targetSdk 37，minSdk 24 |
| `core\ui\src\main\kotlin\com\rawsmusic\core\ui\scene\NavScene.kt` | 场景枚举（当前最大 id = 51） |
| `...\core\ui\...\scene\ComposeNavHost.kt` | 场景 → 页面映射（862 行） |
| `...\core\ui\...\scene\NavigationState.kt` | 导航栈 |
| `...\core\ui\...\scene\AppSideRail.kt` | 侧栏入口枚举与条目 |
| `app\...\MainActivity.kt` | 装配、状态透传、侧栏跳转 |
| `app\...\AppPageRenderer.kt` | NavScene → 独立 Activity 映射 |
| `app\src\main\AndroidManifest.xml` | Activity 声明（`@style/SettingsActivityTheme`） |

### UI 规范来源

| 路径 | 内容 |
| --- | --- |
| `...\core\ui\...\scene\pages\SettingsComponents.kt` | `themeColors()` / `appFontFamily()` / `SettingsPage` / `SectionHeader` / `SettingsCard` / `SettingsNavigationEntry` |
| `...\core\ui\...\theme\RawSMusicTheme.kt` | 主题包装、字体/字重缩放、`RawThemeRuntimeState` |
| `...\core\ui\...\theme\ColorScheme.kt` | 强调色候选与读写 |
| `...\core\ui\...\theme\RawMonetTheme.kt` / `ThemeManager.kt` | Monet / 明暗模式 |
| `...\core\ui\...\scene\pages\SourceImportPage.kt` | **门户页模板**（多 tab + 底栏 + 返回栈） |
| `...\core\ui\...\scene\pages\SourcePortalChrome.kt` | `SourcePortalTab` 枚举、顶栏、底栏、平台 chip |
| `...\core\ui\...\scene\pages\SourceSearchPage.kt` | 列表 + 搜索 + 四态 参考 |
| `...\core\ui\...\scene\pages\SourceConfigurationPage.kt` | 配置型页面参考 |
| `...\core\ui\...\scene\pages\PlaylistsPage.kt` | 歌单页（要改造的目标） |
| `...\core\ui\...\scene\pages\GlobalSearchScreen.kt` + `scene\GlobalSearchScope.kt` | 全局搜索 |
| `...\core\ui\...\widget\RawMiuixOverlayDialog.kt` | 弹窗承载 |
| `...\core\ui\...\widget\player\ComposeAudioVisualizer.kt` | 可视化控件（44 KB，6 形态） |
| `...\core\ui\...\widget\bottombar\` | `LiquidBottomTabs` / `LiquidBottomTab` |
| `app\...\ui\webdav\WebDavPage.kt` | **云同步页 UI 模板**（632 行） |
| `app\...\ui\settings\WebDavBackupScreen.kt` / `WebDavBackupActivity.kt` | 备份设置页模板 |
| `app\...\ui\settings\PlayerInterfaceScreen.kt` | 可视化开关所在地 |
| `app\...\ui\settings\AudioEffectsScreen.kt` / `AudioEffectsActivity.kt` | 设置子页外壳模板 |

### 数据层

| 路径 | 内容 |
| --- | --- |
| `module\data\...\db\MusicDatabase.kt` | Room，version 5，`exportSchema = true`，手写 Migration |
| `module\data\schemas\com.rawsmusic.module.data.db.MusicDatabase\` | Room schema JSON 输出目录 |
| `module\data\...\db\entity\` | 实体（`UserPlaylistEntities.kt` 等） |
| `module\data\...\db\dao\` | DAO（`UserPlaylistDao.kt`、`SearchHistoryDao.kt` 等） |
| `module\data\...\prefs\AppPreferences.kt` | **唯一偏好入口**（1984 行，MMKV 嵌套 object） |
| `module\data\...\prefs\PlaylistStore.kt` | 歌单 Store（21 KB） |
| `module\data\...\repository\MusicRepository.kt` | 主仓库（41 KB） |
| `module\data\...\source\` | 音源插件存储 + 运行时 + 播放控制 |
| `module\data\...\source\LxSourcePluginStore.kt` / `MusicSourcePluginStore.kt` / `MusicSourcePersistenceJson.kt` | 音源安装与序列化（云同步依赖） |
| `module\data\...\source\runtime\MusicSourceRuntimeService.kt` | LX 运行时（109 KB，**复用，勿重写**） |
| `module\data\...\source\playback\MusicSourceAudioResolver.kt` | 在线播放解析（**复用**） |
| `core\common\...\source\RawMusicSourceProtocol.kt` | 音源协议契约 |
| `core\common\...\source\lx\LxCompatProtocol.kt` / `LxSourceScriptParser.kt` | LX 兼容层 |
| `core\common\...\source\musicfree\*` | MusicFree 兼容层 |
| `core\common\...\prefs\UIPreferences.kt` | UI 偏好（主题） |
| `core\common\...\CoreInit.kt` | MMKV 初始化 |

### 平台能力 / 工具

| 路径 | 内容 |
| --- | --- |
| `module\player\...\dsp\RealtimeSpectrumPipeline.kt` | 实时频谱数据源 |
| `module\player\...\dsp\NativeStereoSpectrumAnalyzer.kt` | 原生 FFT（`OUTPUT_SIZE`） |
| `app\...\MainActivityVisualizerCoordinator.kt` | 频谱生命周期与投递 |
| `app\...\AudioVisualizerHapticController.kt` | 可视化触感 |
| `core\common\...\ui\AppNoticeBus.kt` | 用户提示（`post` / `error`，`AppNoticeIcon` 枚举） |
| `core\common\...\utils\AppLogger.kt` | 日志（`d/i/w/e`） |
| `core\common\...\spectrum\AudioSpectrumAnalysis.kt` | 离线频谱分析（与实时可视化不同） |
| `app\...\ui\analysis\AudioSpectrumAnalysisScreen.kt` | 离线分析页 |
| `module\scanner\...\webdav\WebDavClient.kt` | OkHttp 使用范例 |
| `app\...\ui\search\GlobalSearchScreen.kt` | 全局搜索页（app 侧） |

## 3. OrBit 源码路径（只读参考）

| 路径（相对 `D:\development\OrBitPlayer\`） | 内容 |
| --- | --- |
| `app\src\main\java\com\orbit\music\data\online\model\OnlineMusicModels.kt` | 在线模型（116 行） |
| `...\data\online\source\IOnlineMusicSource.kt` | 平台接口（103 行） |
| `...\data\online\source\netease\NeteaseMusicSource.kt` + `NeteaseCrypto.kt` | 网易（38 KB + 3 KB） |
| `...\data\online\source\qq\QQMusicSource.kt` | QQ（62 KB） |
| `...\data\online\source\kugou\KugouMusicSource.kt` | 酷狗（28 KB） |
| `...\data\online\source\kuwo\KuwoMusicSource.kt` | 酷我（28 KB） |
| `...\data\online\source\migu\MiguMusicSource.kt` | 咪咕（28 KB） |
| `...\data\online\repository\OnlineMusicRepository.kt` | 聚合仓库（451 行，含全网搜歌） |
| `...\data\online\repository\OnlinePlaylistFavoriteManager.kt` | 在线歌单收藏（188 行） |
| `...\data\online\engine\CloudPlaylistSyncManager.kt` | 歌单同步（31 KB，契约来源） |
| `...\data\online\engine\CloudSourceSyncManager.kt` | 音源同步（16 KB，契约来源） |
| `...\data\online\engine\OnlineAudioSourceManager.kt` | 匹配 / 探测 / 音质阶梯（71 KB，只摘算法） |
| `...\data\online\engine\SourceScriptManager.kt` | 脚本管理（401 行） |
| `...\data\online\engine\LxSourceEngine.kt` | LX QuickJS 运行时（611 行，**不移植**，仅了解协议） |
| `...\data\online\model\SourceScriptItem.kt` | 脚本条目（61 行） |
| `...\data\online\auth\service\QQMusicAuthService.kt` | QQ 扫码（21 KB） |
| `...\data\online\auth\service\KugouMusicAuthService.kt` | 酷狗扫码（37 KB） |
| `...\data\online\auth\PlatformAccountManager.kt` + `model\PlatformAccountModels.kt` | 账号管理（10 KB + 4 KB） |
| `...\data\online\auth\util\QrCodeGenerator.kt` | ZXing 二维码生成 |
| `...\data\db\AppDatabase.kt` | `song_stats` / 歌单 SQL（语义来源，32 KB） |
| `...\data\playlist\PlaylistGroupManager.kt` | 歌单分组（4 KB） |
| `...\data\playlist\PlaylistTransferManager.kt` | 导入导出（29 KB） |
| `...\data\repository\MusicRepository.kt` | 喜欢/不喜欢 API（27 KB） |
| `...\data\repository\PlayedPlaylistManager.kt` | 最近播放歌单（14 KB） |
| `...\data\model\Song.kt` | Song / SongAttitude / Playlist（144 行） |
| `...\data\model\VisualizerStyle.kt` | 可视化形态与配色（109 行） |
| `...\audio\AudioVisualizerManager.kt` | 可视化物理参数 / 分频（363 行，只摘算法） |
| `...\ui\components\SpectrumVisualizer.kt` | Canvas 形态实现（30 KB） |
| `...\ui\components\TimeTunnelVisualizer.kt` + `TimeTunnelGLRenderer.kt` | GL 形态范例（7.6 KB + 13 KB） |
| `...\ui\components\OctgramsVisualizer.kt` + `OctgramsGLRenderer.kt` | GL 形态 |
| `...\ui\components\SoundCityVisualizer.kt` + `SoundCityGLRenderer.kt` | GL 形态 |
| `...\ui\components\FractalGalaxyVisualizer.kt` + `FractalGalaxyGLRenderer.kt` | GL 形态 |
| `...\ui\components\QuantumVortexVisualizer.kt` + `QuantumVortexGLRenderer.kt` | GL 形态 |
| `...\app\src\main\assets\script\user-api-preload.js` | LX API v2 契约（451 行，了解用） |
| `...\app\src\main\cpp\dsp\FFTAnalyzer.{h,cpp}` | 原生 FFT（次要路径，了解用） |
| `D:\development\OrBitPlayer\music source.txt` | 5 个 LX 音源 URL 样例 |
| `D:\development\OrBitPlayer\墨澜聚合音源 v2.2.0.js` | 真实 LX v2 音源脚本（**端到端测试夹具**） |
| `D:\development\OrBitPlayer\kg_util.js` | 酷狗辅助（KRC 歌词解密等；需 CommonJS 模块加载器，仅参考） |

## 4. 平台接口端点速查（移植时**照搬**，勿改写）

**网易云**（Referer `https://music.163.com/`）
`/api/playlist/catalogue`（tags）、`/api/playlist/list`（分类歌单）、`/api/toplist`（榜单）、`/api/v1/playlist/detail`、`/api/v3/song/detail`、`/api/search/get/web`、`/api/cloudsearch/pc`、`/api/v1/artist/list`、`/api/v1/artist/detail`、`/api/artist/50/hot`、`/api/v1/artist/songs`、`/api/artist/albums/<id>`、`/api/v1/album/<id>`；外链兜底 `https://music.163.com/song/media/outer/url?id=<id>.mp3`

**QQ 音乐**（Referer `https://y.qq.com/`）
统一入口 `https://u.y.qq.com/cgi-bin/musicu.fcg`；`https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_diss_by_tag.fcg`（分类歌单）、`https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg`（歌单详情）、`https://c.y.qq.com/soso/fcgi-bin/client_search_cp`（搜歌）、`https://c.y.qq.com/v8/fcg-bin/v8.fcg`（歌手）、`https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg`（歌手搜索）、`https://c.y.qq.com/v8/fcg-bin/fcg_v8_singer_album.fcg`（歌手专辑）、`https://c.y.qq.com/v8/fcg-bin/fcg_v8_album_info_cp.fcg`（专辑）；封面 `https://y.gtimg.cn/music/photo_new/T002R300x300M000<albumMid>.jpg` / `T001R300x300M000<singerMid>.jpg`；登录 `https://ssl.ptlogin2.qq.com/ptqrshow` + `ptqrlogin` + `graph.qq.com`；用户歌单 `https://c.y.qq.com/rsc/fcgi-bin/fcg_user_created_diss` + `https://c.y.qq.com/rsc/fcgi-bin/fcg_get_profile_homepage.fcg`

**酷狗**（Referer `http://m.kugou.com/`）
`http://mobilecdnbj.kugou.com/api/v5/tag/list`、`http://m.kugou.com/plist/index`、`http://mobilecdnbj.kugou.com/api/v5/rank/list`、`.../api/v5/special/info`、`.../api/v5/special/song`、`.../api/v5/rank/song`、`http://mobilecdn.kugou.com/api/v3/search/special`、`.../api/v3/search/song`、`http://songsearch.kugou.com/song_search_v2`、`.../api/v3/album/info`、`.../api/v3/album/song`；登录 `https://login-user.kugou.com/v2/qrcode` + `/v2/get_userinfo_qrcode`；用户信息与歌单 `https://gateway.kugou.com/...`（多候选兜底）；签名盐 `WEB_SIGN_SALT="NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"`、`ANDROID_SIGN_SALT="OIlwieks28dk2k092lksi2UIkp"`，`APP_ID=1005`、`SRC_APP_ID=2919`、`PLAT_ID=4`

**酷我**（Referer `http://www.kuwo.cn/`）
`http://wapi.kuwo.cn/api/pc/classify/playlist/getRcmPlayList` / `getTagPlayList`、`http://wapi.kuwo.cn/api/pc/bang/list`、`http://nplserver.kuwo.cn/pl.svc?op=getlistinfo`、`http://search.kuwo.cn/r.s`、`http://kbangserver.kuwo.cn/ksong.s`；封面 `https://img1.kuwo.cn/star/albumcover<path>` / `star/starheads<path>`。**无签名**。

**咪咕**（Referer `https://m.music.migu.cn/`）
`https://app.pd.nf.migu.cn/MIGUM2.0/v1.0/content/resourceinfo.do`、`https://c.musicapp.migu.cn/MIGUM2.0/v1.0/user/queryMusicListSongs.do`、`https://c.musicapp.migu.cn/MIGUM2.0/v1.0/content/search_all.do`；直链两步 `resourceinfo.do?copyrightId=&resourceType=2` → `sub/listenSong.do?toneFlag=HQ|PQ`。**无签名**。

> 这些是**逆向得到的脆弱外部契约**，会随时失效。移植时**照搬现有端点、Referer、JSONP 清洗与 fallback 链**，不要"优化"或"现代化改写"。
