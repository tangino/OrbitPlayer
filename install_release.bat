@echo off
setlocal enabledelayedexpansion

echo ======================================================
echo    Orbit Player - Windows Install Release (v0.1.1)
echo ======================================================

if not defined ANDROID_HOME if exist "E:\softwares\Android\sdk" set "ANDROID_HOME=E:\softwares\Android\sdk"
if not defined ANDROID_HOME if exist "C:\Program Files (x86)\Android\android-sdk" set "ANDROID_HOME=C:\Program Files (x86)\Android\android-sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

if defined ANDROID_HOME (
    set "PATH=%ANDROID_HOME%\platform-tools;%ANDROID_HOME%\emulator;%PATH%"
)

where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] adb command not found. Please verify Android SDK installation.
    exit /b 1
)

echo.
echo [1/3] Checking connected Android devices...
set "DEVICE_FOUND="
for /f "tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" (
        set "DEVICE_FOUND=%%A"
    )
)

if not defined DEVICE_FOUND (
    echo [ERROR] No authorized Android device or emulator detected.
    echo Please check:
    echo   1. Device connected via USB with USB debugging enabled.
    echo   2. Or run start_emulator.bat to launch emulator first.
    exit /b 1
)

echo [SUCCESS] Target device identified: %DEVICE_FOUND%

set "APK_PATH=%~dp0app\build\outputs\apk\release\OrbitPlayer.apk"
if not exist "%APK_PATH%" set "APK_PATH=%~dp0OrbitPlayer.apk"
if not exist "%APK_PATH%" set "APK_PATH=%~dp0app\build\outputs\apk\release\app-release.apk"

echo.
echo [2/3] Checking release APK file...
if not exist "%APK_PATH%" (
    echo [INFO] Release APK not found. Running build_release.bat...
    call "%~dp0build_release.bat"
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Release build failed.
        exit /b !ERRORLEVEL!
    )
    set "APK_PATH=%~dp0app\build\outputs\apk\release\OrbitPlayer.apk"
    if not exist "%APK_PATH%" set "APK_PATH=%~dp0OrbitPlayer.apk"
) else (
    echo Found release APK: %APK_PATH%
)

echo.
echo [3/3] Installing release APK via ADB (%DEVICE_FOUND%)...
adb -s %DEVICE_FOUND% install -r -d "%APK_PATH%"
if !ERRORLEVEL! neq 0 (
    echo [INFO] Direct install failed, attempting push and pm install fallback...
    adb -s %DEVICE_FOUND% push "%APK_PATH%" /data/local/tmp/OrbitPlayer.apk
    adb -s %DEVICE_FOUND% shell pm install -r -d /data/local/tmp/OrbitPlayer.apk
    adb -s %DEVICE_FOUND% shell rm /data/local/tmp/OrbitPlayer.apk
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Installation failed.
        exit /b !ERRORLEVEL!
    )
)

echo.
echo ======================================================
echo [SUCCESS] OrbitPlayer release APK installed successfully!
echo ======================================================

set "PACKAGE_NAME=com.antigravity.equalizer"
set "ACTIVITY_NAME=.ui.MainActivity"
echo.
echo Launching application...
adb -s %DEVICE_FOUND% shell am start -n "%PACKAGE_NAME%/%ACTIVITY_NAME%" >nul 2>&1
echo [DONE] Application ready.