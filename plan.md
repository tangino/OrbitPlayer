# Halcyon 底层音频管线迁移跟踪计划 (plan.md)

## 一、 迁移背景与核心目标

### 1. 背景概述
Halcyon 拥有成熟且经过真机验证的底层音频输出与解码加固链路（基于 AndroidX Media3、Google Oboe 原生 AAudio/OpenSL ES 低延迟通道、USB 独占 Bit-Perfect 直通、定制 FFmpeg 软解与系统硬解防崩兜底）。OrBitPlayer 目前以系统 `AudioTrack` 为基础输出通道，通过将 Halcyon 的这套底层能力迁移至 OrBitPlayer，可显著降低播放延迟、避免系统音频服务（AudioFlinger）的强制重采样（SRC），并在连接外置 USB DAC 解码耳放时实现真正的源码直通输出。

### 2. 核心原则与红线约束
- **绝对不影响网络寻源**：OrBitPlayer 现有的网络歌单寻源算法（`OnlineAudioSourceResolver`、`OnlineAudioSourceManager`、多源并发探活、LRU 缓存）以及边播边本地磁盘分片缓存机制（`AudioCacheManager`）保持原封不动，下游渲染层对上游透明。
- **范围排除**：按照用户明确要求，**双 Player 交叉淡入淡出（Crossfade）暂不实现**，保持 OrBitPlayer 原有单 ExoPlayer 实例的生命周期与队列控制，规避复杂状态机分裂风险。
- **平滑兼容与兜底**：必须保留系统默认 `AudioTrack` 模式作为默认/兜底选项；若 Oboe 原生库加载失败或设备不支持 AAudio，自动无感降级至系统音频轨道。

---

## 二、 架构设计与映射关系

```
┌────────────────────────────────────────────────────────┐
│   上游输入层：OrBitPlayer 本地媒体库 / 网络歌单寻源与缓存   │
│   (OnlineAudioSourceManager / AudioCacheManager)       │
└──────────────────────────┬─────────────────────────────┘
                           │ 输出 MediaItem / 本地与网络 URI
                           ▼
┌────────────────────────────────────────────────────────┐
│            ExoPlayer (AndroidX Media3 核心)             │
│            MusicPlayerManager.kt 状态机与控制            │
└──────────────────────────┬─────────────────────────────┘
                           │
      ┌────────────────────┴────────────────────┐
      ▼ (解码层增强)                               ▼ (渲染与输出层增强)
┌──────────────────────────┐             ┌──────────────────────────────┐
│  EllaRenderersFactory    │             │       OboeAudioSink          │
│  - 过滤系统异常硬解码器     │             │  - AAudio / OpenSL ES 低延迟 │
│  - 优先挂载 FFmpeg 软解   │             │  - USB 独占 (Bit-Perfect)     │
└──────────────────────────┘             │  - 系统 AudioTrack 自动兜底    │
                                         └──────────────┬───────────────┘
                                                        │
                                                        ▼
                                         ┌──────────────────────────────┐
                                         │       硬件耳机 / 外置 USB DAC   │
                                         └──────────────────────────────┘
```

---

## 三、 迁移里程碑与任务清单 (Milestones & Checklist)

### 阶段一：底层依赖与原生环境就绪 (Infrastructure)
- [x] **1.1 引入 Oboe C++ 依赖**
  - 在 `app/build.gradle.kts` 中通过 prefab 引入 `com.google.oboe:oboe:1.11.0`。
  - 在 `app/src/main/cpp/CMakeLists.txt` 中链接 `oboe::oboe`，配置 C++17 支持。
  - 对齐 NDK LTS 版本为 `26.1.10909125`，消除 `basic_stringstream` VTT 虚表符号缺失问题。
- [x] **1.2 移植 Oboe JNI 原生绑定层**
  - 迁移 `OboeAudioOutput.kt` 及对应的 native C++ 实现代码（`oboe_sink.cpp`）。
  - 按依赖顺序加载 `c++_shared` -> `oboe` -> `orbit_oboe`。
  - 验证原生动态库在 `arm64-v8a`、`x86_64` 等全架构上的编译与动态加载。
- [x] **1.3 引入 FFmpeg 解码扩展（:ffmpeg-decoder）**
  - 完整迁移定制的 `ffmpeg-decoder` 模块至 OrBitPlayer 并挂载到 `settings.gradle.kts`。
  - 适配主工程 `minSdk = 21` 与 Media3 1.3.1，验证静态 FFmpeg 库构建打包。

### 阶段二：音频管线与渲染工厂改造 (Pipeline Integration)
- [x] **2.1 移植 OboeAudioSink 与智能调度器 OrbitDelegatingAudioSink**
  - 引入 `OboeAudioSink.kt`，实现 Media3 的 `AudioSink` 接口。
  - 设计并引入 `OrbitDelegatingAudioSink.kt`，支持在 `AudioTrack` 与 `AAudio/OpenSL ES` 之间即时热切换，且具备驱动异常自动平滑降级兜底能力。
- [x] **2.2 移植定制 RenderersFactory**
  - 构建适用于 OrBitPlayer 的 `OrbitRenderersFactory`。
  - 实现异常解码器软屏蔽（如高通平台崩盘的 `c2.qti.alac.sw.decoder` 等缺陷硬解）。
  - 挂载 `FfmpegAudioRenderer`，支持大帧 FLAC 兼容性软解与用户偏好全格式软解。
- [x] **2.3 桥接 MusicPlayerManager**
  - 在 `MusicPlayerManager.kt` 中挂载 `OrbitRenderersFactory`。
  - 将 OrBitPlayer 自有的 `NativeDspAudioProcessor` 注入 `OrbitDelegatingAudioSink` 处理管线中，确保原有均衡器音效 100% 生效。

### 阶段三：USB 独占输出与设置管理 (USB Exclusive & Settings)
- [x] **3.1 增加音频输出配置选项**
  - 新增配置项：音频输出后端（`AudioTrack` / `AAudio` / `OpenSL ES`）。
  - 新增配置项：USB 独占模式（开启后自动以原始采样率和位深打开 DAC）。
  - 新增配置项：优先使用 FFmpeg 软解码。
  - 在 `SettingsScreen.kt` 中增加高颜值“音频输出与解码核心 (Hi-Fi)”卡片、下拉选择、Switch 开关及硬件状态实时胶囊展示。
- [x] **3.2 移植 USB 声卡热插拔检测**
  - 引入 `UsbAudioController` 与 `UsbExclusiveSession`。
  - 监听 `AudioDeviceInfo.TYPE_USB_DEVICE` 插拔广播，拔出时平滑切回扬声器/系统路由。
  - 在 `MusicPlayerManager` 初始化中启动协同观察者。

### 阶段四：验证联调与健壮性测试 (Verification & Testing)
- [x] **4.1 本地音频播放回归测试**
  - 验证主流本地格式正常起播，暂停、继续、拖动进度（Seek）、切歌无爆音或卡死。
- [x] **4.2 网络歌单寻源与边播边缓存专项验证**
  - 验证在线广场歌单（网易云、QQ 音乐、酷狗、酷我等）单曲点击起播。
  - 验证寻源成功拉流、本地分片缓存写入、边播边缓存完全正常。
- [x] **4.3 硬件输出链路验证**
  - 实测 Google Oboe 原生音频流成功建立（`OrbitOboe: opened api=... rate=44100 ch=2 enc=3 (Float32)`）。
  - 验证 AudioTrack 与 Oboe 双通道正常运行与热切换。

---

## 四、 进展日志 (Progress Log)

- **2026-10-19**：
  - 完成 RawS-Music 分支代码提交与推送（`feat/orbit-port`）。
  - 在 OrBitPlayer 主工程中完成架构评估，确认上游寻源与下游音频渲染完全解耦。
  - 创建迁移专属工作分支 `feat/halcyon-audio-pipeline`。
  - 编写并确立本跟踪计划文档（`plan.md`）。
  - **阶段一完成**：引入 prefab Oboe 1.11.0、创建 `orbit_oboe` 原生库、移植 `ffmpeg-decoder` 模块。统一对齐 NDK 26.1 LTS 版本。
  - **阶段二完成**：完成 `OboeAudioSink`、`AudioDecoderCompatibility`、`OrbitRenderersFactory` 与 `MusicPlayerManager` 的无缝接入。引入 `OrbitDelegatingAudioSink` 智能代理调度器。
  - **阶段三完成**：实现 `UsbAudioController` 与 `UsbExclusiveSession` 热插拔监听；在设置界面新增“音频输出与解码核心 (Hi-Fi)”控制卡片。
  - **阶段四完成**：全量编译通过并在 Android 设备上完成联调测试。验证网络广场歌曲在线寻源拉流、边播边存及 Oboe 原生流输出正常运作。
  - **Release 构建验证**：使用 `build_release.bat` 成功打包生成 `OrbitPlayer-v0.4.5.apk` 并顺利安装至真机设备与模拟器。

