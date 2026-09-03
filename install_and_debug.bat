@echo off
setlocal enabledelayedexpansion

echo ======================================================
echo    Orbit Player - Windows Install and Debug
echo ======================================================

:: 1. Detect Android SDK and adb
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

:: 2. Check connected devices
echo.
echo [1/4] Checking connected Android devices...
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

:: 3. Check or build APK
set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"
echo.
echo [2/4] Checking APK status...

if not exist "%APK_PATH%" (
    echo APK not found. Building debug APK...
    call "%~dp0build.bat"
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Build failed.
        exit /b !ERRORLEVEL!
    )
) else (
    echo Found existing APK: %APK_PATH%
)

:: 4. Install APK
echo.
echo [3/4] Installing APK via ADB...
adb -s %DEVICE_FOUND% install -r -t "%APK_PATH%"
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Installation failed.
    exit /b !ERRORLEVEL!
)
echo [SUCCESS] App installed successfully!

:: 5. Launch App and start logcat
echo.
echo [4/4] Launching application and streaming DSP logs...
set "PACKAGE_NAME=com.antigravity.equalizer"
set "ACTIVITY_NAME=.ui.MainActivity"

adb -s %DEVICE_FOUND% shell am start -n "%PACKAGE_NAME%/%ACTIVITY_NAME%"

echo.
echo ======================================================
echo Application started!
echo Streaming NativeDSP and EqualizerService logs (Press Ctrl+C to stop)...
echo ======================================================
echo.

adb -s %DEVICE_FOUND% logcat -v color -s NativeDSP EqualizerService AudioEffectManager AudioSessionManager DeviceManager AndroidRuntime:E
