@echo off
setlocal enabledelayedexpansion

echo ======================================================
echo    Orbit Player - Android Screenshot (Windows)
echo ======================================================

:: 1. Detect Android SDK and adb
if not defined ANDROID_HOME if exist "E:\softwares\Android\sdk" set "ANDROID_HOME=E:\softwares\Android\sdk"
if not defined ANDROID_HOME if exist "C:\Program Files (x86)\Android\android-sdk" set "ANDROID_HOME=C:\Program Files (x86)\Android\android-sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

if defined ANDROID_HOME (
    set "PATH=%ANDROID_HOME%\platform-tools;%PATH%"
)

where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] adb command not found. Please check Android SDK installation.
    exit /b 1
)

:: 2. Check connected devices
echo [1/3] Checking connected Android devices...
set "DEVICE_FOUND="
for /f "tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" (
        set "DEVICE_FOUND=%%A"
    )
)

if not defined DEVICE_FOUND (
    echo [ERROR] No authorized Android device detected.
    echo Please make sure USB debugging is enabled on your phone.
    exit /b 1
)
echo [SUCCESS] Target device identified: %DEVICE_FOUND%

:: 3. Generate timestamp
set "TIMESTAMP="
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"`) do (
    set "TIMESTAMP=%%A"
)

if not defined TIMESTAMP (
    set "TIMESTAMP=%RANDOM%"
)

set "OUTPUT_FILE=%~dp0screenshot_%TIMESTAMP%.png"
set "DEVICE_TEMP=/sdcard/screenshot_temp.png"

echo.
echo [2/3] Capturing screen on device...
adb shell screencap -p "%DEVICE_TEMP%"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to take screenshot on device.
    exit /b 1
)

echo [3/3] Pulling screenshot to local PC...
adb pull "%DEVICE_TEMP%" "%OUTPUT_FILE%" >nul 2>&1
set "PULL_RESULT=%ERRORLEVEL%"
adb shell rm -f "%DEVICE_TEMP%" >nul 2>&1

if %PULL_RESULT% equ 0 if exist "%OUTPUT_FILE%" (
    echo.
    echo ======================================================
    echo [SUCCESS] Screenshot saved successfully!
    echo Path: %OUTPUT_FILE%
    echo ======================================================
) else (
    echo [ERROR] Failed to pull screenshot from device.
    exit /b 1
)

endlocal
