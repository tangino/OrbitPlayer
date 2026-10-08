#!/bin/bash

# ==============================================================================
# Orbit Player - 一键编译、多设备选择安装与实时调试脚本
# ==============================================================================

set -e

# 颜色定义
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${CYAN}======================================================${NC}"
echo -e "${CYAN}     Orbit Player - 多设备一键安装与定向调试工具      ${NC}"
echo -e "${CYAN}======================================================${NC}"

# 1. 自动配置 Android SDK 路径与 ADB
if [ -d "/Volumes/BOOTCAMP/Android/sdk" ]; then
    export ANDROID_HOME="/Volumes/BOOTCAMP/Android/sdk"
else
    export ANDROID_HOME="${ANDROID_HOME:-/Users/$USER/Library/Android/sdk}"
fi
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

if ! command -v adb &> /dev/null; then
    echo -e "${RED}[错误] 未找到 adb 命令，请确保 Android SDK 安装在: $ANDROID_HOME${NC}"
    exit 1
fi

# 2. 多设备检测与选择
echo -e "\n${YELLOW}[1/4] 正在检测已连接的 Android 设备与车机/模拟器...${NC}"

# 获取所有已授权的设备序列号列表
DEVICE_LIST=()
while IFS= read -r line; do
    [ -n "$line" ] && DEVICE_LIST+=("$line")
done < <(adb devices | awk '$2=="device" {print $1}')

DEVICE_COUNT=${#DEVICE_LIST[@]}

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${RED}[错误] 未检测到任何已连接且已授权的 Android 设备或虚拟机！${NC}"
    echo -e "请检查："
    echo -e "  1. 手机/车机是否已通过 USB 数据线或局域网 Wi-Fi ADB 连接；"
    echo -e "  2. 目标设备是否在「开发者选项」中开启了「USB 调试」；"
    echo -e "  3. 设备屏幕是否弹出了「允许 USB 调试」并点击了允许；"
    echo -e "  4. 尝试在终端运行: adb kill-server && adb devices"
    exit 1
fi

TARGET_SERIAL=""

# 如果命令行传入了设备序列号参数 (例如: ./install_and_debug.sh -s <serial> 或直接传入序列号)
if [ "$1" == "-s" ] && [ -n "$2" ]; then
    TARGET_SERIAL="$2"
elif [ -n "$1" ] && [[ "$1" != -* ]]; then
    # 尝试按序号或序列号匹配
    if [[ "$1" =~ ^[0-9]+$ ]] && [ "$1" -ge 1 ] && [ "$1" -le "$DEVICE_COUNT" ]; then
        TARGET_SERIAL="${DEVICE_LIST[$(( $1 - 1 ))]}"
    else
        TARGET_SERIAL="$1"
    fi
fi

if [ -z "$TARGET_SERIAL" ]; then
    if [ "$DEVICE_COUNT" -eq 1 ]; then
        TARGET_SERIAL="${DEVICE_LIST[0]}"
    else
        echo -e "${CYAN}检测到当前连接了多个 Android 设备 (${DEVICE_COUNT} 台)：${NC}"
        echo -e "------------------------------------------------------"
        for i in "${!DEVICE_LIST[@]}"; do
            serial="${DEVICE_LIST[$i]}"
            model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "未知型号")
            brand=$(adb -s "$serial" shell getprop ro.product.brand 2>/dev/null | tr -d '\r' || echo "")
            ver=$(adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "未知")
            echo -e "  ${BOLD}[$((i + 1))]${NC} ${GREEN}${brand} ${model}${NC} (Android ${ver}) - [序列号: ${CYAN}${serial}${NC}]"
        done
        echo -e "------------------------------------------------------"

        while true; do
            read -p "👉 请输入要安装调试的目标设备序号 (1-${DEVICE_COUNT}) [默认 1]: " choice
            choice="${choice:-1}"
            if [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "$DEVICE_COUNT" ]; then
                TARGET_SERIAL="${DEVICE_LIST[$((choice - 1))]}"
                break
            else
                echo -e "${RED}输入无效，请输入 1 到 ${DEVICE_COUNT} 之间的数字！${NC}"
            fi
        done
    fi
fi

# 获取所选设备的详细硬件信息
TARGET_MODEL=$(adb -s "$TARGET_SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "Unknown")
TARGET_BRAND=$(adb -s "$TARGET_SERIAL" shell getprop ro.product.brand 2>/dev/null | tr -d '\r' || echo "")
TARGET_VER=$(adb -s "$TARGET_SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "Unknown")
TARGET_SDK=$(adb -s "$TARGET_SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || echo "Unknown")

echo -e "${GREEN}[成功] 已选用目标设备: ${BOLD}${TARGET_BRAND} ${TARGET_MODEL}${NC} (Android ${TARGET_VER}, API ${TARGET_SDK}) [${TARGET_SERIAL}]"

# 3. 检查或编译 APK
APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
echo -e "\n${YELLOW}[2/4] 检查 APK 安装包状态...${NC}"

if [ ! -f "$APK_PATH" ]; then
    echo -e "${YELLOW}未发现编译好的 APK，正在调用 ./build.sh 自动执行构建...${NC}"
    if [ -f "./build.sh" ]; then
        ./build.sh
    else
        ./gradlew assembleDebug
    fi
else
    APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo -e "${GREEN}找到已有 APK: ${APK_PATH} (大小: ${APK_SIZE})${NC}"
fi

# 4. 定向安装 APK
echo -e "\n${YELLOW}[3/4] 正在向目标设备 [${TARGET_SERIAL}] 推送安装...${NC}"
echo -e "${CYAN}提示: 如屏幕弹出「允许 USB 安装应用」确认对话框，请在设备屏幕上点击允许。${NC}"

adb -s "$TARGET_SERIAL" install -r -t "$APK_PATH"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}[成功] 应用安装完成！${NC}"
else
    echo -e "${RED}[错误] 安装失败，请检查设备是否处于锁屏状态或阻止了安装权限。${NC}"
    exit 1
fi

# 5. 启动应用并进入实时调试日志
echo -e "\n${YELLOW}[4/4] 正在启动应用并捕获实时核心日志...${NC}"
PACKAGE_NAME="com.orbit.music"
ACTIVITY_NAME=".ui.MainActivity"

adb -s "$TARGET_SERIAL" shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}"

echo -e "\n${CYAN}======================================================${NC}"
echo -e "${GREEN}🎉 应用已在 ${TARGET_MODEL} 上成功启动！${NC}"
echo -e "${CYAN}正在捕获 DSP 音频核心、音源引擎、平台登录与系统异常日志 (按 Ctrl+C 退出)...${NC}"
echo -e "${CYAN}======================================================${NC}\n"

# 优先清空当前缓冲中的陈旧日志
adb -s "$TARGET_SERIAL" logcat -c

# 过滤显示音频核心、在线音源、平台鉴权与崩溃异常日志
adb -s "$TARGET_SERIAL" logcat -v color -s \
    NativeDSP \
    EqualizerService \
    AudioEffectManager \
    AudioSessionManager \
    DeviceManager \
    OnlineAudioSourceMgr \
    PlatformAccountMgr \
    QQMusicAuthService \
    KugouMusicAuthService \
    LxSourceEngine \
    AndroidRuntime:E \
    DEBUG:E

