#!/bin/bash
SDK_DIR="/Volumes/BOOTCAMP/Android/sdk"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDCARD_IMG="$SCRIPT_DIR/sdcard_2k.img"

# 如果虚拟 SD 卡镜像不存在，则自动创建 2GB 镜像
if [ ! -f "$SDCARD_IMG" ]; then
    echo "正在创建 2GB 虚拟 SD 卡镜像..."
    "$SDK_DIR/emulator/mksdcard" 2048M "$SDCARD_IMG"
fi

echo "正在启动 2K 车机横屏虚拟机 (2560x1440)，已挂载虚拟 SD 卡..."
"$SDK_DIR/emulator/emulator" -avd Car_2K_Android_11 -gpu host -sdcard "$SDCARD_IMG" "$@"
