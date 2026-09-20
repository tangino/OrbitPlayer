#!/usr/bin/env bash

# Android 虚拟机交互式启动脚本
set -e

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

# 2. 设置环境变量与查找 Android SDK
if [ -f "${WORKSPACE_DIR}/local.properties" ]; then
    LOCAL_SDK=$(grep -E "^sdk.dir=" "${WORKSPACE_DIR}/local.properties" | cut -d'=' -f2 | sed 's/\\:/:/g' | sed 's/\\\\/\//g' || true)
    if [ -d "${LOCAL_SDK}" ]; then
        SDK_DIR="${LOCAL_SDK}"
    fi
fi

export ANDROID_SDK_ROOT="${SDK_DIR}"
export ANDROID_HOME="${SDK_DIR}"
export PATH="${SDK_DIR}/emulator:${SDK_DIR}/platform-tools:${PATH}"

EMULATOR_BIN="${SDK_DIR}/emulator/emulator"
ADB_BIN="${SDK_DIR}/platform-tools/adb"

if [ ! -x "${EMULATOR_BIN}" ]; then
    EMULATOR_BIN=$(which emulator 2>/dev/null || true)
fi

if [ ! -x "${ADB_BIN}" ]; then
    ADB_BIN=$(which adb 2>/dev/null || true)
fi

if [ -z "${EMULATOR_BIN}" ] || [ ! -x "${EMULATOR_BIN}" ]; then
    echo "❌ 未找到可执行的 emulator 命令，请确认 Android SDK 路径。"
    exit 1
fi

# 3. 获取所有可用的 AVD 列表
AVD_LIST=()
while IFS= read -r line; do
    if [ -n "${line}" ]; then
        AVD_LIST+=("${line}")
    fi
done < <("${EMULATOR_BIN}" -list-avds 2>/dev/null || true)

AVD_COUNT=${#AVD_LIST[@]}
if [ ${AVD_COUNT} -eq 0 ]; then
    echo "❌ 未检测到任何 Android 虚拟机 (AVD)！"
    echo "请先在 Android Studio 中打开 Device Manager 创建虚拟机。"
    exit 1
fi

# 4. 解析目标虚拟机 (支持序号与名称参数)
TARGET_ARG="$1"
SELECTED_AVD=""

if [ -n "${TARGET_ARG}" ]; then
    if [[ "${TARGET_ARG}" =~ ^[0-9]+$ ]]; then
        if [ "${TARGET_ARG}" -ge 1 ] && [ "${TARGET_ARG}" -le ${AVD_COUNT} ]; then
            SELECTED_AVD="${AVD_LIST[$((TARGET_ARG - 1))]}"
        fi
    fi
    if [ -z "${SELECTED_AVD}" ]; then
        for avd in "${AVD_LIST[@]}"; do
            if [ "${avd}" = "${TARGET_ARG}" ]; then
                SELECTED_AVD="${avd}"
                break
            fi
        done
    fi
fi

# 5. 交互式选择
if [ -z "${SELECTED_AVD}" ]; then
    if [ ${AVD_COUNT} -eq 1 ]; then
        SELECTED_AVD="${AVD_LIST[0]}"
        echo "检测到唯一虚拟机: ${SELECTED_AVD}，自动选择启动。"
    else
        echo "======================================================"
        echo "   Orbit Player - Android 虚拟机启动器"
        echo "======================================================"
        echo "检测到以下可用的 Android 虚拟机:"
        echo "------------------------------------------------------"
        for i in "${!AVD_LIST[@]}"; do
            echo "  [$((i + 1))] ${AVD_LIST[$i]}"
        done
        echo "------------------------------------------------------"
        read -r -p "请输入虚拟机序号 [1-${AVD_COUNT}] (直接回车默认: 1): " CHOICE
        CHOICE="${CHOICE:-1}"
        if [[ "${CHOICE}" =~ ^[0-9]+$ ]] && [ "${CHOICE}" -ge 1 ] && [ "${CHOICE}" -le ${AVD_COUNT} ]; then
            SELECTED_AVD="${AVD_LIST[$((CHOICE - 1))]}"
        else
            echo "⚠️ 输入无效，默认选择 [1] ${AVD_LIST[0]}"
            SELECTED_AVD="${AVD_LIST[0]}"
        fi
    fi
fi

# 6. 检查当前是否已有运行中的模拟器
RUNNING_DEVICE=$("${ADB_BIN}" devices 2>/dev/null | grep -E "emulator-[0-9]+" | awk '{print $1}' | head -n 1 || true)
if [ -n "${RUNNING_DEVICE}" ]; then
    echo "💡 当前已有正在运行的模拟器 (${RUNNING_DEVICE})，无需重复启动。"
    "${ADB_BIN}" devices
    exit 0
fi

echo "🚀 [1/3] 正在准备启动虚拟机: ${SELECTED_AVD} ..."
LOG_FILE="${WORKSPACE_DIR}/emulator.log"

# 7. 后台启动虚拟机
echo "🚀 [2/3] 正在启动 ${SELECTED_AVD} ..."
nohup "${EMULATOR_BIN}" -avd "${SELECTED_AVD}" -netdelay none -netspeed full > "${LOG_FILE}" 2>&1 &
EMU_PID=$!
echo "📝 虚拟机已在后台启动 (PID: ${EMU_PID})，日志实时输出至: emulator.log"

# 8. 等待并检测启动就绪状态
echo "⏳ [3/3] 正在等待 ADB 连接设备..."
"${ADB_BIN}" wait-for-device

DEVICE_NAME=$("${ADB_BIN}" shell getprop ro.product.model 2>/dev/null || echo "Android Device")
ANDROID_VER=$("${ADB_BIN}" shell getprop ro.build.version.release 2>/dev/null || echo "Unknown")

echo ""
echo "======================================================"
echo "🎉 [成功] 虚拟机启动就绪！"
echo "   - 虚拟机名称: ${SELECTED_AVD}"
echo "   - 设备型号:   ${DEVICE_NAME}"
echo "   - 系统版本:   Android ${ANDROID_VER}"
echo "======================================================"
"${ADB_BIN}" devices

