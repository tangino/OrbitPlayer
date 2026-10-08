#!/usr/bin/env bash

# 脚本所在目录（即项目根目录）
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 检查 adb 工具是否存在
if ! command -v adb &> /dev/null; then
    echo "错误: 未检测到 adb，请确认已安装 Android SDK 平台工具并配置环境变量。"
    exit 1
fi

echo "======================================================"
echo "   Orbit Player - Android Screenshot"
echo "======================================================"

# 获取已连接并处于 authorized/device 状态的设备列表
mapfile -t ALL_DEVICES < <(adb devices | awk '$2=="device"{print $1}')
DEVICE_COUNT=${#ALL_DEVICES[@]}

if [ "${DEVICE_COUNT}" -eq 0 ]; then
    echo "错误: 未检测到已授权连接的 Android 设备或模拟器。"
    echo "请检查手机是否开启 USB 调试，或启动车机/手机模拟器。"
    exit 1
fi

SELECTED_DEVICES=()

if [ "${DEVICE_COUNT}" -eq 1 ]; then
    DEV="${ALL_DEVICES[0]}"
    DEV_MODEL=$(adb -s "${DEV}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    DEV_VER=$(adb -s "${DEV}" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
    echo "[成功] 已识别目标设备: ${DEV} [${DEV_MODEL:-Android Device}, Android ${DEV_VER:-Unknown}]"
    SELECTED_DEVICES=("${DEV}")
else
    echo "检测到 ${DEVICE_COUNT} 台已连接设备:"
    echo "------------------------------------------------------"
    for i in "${!ALL_DEVICES[@]}"; do
        idx=$((i + 1))
        DEV="${ALL_DEVICES[$i]}"
        DEV_MODEL=$(adb -s "${DEV}" shell getprop ro.product.model 2>/dev/null | tr -d '\r')
        DEV_VER=$(adb -s "${DEV}" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
        echo "  [${idx}] ${DEV} [${DEV_MODEL:-Android Device}, Android ${DEV_VER:-Unknown}]"
    done
    echo "  [A] 截取所有设备"
    echo "  [Q] 退出"
    echo "------------------------------------------------------"

    while true; do
        read -rp "请选择要截图的设备 [1-${DEVICE_COUNT} / A / Q] (默认: 1): " choice
        choice=${choice:-1}

        if [[ "${choice}" =~ ^[Qq]$ ]]; then
            echo "已取消截图操作。"
            exit 0
        elif [[ "${choice}" =~ ^[Aa]$ ]]; then
            SELECTED_DEVICES=("${ALL_DEVICES[@]}")
            break
        elif [[ "${choice}" =~ ^[0-9]+$ ]] && [ "${choice}" -ge 1 ] && [ "${choice}" -le "${DEVICE_COUNT}" ]; then
            SELECTED_DEVICES=("${ALL_DEVICES[$((choice - 1))]}")
            break
        else
            echo "输入无效，请重新输入！"
        fi
    done
fi

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TEMP_REMOTE="/sdcard/screenshot_temp_${RANDOM}.png"
SUCCESS_COUNT=0

echo ""
echo "正在执行截屏并传输至电脑..."

for DEV in "${SELECTED_DEVICES[@]}"; do
    SAFE_NAME=$(echo "${DEV}" | tr ':.' '__')
    if [ "${DEVICE_COUNT}" -eq 1 ]; then
        OUTPUT_FILE="${ROOT_DIR}/screenshot_${TIMESTAMP}.png"
    else
        OUTPUT_FILE="${ROOT_DIR}/screenshot_${SAFE_NAME}_${TIMESTAMP}.png"
    fi

    echo "  - 正在截取 [${DEV}] 屏幕..."
    adb -s "${DEV}" shell screencap -p "${TEMP_REMOTE}" >/dev/null 2>&1
    if [ $? -eq 0 ]; then
        adb -s "${DEV}" pull "${TEMP_REMOTE}" "${OUTPUT_FILE}" >/dev/null 2>&1
        if [ -s "${OUTPUT_FILE}" ]; then
            echo "    [成功] 已保存至: ${OUTPUT_FILE}"
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        else
            echo "    [错误] 拉取截图文件失败: ${DEV}"
        fi
        adb -s "${DEV}" shell rm -f "${TEMP_REMOTE}" >/dev/null 2>&1
    else
        echo "    [错误] 设备截屏命令执行失败: ${DEV}"
    fi
done

echo ""
if [ "${SUCCESS_COUNT}" -gt 0 ]; then
    echo "======================================================"
    echo "🎉 截图完成！共成功截取 ${SUCCESS_COUNT} 张图片。"
    echo "======================================================"
else
    echo "错误: 未能保存任何截图。"
    exit 1
fi
