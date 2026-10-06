#!/bin/bash
SDK_DIR="/Volumes/BOOTCAMP/Android/sdk"
echo "正在启动 2K 车机横屏虚拟机 (2560x1440)..."
"$SDK_DIR/emulator/emulator" -avd Car_2K_Android_11 -gpu host "$@"
