#!/bin/bash
set -e

SDK_DIR="/Volumes/BOOTCAMP/Android/sdk"
TEMP_DIR="$SDK_DIR/.temp"
TARGET_BASE="$SDK_DIR/system-images/android-30/google_apis"
TARGET_IMG_DIR="$TARGET_BASE/x86_64"
ZIP_URL="https://dl.google.com/android/repository/sys-img/google_apis/x86_64-30_r12.zip"
ZIP_FILE="$TEMP_DIR/x86_64-30_r12.zip"

echo "=== [1/4] 检查/下载 Android 11 (API 30) x86_64 系统镜像 ==="
if [ -f "$TARGET_IMG_DIR/system.img" ]; then
    echo "系统镜像已存在: $TARGET_IMG_DIR"
else
    mkdir -p "$TEMP_DIR" "$TARGET_BASE"
    echo "正在从 Google 官方高速下载系统镜像 (约 1.4GB)..."
    curl -L --progress-bar -C - "$ZIP_URL" -o "$ZIP_FILE"
    
    echo "正在解压系统镜像..."
    unzip -q -o "$ZIP_FILE" -d "$TARGET_BASE"
    
    # 检查解压结构是否包含顶层 x86_64
    if [ ! -d "$TARGET_IMG_DIR" ] && [ -f "$TARGET_BASE/system.img" ]; then
        mkdir -p "$TARGET_IMG_DIR"
        mv "$TARGET_BASE"/*.* "$TARGET_IMG_DIR/" 2>/dev/null || true
    fi
    
    # 清理压缩包释放空间
    rm -f "$ZIP_FILE"
fi

if [ ! -f "$TARGET_IMG_DIR/system.img" ]; then
    echo "错误: 系统镜像解压后未找到 system.img"
    exit 1
fi
echo "系统镜像就绪: $TARGET_IMG_DIR"

AVD_DIR="$HOME/.android/avd"
mkdir -p "$AVD_DIR"

echo "=== [2/4] 创建 2K 车机横屏虚拟机 (Car_2K_Android_11: 2560x1440) ==="
AVD_2K="Car_2K_Android_11"
cat << 'EOF' > "$AVD_DIR/${AVD_2K}.ini"
avd.ini.encoding=UTF-8
path=/Users/dengzhiyong/.android/avd/Car_2K_Android_11.avd
path.rel=avd/Car_2K_Android_11.avd
target=android-30
EOF

mkdir -p "$AVD_DIR/${AVD_2K}.avd"
cat << 'EOF' > "$AVD_DIR/${AVD_2K}.avd/config.ini"
AvdId=Car_2K_Android_11
PlayStore.enabled=false
abi.type=x86_64
avd.ini.displayname=Car 2K Android 11 (2560x1440)
avd.ini.encoding=UTF-8
disk.dataPartition.size=6442450944
fastboot.chosenSnapshotFile=
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.arc=false
hw.audioInput=yes
hw.battery=yes
hw.camera.back=emulated
hw.camera.front=emulated
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.dPad=no
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gyroscope=yes
hw.keyboard=yes
hw.lcd.density=280
hw.lcd.height=1440
hw.lcd.width=2560
hw.mainKeys=no
hw.ramSize=3072
hw.sdCard=yes
hw.sensors.light=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=system-images/android-30/google_apis/x86_64/
tag.display=Google APIs
tag.id=google_apis
target=android-30
vm.heapSize=512
EOF

echo "=== [3/4] 创建 4K 车机横屏虚拟机 (Car_4K_Android_11: 3840x2160) ==="
AVD_4K="Car_4K_Android_11"
cat << 'EOF' > "$AVD_DIR/${AVD_4K}.ini"
avd.ini.encoding=UTF-8
path=/Users/dengzhiyong/.android/avd/Car_4K_Android_11.avd
path.rel=avd/Car_4K_Android_11.avd
target=android-30
EOF

mkdir -p "$AVD_DIR/${AVD_4K}.avd"
cat << 'EOF' > "$AVD_DIR/${AVD_4K}.avd/config.ini"
AvdId=Car_4K_Android_11
PlayStore.enabled=false
abi.type=x86_64
avd.ini.displayname=Car 4K Android 11 (3840x2160)
avd.ini.encoding=UTF-8
disk.dataPartition.size=8589934592
fastboot.chosenSnapshotFile=
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.arc=false
hw.audioInput=yes
hw.battery=yes
hw.camera.back=emulated
hw.camera.front=emulated
hw.cpu.arch=x86_64
hw.cpu.ncore=4
hw.dPad=no
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
hw.gyroscope=yes
hw.keyboard=yes
hw.lcd.density=440
hw.lcd.height=2160
hw.lcd.width=3840
hw.mainKeys=no
hw.ramSize=4096
hw.sdCard=yes
hw.sensors.light=yes
hw.sensors.proximity=yes
hw.trackBall=no
image.sysdir.1=system-images/android-30/google_apis/x86_64/
tag.display=Google APIs
tag.id=google_apis
target=android-30
vm.heapSize=512
EOF

echo "=== [4/4] 验证 AVD 列表 ==="
"$SDK_DIR/emulator/emulator" -list-avds
echo "🎉 2K 与 4K Android 11 虚拟机创建完成！"
