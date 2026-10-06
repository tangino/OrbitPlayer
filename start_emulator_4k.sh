#!/bin/bash
SDK_DIR="/Volumes/BOOTCAMP/Android/sdk"
echo "正在启动 4K 车机横屏虚拟机 (3840x2160)..."
"$SDK_DIR/emulator/emulator" -avd Car_4K_Android_11 -gpu host "$@"
