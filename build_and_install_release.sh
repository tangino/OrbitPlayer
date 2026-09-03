#!/bin/bash

# ==============================================================================
# Orbit Player - macOS 一键构建与安装发布版本 (Release) 脚本
# 支持特性：
# 1. 自动适配 macOS 各种 JDK 17 安装路径 (Homebrew openjdk@17 / Android Studio JBR)
# 2. 自动配置 Android SDK 与 ADB 环境
# 3. 极速编译 Release 发布版 APK (C++ JNI Release 级深度优化与符号剥离)
# 4. 自动检测已连接的 Android 设备 / 模拟器并一键推流安装与启动
# 5. 支持参数: 
#    --build-only    仅构建发布版 APK，不执行设备安装
#    --install-only  跳过构建，直接安装现有 Release APK
#    --clean         构建前先执行 ./gradlew clean
# ==============================================================================

set -e

# 终端彩色输出
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BOLD='\033[1m'
NC='\033[0m' # No Color

echo -e "${CYAN}================================================================${NC}"
echo -e "${CYAN}${BOLD}       Orbit Player - macOS 一键构建与安装发布版本 (Release)     ${NC}"
echo -e "${CYAN}================================================================${NC}"

# 获取脚本所在根目录并进入
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# 解析命令行参数
BUILD_ONLY=false
INSTALL_ONLY=false
DO_CLEAN=false

for arg in "$@"; do
    case "$arg" in
        --build-only)
            BUILD_ONLY=true
            ;;
        --install-only)
            INSTALL_ONLY=true
            ;;
        --clean)
            DO_CLEAN=true
            ;;
        --help|-h)
            echo -e "用法: $0 [选项]"
            echo -e "选项:"
            echo -e "  --build-only    仅执行 Release APK 编译，不执行安装"
            echo -e "  --install-only  跳过编译，直接将已有的 Release APK 安装到设备"
            echo -e "  --clean         编译前先执行 clean 清理"
            echo -e "  -h, --help      显示帮助信息"
            exit 0
            ;;
    esac
done

# ==============================================================================
# 1. 环境检测与配置 (Android SDK & JDK 17)
# ==============================================================================
echo -e "\n${YELLOW}[1/4] 检测与配置 macOS 编译环境...${NC}"

# 1.1 Android SDK
export ANDROID_HOME="${ANDROID_HOME:-/Users/$USER/Library/Android/sdk}"
if [ -d "$ANDROID_HOME" ]; then
    export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$PATH"
    echo -e "  ${GREEN}✓${NC} 找到 Android SDK: ${ANDROID_HOME}"
else
    echo -e "  ${YELLOW}!${NC} 默认 SDK 路径不存在: ${ANDROID_HOME}，尝试依赖系统环境变量 PATH..."
fi

# 1.2 JDK 17
POSSIBLE_JDK_PATHS=(
    "/usr/local/opt/openjdk@17"
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/opt/homebrew/opt/openjdk@17"
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/Applications/Android Studio.app/Contents/jre/Contents/Home"
    "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
    "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
)

FOUND_JDK=""
for p in "${POSSIBLE_JDK_PATHS[@]}"; do
    if [ -d "$p" ]; then
        FOUND_JDK="$p"
        break
    fi
done

if [ -n "$FOUND_JDK" ]; then
    export JAVA_HOME="$FOUND_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo -e "  ${GREEN}✓${NC} 已配置 JDK 17: ${JAVA_HOME}"
else
    echo -e "  ${YELLOW}!${NC} 未在常见路径中匹配到独立 JDK 17，使用当前默认 Java: $(which java || echo '未找到')"
fi

# 1.3 Gradle 包装器
if [ ! -f "./gradlew" ]; then
    echo -e "${RED}[错误] 当前目录下未找到 gradlew 执行文件！请在项目根目录运行本脚本。${NC}"
    exit 1
fi
chmod +x ./gradlew

# ==============================================================================
# 2. 编译发布版本 (Release APK)
# ==============================================================================
RELEASE_APK="app/build/outputs/apk/release/OrbitPlayer.apk"

if [ "$INSTALL_ONLY" = true ]; then
    echo -e "\n${YELLOW}[2/4] 跳过编译阶段 (--install-only)...${NC}"
    if [ ! -f "$RELEASE_APK" ]; then
        echo -e "${RED}[错误] 未找到已有发布版 APK: ${RELEASE_APK}，无法执行安装！请先去掉 --install-only 执行编译。${NC}"
        exit 1
    fi
else
    echo -e "\n${YELLOW}[2/4] 开始执行 Release 发布版本构建...${NC}"
    START_TIME=$(date +%s)

    GRADLE_ARGS=("assembleRelease")
    if [ "$DO_CLEAN" = true ]; then
        echo -e "  ${CYAN}正在执行 clean 清理...${NC}"
        ./gradlew clean
    fi

    echo -e "  ${CYAN}正在执行 ./gradlew assembleRelease (包含 C++ JNI 与 Kotlin 生产编译优化)...${NC}"
    ./gradlew assembleRelease

    END_TIME=$(date +%s)
    DURATION=$((END_TIME - START_TIME))

    if [ -f "$RELEASE_APK" ]; then
        APK_SIZE=$(ls -lh "$RELEASE_APK" | awk '{print $5}')
        echo -e "\n${GREEN}================================================================${NC}"
        echo -e "${GREEN}${BOLD}✓ Release 发布版 APK 构建成功！耗时: ${DURATION}s${NC}"
        echo -e "  产物路径: ${BOLD}${PROJECT_ROOT}/${RELEASE_APK}${NC}"
        echo -e "  文件大小: ${BOLD}${APK_SIZE}${NC}"
        echo -e "${GREEN}================================================================${NC}"
    else
        echo -e "${RED}[错误] 编译完成但未在 ${RELEASE_APK} 找到生成产物！${NC}"
        exit 1
    fi
fi

# 如果指定了 --build-only，直接退出
if [ "$BUILD_ONLY" = true ]; then
    echo -e "\n${CYAN}提示: 您指定了 --build-only，已跳过设备安装步骤。${NC}"
    echo -e "可在 Finder 中查看产物: ${YELLOW}open $(dirname "$PROJECT_ROOT/$RELEASE_APK")${NC}\n"
    exit 0
fi

# ==============================================================================
# 3. 检查已连接的设备 (ADB)
# ==============================================================================
echo -e "\n${YELLOW}[3/4] 检查目标 Android 设备或模拟器...${NC}"

if ! command -v adb &> /dev/null; then
    echo -e "${YELLOW}[提示] 未在系统中检测到 adb 命令，跳过自动安装。${NC}"
    echo -e "您可手动将以下 APK 安装到手机: ${BOLD}${PROJECT_ROOT}/${RELEASE_APK}${NC}"
    exit 0
fi

DEVICES=$(adb devices 2>/dev/null | grep -w "device" | awk '{print $1}')

if [ -z "$DEVICES" ]; then
    echo -e "${YELLOW}[提示] 当前未检测到已连接并授权的 Android 设备或模拟器。${NC}"
    echo -e "已为您成功生成了发布版安装包："
    echo -e "  文件位置: ${GREEN}${PROJECT_ROOT}/${RELEASE_APK}${NC}"
    echo -e "如需安装到手机："
    echo -e "  1. 用 USB 数据线连接手机并打开「开发者选项」中的「USB 调试」；"
    echo -e "  2. 手机上点击「允许 USB 调试」；"
    echo -e "  3. 再次运行: ${BOLD}./build_and_install_release.sh --install-only${NC}"
    echo -e "\n也可在 Mac 访达中定位该文件: ${CYAN}open $(dirname "$PROJECT_ROOT/$RELEASE_APK")${NC}\n"
    exit 0
fi

# 获取设备信息
DEVICE_COUNT=$(echo "$DEVICES" | wc -l | awk '{print $1}')
FIRST_DEVICE=$(echo "$DEVICES" | head -n 1)
DEVICE_MODEL=$(adb -s "$FIRST_DEVICE" shell getprop ro.product.model 2>/dev/null || echo "Android 设备")
ANDROID_VER=$(adb -s "$FIRST_DEVICE" shell getprop ro.build.version.release 2>/dev/null || echo "未知")

echo -e "  ${GREEN}✓${NC} 成功识别目标设备 (${DEVICE_COUNT} 台): ${BOLD}${DEVICE_MODEL}${NC} (Android ${ANDROID_VER}, ID: ${FIRST_DEVICE})"

# ==============================================================================
# 4. 安装并自动启动应用
# ==============================================================================
echo -e "\n${YELLOW}[4/4] 正在安装 Release 发布版 APK 并启动...${NC}"
echo -e "  目标设备: ${FIRST_DEVICE}"
echo -e "  正在传输与安装 ${RELEASE_APK} ..."

if adb -s "$FIRST_DEVICE" install -r -d -t "$RELEASE_APK"; then
    echo -e "  ${GREEN}✓ 安装成功！${NC}"
    
    PACKAGE_NAME="com.antigravity.equalizer"
    ACTIVITY_NAME=".ui.MainActivity"
    
    echo -e "  正在启动 Orbit Player 发布版本..."
    adb -s "$FIRST_DEVICE" shell am start -n "${PACKAGE_NAME}/${ACTIVITY_NAME}" > /dev/null 2>&1 || true
    
    echo -e "\n${GREEN}================================================================${NC}"
    echo -e "${GREEN}${BOLD}🎉 Orbit Player 发布版本已成功安装并启动！${NC}"
    echo -e "${GREEN}================================================================${NC}\n"
else
    echo -e "\n${RED}[错误] 安装失败！可能原因：${NC}"
    echo -e "  1. 手机屏幕弹出「是否允许通过 USB 安装应用」确认框，未及时点击「允许」；"
    echo -e "  2. 手机开启了纯净模式/安装验证，需在手机上手动授权。"
    echo -e "  安装包位置: ${BOLD}${PROJECT_ROOT}/${RELEASE_APK}${NC}\n"
    exit 1
fi
