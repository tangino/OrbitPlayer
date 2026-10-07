#!/bin/bash

# ==============================================================================
# Orbit Installer (车机安装助手) - 专属编译构建脚本
# 支持 Debug 和 Release 编译
# ==============================================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}======================================================${NC}"
echo -e "${CYAN}     Orbit Installer (车机安装助手) - 编译构建        ${NC}"
echo -e "${CYAN}======================================================${NC}"

# 1. 确定构建类型 (debug / release)
BUILD_TYPE="debug"
CLEAN_BUILD=false

for arg in "$@"; do
    case $arg in
        release|Release|--release|-r)
            BUILD_TYPE="release"
            ;;
        debug|Debug|--debug|-d)
            BUILD_TYPE="debug"
            ;;
        clean|--clean|-c)
            CLEAN_BUILD=true
            ;;
    esac
done

echo -e "${YELLOW}🎯 目标构建模式: [ ${BUILD_TYPE} ]${NC}"

# 2. 定位 Android SDK
if [ -d "/Volumes/BOOTCAMP/Android/sdk" ]; then
    export ANDROID_HOME="/Volumes/BOOTCAMP/Android/sdk"
else
    export ANDROID_HOME="${ANDROID_HOME:-/Users/$USER/Library/Android/sdk}"
fi

if [ ! -d "$ANDROID_HOME" ]; then
    echo -e "${RED}[错误] 未找到 Android SDK 路径: $ANDROID_HOME${NC}"
    exit 1
fi
echo -e "${GREEN}[1/3] Android SDK: ${ANDROID_HOME}${NC}"

# 3. 配置 Java 17+ 编译环境
echo -e "${YELLOW}[2/3] 正在检测 Java 17 编译环境...${NC}"

POSSIBLE_JDK_PATHS=(
    "/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home"
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home"
    "/Applications/Android Studio.app/Contents/jre/Contents/Home"
    "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home"
    "/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
)

FOUND_JDK=""
for path in "${POSSIBLE_JDK_PATHS[@]}"; do
    if [ -d "$path" ]; then
        FOUND_JDK="$path"
        break
    fi
done

if [ -n "$FOUND_JDK" ]; then
    export JAVA_HOME="$FOUND_JDK"
    export PATH="$JAVA_HOME/bin:$PATH"
    echo -e "${GREEN}      已选用 JDK: ${JAVA_HOME}${NC}"
fi

# 4. 确定 Gradle 执行命令
echo -e "\n${YELLOW}[3/3] 正在执行 Gradle 构建任务...${NC}"

GRADLE_BIN="/Users/$USER/.gradle-bins/gradle-8.4/bin/gradle"
if [ ! -f "$GRADLE_BIN" ]; then
    if [ -f "./gradlew" ]; then
        GRADLE_BIN="./gradlew"
    else
        GRADLE_BIN="gradle"
    fi
fi

GRADLE_EXTRA_OPTS=()
if [ -n "$FOUND_JDK" ]; then
    GRADLE_EXTRA_OPTS+=("-Dorg.gradle.java.home=$FOUND_JDK")
fi

# 若指定了 clean
if [ "$CLEAN_BUILD" = true ]; then
    echo -e "${YELLOW}正在执行 clean 清理...${NC}"
    $GRADLE_BIN "${GRADLE_EXTRA_OPTS[@]}" :installer:clean
fi

# 根据构建类型执行相应任务
if [ "$BUILD_TYPE" = "release" ]; then
    $GRADLE_BIN "${GRADLE_EXTRA_OPTS[@]}" :installer:assembleRelease
    OUTPUT_DIR="installer/build/outputs/apk/release"
else
    $GRADLE_BIN "${GRADLE_EXTRA_OPTS[@]}" :installer:assembleDebug
    OUTPUT_DIR="installer/build/outputs/apk/debug"
fi

# 查找生成的 APK 文件
APK_FILE=$(find "$OUTPUT_DIR" -name "*.apk" | head -n 1)

if [ -n "$APK_FILE" ] && [ -f "$APK_FILE" ]; then
    APK_SIZE=$(ls -lh "$APK_FILE" | awk '{print $5}')
    echo -e "\n${CYAN}======================================================${NC}"
    echo -e "${GREEN}🎉 编译成功！${NC}"
    echo -e "${GREEN}   构建类型: ${BUILD_TYPE}${NC}"
    echo -e "${GREEN}   产物路径: ${APK_FILE}${NC}"
    echo -e "${GREEN}   文件大小: ${APK_SIZE}${NC}"
    echo -e "${CYAN}======================================================${NC}"
else
    echo -e "${RED}[错误] 编译完成但未在 ${OUTPUT_DIR} 找到预期的 APK 文件。${NC}"
    exit 1
fi
