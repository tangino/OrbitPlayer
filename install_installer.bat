@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ======================================================
echo      Orbit Installer (车机安装助手) - 一键安装
echo ======================================================

set BUILD_TYPE=debug

:parse_args
if "%~1"=="" goto after_args
if /i "%~1"=="release" set BUILD_TYPE=release
if /i "%~1"=="-r" set BUILD_TYPE=release
if /i "%~1"=="--release" set BUILD_TYPE=release
if /i "%~1"=="debug" set BUILD_TYPE=debug
if /i "%~1"=="-d" set BUILD_TYPE=debug
if /i "%~1"=="--debug" set BUILD_TYPE=debug
shift
goto parse_args
:after_args

echo [1/3] 检查 ADB 设备连接...
adb devices
for /f "skip=1 tokens=1" %%i in ('adb devices') do (
    if not "%%i"=="" (
        set FOUND_DEVICE=%%i
        goto device_found
    )
)

echo [错误] 未检测到已连接的 Android 车机或调试设备！
pause
exit /b 1

:device_found
echo [成功] 目标设备已连接: %FOUND_DEVICE%

if /i "%BUILD_TYPE%"=="release" (
    set APK_PATH=installer\build\outputs\apk\release\OrbitInstaller-v1.0.0-release.apk
    set PKG_NAME=com.orbit.installer
) else (
    set APK_PATH=installer\build\outputs\apk\debug\OrbitInstaller-v1.0.0-debug.apk
    set PKG_NAME=com.orbit.installer.debug
)

echo [2/3] 检查 APK 文件...
if not exist "%APK_PATH%" (
    echo 未发现 APK，正在自动编译...
    call build_installer.bat %BUILD_TYPE%
)

echo [3/3] 正在通过 ADB 推送安装至车机...
adb install -r -d -t "%APK_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo [错误] 安装失败，请检查设备提示。
    pause
    exit /b %ERRORLEVEL%
)

echo 正在启动车机安装助手...
adb shell am start -n %PKG_NAME%/com.orbit.installer.MainActivity

echo ======================================================
echo 安装成功并在车机上启动！
echo ======================================================
pause
