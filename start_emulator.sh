#!/usr/bin/env bash

# Android 虚拟机一键启动脚本
set -e

# 定位脚本所在目录作为工作区根目录
WORKSPACE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK_DIR="${WORKSPACE_DIR}/Android/sdk"
BACKUP_SDK_DIR="/Volumes/BOOTCAMP/Android/sdk"

# 1. 检查并补全系统镜像与皮肤软链接
if [ ! -d "${SDK_DIR}/system-images" ] && [ -d "${BACKUP_SDK_DIR}/system-images" ]; then
    echo "💡 检测到 SDK 缺少 system-images，正在自动软链接镜像..."
    ln -s "${BACKUP_SDK_DIR}/system-images" "${SDK_DIR}/system-images"
fi

if [ ! -d "${SDK_DIR}/skins" ] && [ -d "${BACKUP_SDK_DIR}/skins" ]; then
    echo "💡 正在自动软链接 skins..."
    ln -s "${BACKUP_SDK_DIR}/skins" "${SDK_DIR}/skins"
fi

# 2. 设置环境变量
export ANDROID_SDK_ROOT="${SDK_DIR}"
export ANDROID_HOME="${SDK_DIR}"
export PATH="${SDK_DIR}/emulator:${SDK_DIR}/platform-tools:${PATH}"

EMULATOR_BIN="${SDK_DIR}/emulator/emulator"
ADB_BIN="${SDK_DIR}/platform-tools/adb"

if [ ! -x "${EMULATOR_BIN}" ]; then
    echo "❌ 未找到可执行的 emulator: ${EMULATOR_BIN}"
    exit 1
fi

# 3. 检查当前是否已有运行中的模拟器
RUNNING_DEVICE=$("${ADB_BIN}" devices 2>/dev/null | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1 || true)
if [ -n "${RUNNING_DEVICE}" ]; then
    echo "✅ 虚拟机已在运行中 (${RUNNING_DEVICE})，无需重复启动。"
    "${ADB_BIN}" devices
    exit 0
fi

# 4. 获取要启动的 AVD
AVD_NAME="$1"
if [ -z "${AVD_NAME}" ]; then
    AVAILABLE_AVDS=$("${EMULATOR_BIN}" -list-avds 2>/dev/null || true)
    if echo "${AVAILABLE_AVDS}" | grep -q "Pixel_3a_API_34"; then
        AVD_NAME="Pixel_3a_API_34"
    else
        AVD_NAME=$(echo "${AVAILABLE_AVDS}" | head -n 1)
    fi
fi

if [ -z "${AVD_NAME}" ]; then
    echo "❌ 未检测到任何可用的 Android 虚拟机 (AVD)！"
    exit 1
fi

echo "🚀 正在启动虚拟机: ${AVD_NAME} ..."
LOG_FILE="${WORKSPACE_DIR}/emulator.log"

# 5. 后台启动虚拟机
nohup "${EMULATOR_BIN}" -avd "${AVD_NAME}" -netdelay none -netspeed full > "${LOG_FILE}" 2>&1 &
EMU_PID=$!
echo "📝 虚拟机已在后台启动 (PID: ${EMU_PID})，日志实时输出至: emulator.log"

# 6. 等待并检测启动就绪状态
echo "⏳ 正在等待 ADB 连接设备..."
"${ADB_BIN}" wait-for-device

DEVICE_NAME=$("${ADB_BIN}" shell getprop ro.product.model 2>/dev/null || echo "Android Device")
ANDROID_VER=$("${ADB_BIN}" shell getprop ro.build.version.release 2>/dev/null || echo "Unknown")

echo "🎉 虚拟机启动就绪！"
echo "   - 设备型号: ${DEVICE_NAME}"
echo "   - 系统版本: Android ${ANDROID_VER}"
"${ADB_BIN}" devices
