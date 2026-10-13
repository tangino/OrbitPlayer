# `librawsmusic_ffmpeg.so` 缺失问题：分析与两条解决路线

> ## ✅ 本问题已解决 —— 本文档仅作排查过程存档
>
> 最终结论与本文原先的两条路线都不同：
> 1. **`.so` 全部到位**：`app/src/main/jniLibs/arm64-v8a/` 现在有官方 **1.0.13 release** 的**完整预编译集**（`librawsmusic_ffmpeg.so`、`librawscoreservice.so`、`librawsmusic_ffmpeg_live.so`、`librawsmusic_audio_transcode.so`、`librawsmusic_sacd.so`、4 个 FFmpeg 拆分库 + AAR 自带库）。**路线 A 与路线 B 都不需要了。**
> 2. **真正卡住构建的不是这个 `.so`，而是缺失的原生源码**：`CMakeLists.txt` 引用 **144** 个 `.cpp`，仓库里只有 **74** 个，缺的 **70 个全部是 USB 独占文件**（`usb_audio_engine.cpp`、`usb_hardware_volume.cpp`、`raw_usb_*.cpp`）。这正是官方 `docs/BUILDING.md` 说的「完整 USB 独占 Native 核心不在当前公开仓库中」。
> 3. **最终方案**：**关掉 `externalNativeBuild`，直接打包上述预编译 `.so`** —— 见 [`AGENTS.MD`](../AGENTS.MD) §2.2 与 §9 D13。好处是预编译版 `librawscoreservice.so` **本身已包含 USB 独占实现**，比从源码编译时砍掉 USB 更完整。
>
> 下面的内容保留当时的分析，供日后需要恢复原生构建时参考。

> 从 [`AGENTS.MD`](../AGENTS.MD) §2.2 拆出的详细分析。
> 进度见 [`TODO.md`](../TODO.md) 的 **P0-04**。

## 1. 问题本身

`app/src/main/cpp/CMakeLists.txt:231-235` 把 `librawsmusic_ffmpeg.so` 声明为 `SHARED IMPORTED`，位置 `jniLibs/${ANDROID_ABI}/librawsmusic_ffmpeg.so`。
但 `app/src/main/jniLibs/arm64-v8a/` 下只有 `libavcodec.so` / `libavformat.so` / `libavutil.so` / `libswresample.so`，**没有它**。

- `.gitignore` 含 `app/src/main/jniLibs/**/*.so`（其余 4 个是历史上强提交进去的）。
- 已核对本 fork 的**全部 git 历史**：`librawsmusic_ffmpeg.so` **从未被提交过**（`git log --all -- '*librawsmusic_ffmpeg*'` 为空）。
- 上游 `QFDY-GZC/RawS-Music` 的 `main` 分支 tree 里也**只有那 4 个 .so**，没有它。
- 上游 release 说明里明确写着：*"此 APK 由本地未提交工作区构建；GitHub 自动生成的源码压缩包与安装包不完全对应。"*
  → 说明该库里存在于作者**本地工作区**，从未公开到仓库。

## 2. ⚠️ 关键结论：它不是「USB 独占专用库」，关掉 USB 独占**不能**绕过

被链接的目标（`CMakeLists.txt`）：

| 行 | 目标 | 用途 |
| --- | --- | --- |
| 224 | `rawsmusic_usb` | USB 独占（**只是其中之一**） |
| 265 | `rawsmusic_ffmpeg_live` | **标准播放解码桥**，由 `core/common/.../ffmpeg/FFmpegBridge.kt:85` 加载 |
| 566 | `rawsmusic_ai_separation` | AI 人声分离 |
| 692 | `rawscoreservice` | **全 app 唯一加载的主 native 库**（30+ 处 `System.loadLibrary("rawscoreservice")`：DSP、频谱、TagLib、播放引擎、转场、USB…） |

因此：

- 它是**标准播放链路**的依赖，不只是 USB 独占；
- 由于 `rawscoreservice` **静态链接**它，缺失不只是链接期报错 —— **APK 一加载 native 就会崩**，`System.loadLibrary("rawscoreservice")` 直接失败。

> 所以「先不编译 USB 独占」这个方向**不成立**。要么拿到它，要么替换它。

## 3. 路线 A：取得原版 `.so`（最稳）

| 来源 | 说明 |
| --- | --- |
| **上游 release APK** | 下载 `QFDY-GZC/RawS-Music` 的 release APK，解压出 `lib/arm64-v8a/librawsmusic_ffmpeg.so`。**与本仓库快照匹配的版本是 `v1.0.10-release`** —— 该 tag 的 target commit 是 `52a604986aeda001fb47585bb7fba325d25d5abd`，正是本仓库 HEAD `52a6049`。下载页：`https://github.com/QFDY-GZC/RawS-Music/releases/tag/v1.0.10-release` |
| 作者渠道 | 上游 README 里有 QQ 群；可直接索取该预编译库 |
| 自己构建 | 需要重建 FFmpeg 6.0 解码版构建链（上游 `.gitignore` 里出现过 `tools/ffmpeg_build/ffmpeg-6.0/`，但本 fork **没有 `tools/` 目录**）。成本最高。 |

拿到后：放入 `app/src/main/jniLibs/arm64-v8a/`。
注意 `.gitignore` 会忽略它，需要 `git add -f`。

> 本机沙箱**禁止下载二进制**（`Invoke-WebRequest` TLS 失败、`curl.exe` 报 `SEC_E_NO_CREDENTIALS`），所以这一步需要人工在浏览器里下载。

## 4. 路线 B：用仓库里已有的拆分库替换单体库（纯 CMake 改动）

**可行，且证据充分。**

### 4.1 依赖面完全吻合

`app/src/main/cpp/ffmpeg_bridge.cpp` 只 include：

```
libavformat/avformat.h
libavcodec/avcodec.h
libavutil/avutil.h
libavutil/dict.h
libavutil/opt.h
libswresample/swresample.h
```

= **正是仓库里已有的那 4 个 .so 所提供的 API**。

唯一 include `<libswscale/swscale.h>` 的是 `ffmpeg_video_cover.cpp`，而该文件**没有被任何 CMakeLists 引用**（已核对所有 `CMakeLists.txt`，也没有 `file(GLOB ...)` 自动收集）→ **它根本没参与编译，所以不需要 swscale**。
（`app/build.gradle.kts` 里 `excludes += "**/libswscale.so"` 也印证 swscale 不进 APK。）

### 4.2 FFmpeg 版本吻合

| 对象 | 版本 | 对应 FFmpeg |
| --- | --- | --- |
| `cpp/ffmpeg_include/libavutil/version.h` | `LIBAVUTIL_VERSION 58.2.100` | **6.0** |
| `jniLibs/arm64-v8a/libavcodec.so`、`libavformat.so` 内嵌版本串 | `60.3.100` | **6.0** ✅ |

即 headers 与拆分库是**同一代 FFmpeg 6.0**。而转码路径（`rawsmusic_audio_transcode`）本来就是拿同一套 `ffmpeg_include` headers 编译、并链接这 4 个拆分库的 —— 这本身就证明 headers 与拆分库 ABI 兼容。

### 4.3 改法

`CMakeLists.txt` 里已经在 273-292 行声明了 4 个 IMPORTED 目标：

```
rawsmusic_transcode_avutil / rawsmusic_transcode_swresample
rawsmusic_transcode_avcodec / rawsmusic_transcode_avformat
```

把它们建成一个 INTERFACE 目标，或直接把 4 处引用 `rawsmusic_ffmpeg` 的位置替换掉：

| 行 | 目标 | 改动 |
| --- | --- | --- |
| 224 | `rawsmusic_usb` | 换成 4 个拆分库 |
| 265 | `rawsmusic_ffmpeg_live` | 换成 4 个拆分库 |
| 566 | `rawsmusic_ai_separation` | 换成 4 个拆分库 |
| 692 | `rawscoreservice` | 换成 4 个拆分库 |

并删除/停用 231-235 行的 `add_library(rawsmusic_ffmpeg SHARED IMPORTED)`（若不再被引用）。

**Kotlin 侧完全不用改**（没有 `System.loadLibrary("rawsmusic_ffmpeg")`，只有 `rawsmusic_ffmpeg_live` 与 `rawscoreservice`）。

### 4.4 代价与风险（必须实测）

1. 作者在 270-272 行的注释里**刻意**把两者分开：「transcoder 用 encoder-enabled 的拆分构建；playback 继续加载未改动的 `librawsmusic_ffmpeg.so`」。合并后：
   - **APK 变大**（播放路径会把编码器一并带进去）；
   - 两个构建的 FFmpeg **configure 选项可能不同**（解码能力集合、可选 codec），播放兼容性需实测（重点是 DSD/APE/WMA/AIFF/MPC/WV 等 README 宣称支持的格式）。
2. 若替换后出现 `undefined symbol`，说明单体库确实导出过拆分库没有的符号 → 回退到路线 A。
3. 该改动**属于"改 RawS 既有构建行为"**，按 `AGENTS.MD` §10.3 需先经用户确认，并在 §9 登记决策（D12）。

## 5. 建议执行顺序

1. **先用路线 B 解阻塞**（改动小、当天可编过），让 P1/P2/P3/P4 的 Kotlin 工作能推进。
2. **拿到原版 `.so` 后切回路线 A**，跑一次全量回归（P5-03 / P5-04）确认播放兼容性与 APK 体积回到原状。
3. 无论走哪条，都**单独成一个 commit**，并在 `TODO.md` 的 P0-04 记录实际采用哪条路线与验证结果。
