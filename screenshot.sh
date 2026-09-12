#!/usr/bin/env bash

# 脚本所在目录（即项目根目录）
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 检查 adb 工具是否存在
if ! command -v adb &> /dev/null; then
    echo "错误: 未检测到 adb，请确认已安装 Android SDK 平台工具并配置环境变量。"
    exit 1
fi

# 检查是否连接了设备
DEVICE_STATUS=$(adb get-state 2>&1)
if [[ "${DEVICE_STATUS}" != "device" ]]; then
    echo "错误: 未连接 Android 设备或未授权 USB 调试（当前状态: ${DEVICE_STATUS}）"
    exit 1
fi

# 生成基于时间戳的文件名，保存在根目录
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
OUTPUT_FILE="${ROOT_DIR}/screenshot_${TIMESTAMP}.png"

echo "正在截取当前手机画面..."
adb exec-out screencap -p > "${OUTPUT_FILE}"

# 验证截屏结果
if [ -s "${OUTPUT_FILE}" ]; then
    echo "截图成功！已保存至:"
    echo "${OUTPUT_FILE}"
else
    echo "错误: 截图失败或生成文件为空。"
    rm -f "${OUTPUT_FILE}"
    exit 1
fi
