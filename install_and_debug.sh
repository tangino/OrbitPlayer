#!/bin/bash

# ==============================================================================
# Android Music Equalizer - 一键编译、安装与调试脚本
# ==============================================================================

set -e

# 颜色定义
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${CYAN}======================================================${NC}"
echo -e "${CYAN}   Android Music Equalizer - 一键安装与调试工具      ${NC}"
echo -e "${CYAN}======================================================${NC}"

# 1. 自动配置 Android SDK 路径与 ADB
export ANDROID_HOME="${ANDROID_HOME:-/Users/$USER/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

if ! command -v adb &> /dev/null; then
    echo -e "${RED}[错误] 未找到 adb 命令，请确保 Android SDK 安装在: $ANDROID_HOME${NC}"
    exit 1
fi

# 2. 检查设备连接状态
echo -e "\n${YELLOW}[1/4] 检查已连接的 Android 设备...${NC}"
DEVICES=$(adb devices | grep -w "device" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo -e "${RED}[错误] 未检测到已授权的 Android 设备或虚拟机！${NC}"
    echo -e "请检查："
    echo -e "  1. 手机是否已通过 USB 连接到电脑；"
    echo -e "  2. 手机是否在「开发者选项」中开启了「USB 调试」；"
    echo -e "  3. 手机屏幕是否弹出了「允许 USB 调试」并点击了允许。"
    exit 1
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null || echo "Unknown")
ANDROID_VER=$(adb shell getprop ro.build.version.release 2>/dev/null || echo "Unknown")
echo -e "${GREEN}[成功] 已识别目标设备: ${DEVICE_MODEL} (Android ${ANDROID_VER})${NC}"

# 3. 检查或编译 APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
echo -e "\n${YELLOW}[2/4] 检查 APK 状态...${NC}"

if [ ! -f "$APK_PATH" ]; then
    echo -e "${YELLOW}未发现编译好的 APK，正在尝试自动构建...${NC}"
    GRADLE_BIN="/Users/$USER/.gradle-bins/gradle-8.4/bin/gradle"
    if [ -f "$GRADLE_BIN" ]; then
        $GRADLE_BIN assembleDebug
    elif [ -f "./gradlew" ]; then
        ./gradlew assembleDebug
    else
        echo -e "${RED}[提示] 请先运行 ./build.sh，然后再运行本脚本。${NC}"
        exit 1
    fi
else
    echo -e "${GREEN}找到已有 APK: ${APK_PATH}${NC}"
fi

# 4. 安装 APK
echo -e "\n${YELLOW}[3/4] 正在通过 ADB 推送安装应用...${NC}"
echo -e "${CYAN}提示: 如手机屏幕弹出「允许 USB 安装应用」确认对话框，请在手机上点击允许。${NC}"

adb install -r -t "$APK_PATH"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}[成功] 应用安装完成！${NC}"
else
    echo -e "${RED}[错误] 安装失败，请检查手机屏幕是否阻止了安装权限。${NC}"
    exit 1
fi

# 5. 启动应用并进入实时调试日志
echo -e "\n${YELLOW}[4/4] 正在启动应用并捕获实时 DSP 日志...${NC}"
PACKAGE_NAME="com.antigravity.equalizer"
ACTIVITY_NAME=".ui.MainActivity"

adb shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"

echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}应用已成功在手机上启动！${NC}"
echo -e "${CYAN}正在监听 DSP 核心、音频会话与设备管理器日志 (按 Ctrl+C 退出)...${NC}"
echo -e "${CYAN}======================================================${NC}\n"

# 过滤显示均衡器相关的实时 logcat
adb logcat -v color -s NativeDSP EqualizerService AudioEffectManager AudioSessionManager DeviceManager AndroidRuntime:E
