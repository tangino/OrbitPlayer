#!/bin/bash

# ==============================================================================
# Orbit Player - 一键编译构建脚本
# ==============================================================================

set -e

# 颜色输出
GREEN='\033[0;32m'
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${CYAN}======================================================${NC}"
echo -e "${CYAN}     Orbit Player - 完整编译构建           ${NC}"
echo -e "${CYAN}======================================================${NC}"

# 1. 定位 Android SDK
export ANDROID_HOME="${ANDROID_HOME:-/Users/$USER/Library/Android/sdk}"
if [ ! -d "$ANDROID_HOME" ]; then
    echo -e "${RED}[错误] 未找到 Android SDK 路径: $ANDROID_HOME${NC}"
    exit 1
fi
echo -e "${GREEN}[1/3] Android SDK: ${ANDROID_HOME}${NC}"

# 2. 检测并配置 Java 17+ 编译环境
echo -e "${YELLOW}[2/3] 正在配置 Java 17 编译环境...${NC}"

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
    echo -e "${GREEN}      已选用 JDK 17: ${JAVA_HOME}${NC}"
fi

# 3. 确定 Gradle 执行命令
echo -e "\n${YELLOW}[3/3] 正在执行 Gradle 构建任务...${NC}"

GRADLE_BIN="/Users/$USER/.gradle-bins/gradle-8.4/bin/gradle"
if [ ! -f "$GRADLE_BIN" ]; then
    if [ -f "./gradlew" ]; then
        GRADLE_BIN="./gradlew"
    else
        GRADLE_BIN="gradle"
    fi
fi

echo -e "${GREEN}      使用 Gradle: ${GRADLE_BIN}${NC}"

# 支持传入参数如 --clean
if [[ "$*" == *"--clean"* ]]; then
    echo -e "${YELLOW}正在执行 clean 清理...${NC}"
    $GRADLE_BIN clean
fi

# 执行完整编译任务
$GRADLE_BIN assembleDebug

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo -e "\n${CYAN}======================================================${NC}"
    echo -e "${GREEN}🎉 编译成功！${NC}"
    echo -e "${GREEN}   产物路径: ${APK_PATH}${NC}"
    echo -e "${GREEN}   文件大小: ${APK_SIZE}${NC}"
    echo -e "${CYAN}======================================================${NC}"
    echo -e "\n${YELLOW}提示：您可直接运行 ./install_and_debug.sh 将 APK 一键安装到手机并开启调试！${NC}"
else
    echo -e "${RED}[错误] 编译未生成预期的 APK 文件。${NC}"
    exit 1
fi
