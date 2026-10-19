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
- [ ] **1.1 引入 Oboe C++ 依赖**
  - 在 `app/build.gradle.kts` 中通过 prefab 或本地依赖引入 `com.google.oboe:oboe`。
  - 在 `app/src/main/cpp/CMakeLists.txt` 中链接 `oboe::oboe`，配置 C++17 支持。
- [ ] **1.2 移植 Oboe JNI 原生绑定层**
  - 迁移 `OboeAudioOutput.kt` 及对应的 native C++ 实现代码。
  - 验证原生动态库在 `arm64-v8a` 架构上的编译与动态加载。
- [ ] **1.3 引入 FFmpeg 解码扩展（:ffmpeg-decoder）**
  - 引入或挂载定制的 `ffmpeg-decoder` 模块与 JNI 桥接。
  - 验证静态 FFmpeg 库在构建时的集成。

### 阶段二：音频管线与渲染工厂改造 (Pipeline Integration)
- [ ] **2.1 移植 OboeAudioSink**
  - 引入 `OboeAudioSink.kt`，实现 Media3 的 `AudioSink` 接口。
  - 支持将 PCM 缓冲流直接递交给原生 Oboe Stream。
- [ ] **2.2 移植定制 RenderersFactory**
  - 构建适用于 OrBitPlayer 的 `OrbitRenderersFactory`（参考 `EllaRenderersFactory`）。
  - 实现异常解码器软屏蔽（如高通平台崩盘的 `c2.qti.alac.sw.decoder`）。
  - 挂载 `FfmpegAudioRenderer`，支持 ALAC/FLAC/APE 等格式软解。
- [ ] **2.3 桥接 MusicPlayerManager**
  - 在 `MusicPlayerManager.kt` 中重构 `renderersFactory` 初始化逻辑。
  - 将 OrBitPlayer 自有的 `NativeDspAudioProcessor` 注入 `OboeAudioSink` 的处理管线中，确保原有均衡器音效完全生效。

### 阶段三：USB 独占输出与设置管理 (USB Exclusive & Settings)
- [ ] **3.1 增加音频输出配置选项**
  - 新增配置项：音频输出后端（`AudioTrack` / `AAudio` / `OpenSL ES`）。
  - 新增配置项：USB 独占模式（开启后自动以原始采样率和位深打开 DAC）。
- [ ] **3.2 移植 USB 声卡热插拔检测**
  - 引入 `UsbAudioController` 与 `UsbExclusiveSession`。
  - 监听 `AudioDeviceInfo.TYPE_USB_DEVICE` 插拔广播，拔出时平滑切回扬声器/系统路由。

### 阶段四：验证联调与健壮性测试 (Verification & Testing)
- [ ] **4.1 本地音频播放回归测试**
  - 验证 MP3、AAC、FLAC、WAV、ALAC、DSD 等主流本地格式正常起播。
  - 验证暂停、继续、拖动进度（Seek）、切歌无爆音或卡死。
- [ ] **4.2 网络歌单寻源与边播边缓存专项验证**
  - 验证在线广场歌单单曲点击、连续切歌、队列播完自动切下一首。
  - 验证本地磁盘分片缓存正常写入与再次点击 0ms 秒开。
- [ ] **4.3 硬件输出链路验证**
  - 在真机上实测普通蓝牙/Type-C 耳机下的 AAudio 输出。
  - 在外接 USB DAC 下实测高采样率（96kHz/192kHz/24bit）源码输出。

---

## 四、 进展日志 (Progress Log)

- **2026-10-19**：
  - 完成 RawS-Music 分支代码提交与推送（`feat/orbit-port`）。
  - 在 OrBitPlayer 主工程中完成架构评估，确认上游寻源与下游音频渲染完全解耦。
  - 创建迁移专属工作分支 `feat/halcyon-audio-pipeline`。
  - 编写并确立本跟踪计划文档（`plan.md`）。
