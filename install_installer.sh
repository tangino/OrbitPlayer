#!/bin/bash

# ==============================================================================
# Orbit Installer (车机安装助手) - 一键安装与启动脚本
# ==============================================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}======================================================${NC}"
echo -e "${CYAN}     Orbit Installer (车机安装助手) - 一键安装工具    ${NC}"
echo -e "${CYAN}======================================================${NC}"

# 定位工程根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if [ -f "$SCRIPT_DIR/build_installer.sh" ]; then
    ROOT_DIR="$SCRIPT_DIR"
elif [ -f "$SCRIPT_DIR/../build_installer.sh" ]; then
    ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
else
    ROOT_DIR="$PWD"
fi

# 1. 解析参数 (debug 或 release, 以及是否跟随日志)
BUILD_TYPE="debug"
FOLLOW_LOG=false

for arg in "$@"; do
    case $arg in
        release|Release|--release|-r)
            BUILD_TYPE="release"
            ;;
        debug|Debug|--debug|-d)
            BUILD_TYPE="debug"
            ;;
        --log|-l|log)
            FOLLOW_LOG=true
            ;;
    esac
done

echo -e "${YELLOW}🎯 目标安装类型: [ ${BUILD_TYPE} ]${NC}"

# 2. 定位 Android SDK 与 ADB
if [ -d "/Volumes/BOOTCAMP/Android/sdk" ]; then
    export ANDROID_HOME="/Volumes/BOOTCAMP/Android/sdk"
else
    export ANDROID_HOME="${ANDROID_HOME:-/Users/$USER/Library/Android/sdk}"
fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

if ! command -v adb &> /dev/null; then
    echo -e "${RED}[错误] 未找到 adb 命令，请确认 Android SDK 路径: $ANDROID_HOME${NC}"
    exit 1
fi
echo -e "${GREEN}[1/4] Android SDK 与 ADB: 已就绪${NC}"

# 3. 检查设备连接状态
echo -e "\n${YELLOW}[2/4] 正在检测已连接的车机/设备...${NC}"
DEVICES=$(adb devices | grep -w "device" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo -e "${RED}[错误] 未检测到已连接的 Android 车机或调试设备！${NC}"
    echo -e "排查提示："
    echo -e "  1. 确认车机/设备已通过 USB 连接或已开启无线 ADB 调试；"
    echo -e "  2. 确认车机已开启「开发者选项」中的「USB 调试」或「无线调试」；"
    echo -e "  3. 若为无线连接，请先执行: adb connect <车机IP:端口>"
    exit 1
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null || echo "车载设备")
ANDROID_VER=$(adb shell getprop ro.build.version.release 2>/dev/null || echo "未知版本")
echo -e "${GREEN}[成功] 目标设备: ${DEVICE_MODEL} (Android ${ANDROID_VER})${NC}"

# 4. 查找或自动构建 APK
echo -e "\n${YELLOW}[3/4] 检查安装包文件...${NC}"
if [ "$BUILD_TYPE" = "release" ]; then
    APK_DIR="$ROOT_DIR/installer/build/outputs/apk/release"
    PACKAGE_NAME="com.orbit.installer"
else
    APK_DIR="$ROOT_DIR/installer/build/outputs/apk/debug"
    PACKAGE_NAME="com.orbit.installer.debug"
fi

APK_PATH=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -n 1 || true)

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${YELLOW}未检测到编译产物，正在为您自动构建 ${BUILD_TYPE} 版本...${NC}"
    "$ROOT_DIR/build_installer.sh" "$BUILD_TYPE"
    APK_PATH=$(find "$APK_DIR" -name "*.apk" 2>/dev/null | head -n 1 || true)
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}[错误] 找不到 APK 安装包: $APK_DIR${NC}"
    exit 1
fi

APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
echo -e "${GREEN}[成功] 待安装文件: ${APK_PATH} (${APK_SIZE})${NC}"

# 5. 执行 ADB 推送安装
echo -e "\n${YELLOW}[4/4] 正在推送安装至车机...${NC}"
adb install -r -d -t "$APK_PATH"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}[成功] 安装完成！${NC}"
else
    echo -e "${RED}[错误] 安装失败，请检查车机屏幕是否阻止了安装或存在签名冲突。${NC}"
    exit 1
fi

# 6. 启动应用
echo -e "\n${YELLOW}正在车机上启动应用...${NC}"
ACTIVITY_NAME="com.orbit.installer.MainActivity"
adb shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"

echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}🎉 车机安装助手已成功安装并启动！${NC}"
echo -e "${CYAN}======================================================${NC}"

# 7. 可选：查看实时日志
if [ "$FOLLOW_LOG" = true ]; then
    echo -e "\n${YELLOW}正在监听应用实时日志 (按 Ctrl+C 退出)...${NC}"
    adb logcat -v color -s OrbitInstaller DownloadManager PackageInstallerHelper AndroidRuntime:E
fi
