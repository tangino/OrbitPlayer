@echo off
setlocal enabledelayedexpansion

echo ======================================================
echo    Orbit Player - Windows Start Emulator
echo ======================================================

:: 1. Detect Android SDK
if not defined ANDROID_HOME if exist "E:\softwares\Android\sdk" set "ANDROID_HOME=E:\softwares\Android\sdk"
if not defined ANDROID_HOME if exist "C:\Program Files (x86)\Android\android-sdk" set "ANDROID_HOME=C:\Program Files (x86)\Android\android-sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

if not defined ANDROID_HOME (
    echo [ERROR] Android SDK not found.
    exit /b 1
)

set "EMULATOR_BIN=%ANDROID_HOME%\emulator\emulator.exe"
set "ADB_BIN=%ANDROID_HOME%\platform-tools\adb.exe"

if not exist "%EMULATOR_BIN%" (
    echo [ERROR] emulator.exe not found at %EMULATOR_BIN%
    exit /b 1
)

:: 2. Check running emulator
for /f "tokens=1,2" %%A in ('"%ADB_BIN%" devices') do (
    echo %%A | findstr /R "emulator-[0-9]+" >nul
    if !ERRORLEVEL! equ 0 (
        if "%%B"=="device" (
            echo [HINT] Emulator already running (%%A).
            "%ADB_BIN%" devices
            exit /b 0
        )
    )
)

:: 3. Select AVD
set "AVD_NAME=%~1"
if not defined AVD_NAME (
    for /f "tokens=*" %%A in ('"%EMULATOR_BIN%" -list-avds') do (
        if not defined AVD_NAME set "AVD_NAME=%%A"
    )
)

if not defined AVD_NAME (
    echo [ERROR] No Android Virtual Device (AVD) found.
    echo Please create one using Android Studio Device Manager.
    exit /b 1
)

echo Starting emulator: %AVD_NAME% ...
start "Android Emulator" "%EMULATOR_BIN%" -avd "%AVD_NAME%" -netdelay none -netspeed full

echo Waiting for device to connect...
"%ADB_BIN%" wait-for-device

echo [SUCCESS] Emulator is ready!
"%ADB_BIN%" devices
