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
set "DEVICE_COUNT=0"
for /f "tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEV_!DEVICE_COUNT!=%%A"
    )
)

if !DEVICE_COUNT! equ 0 (
    echo [ERROR] No authorized Android device or emulator detected.
    echo Please check:
    echo   1. Device connected via USB with USB debugging enabled.
    echo   2. Or run start_emulator.bat to launch emulator first.
    exit /b 1
)

set "TARGET_DEVICES="
set "PRIMARY_LOG_DEV="
if !DEVICE_COUNT! equ 1 (
    set "TARGET_DEVICES=!DEV_1!"
    set "PRIMARY_LOG_DEV=!DEV_1!"
    set "DEV_MODEL="
    set "DEV_VER="
    for /f "delims=" %%M in ('adb -s !DEV_1! shell getprop ro.product.model 2^>nul') do set "DEV_MODEL=%%M"
    for /f "delims=" %%V in ('adb -s !DEV_1! shell getprop ro.build.version.release 2^>nul') do set "DEV_VER=%%V"
    if not defined DEV_MODEL set "DEV_MODEL=Android Device"
    if not defined DEV_VER set "DEV_VER=Unknown"
    echo [SUCCESS] Target device identified: !DEV_1! [!DEV_MODEL!, Android !DEV_VER!]
) else (
    echo.
    echo Detected !DEVICE_COUNT! connected Android devices:
    echo ------------------------------------------------------
    for /l %%i in (1,1,!DEVICE_COUNT!) do (
        set "CUR_DEV=!DEV_%%i!"
        set "CUR_MODEL="
        set "CUR_VER="
        for /f "delims=" %%M in ('adb -s !CUR_DEV! shell getprop ro.product.model 2^>nul') do set "CUR_MODEL=%%M"
        for /f "delims=" %%V in ('adb -s !CUR_DEV! shell getprop ro.build.version.release 2^>nul') do set "CUR_VER=%%V"
        if not defined CUR_MODEL set "CUR_MODEL=Android Device"
        if not defined CUR_VER set "CUR_VER=Unknown"
        echo   [%%i] !CUR_DEV! [!CUR_MODEL!, Android !CUR_VER!]
    )
    echo   [A] Install to ALL devices
    echo   [Q] Quit
    echo ------------------------------------------------------
    
    :CHOOSE_DEBUG_DEVICE
    set "USER_CHOICE="
    set /p "USER_CHOICE=Please select target [1-!DEVICE_COUNT! / A / Q] (Default: A): "
    if not defined USER_CHOICE set "USER_CHOICE=A"
    
    if /i "!USER_CHOICE!"=="Q" (
        echo [INFO] Installation aborted by user.
        exit /b 0
    )
    
    if /i "!USER_CHOICE!"=="A" (
        set "PRIMARY_LOG_DEV=!DEV_1!"
        for /l %%i in (1,1,!DEVICE_COUNT!) do (
            if not defined TARGET_DEVICES (
                set "TARGET_DEVICES=!DEV_%%i!"
            ) else (
                set "TARGET_DEVICES=!TARGET_DEVICES! !DEV_%%i!"
            )
        )
    ) else (
        set "VALID_NUM=0"
        for /l %%i in (1,1,!DEVICE_COUNT!) do (
            if "!USER_CHOICE!"=="%%i" (
                set "VALID_NUM=1"
                set "TARGET_DEVICES=!DEV_%%i!"
                set "PRIMARY_LOG_DEV=!DEV_%%i!"
            )
        )
        if !VALID_NUM! equ 0 (
            echo [WARNING] Invalid selection. Please choose again.
            goto CHOOSE_DEBUG_DEVICE
        )
    )
)

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
echo [3/4] Installing debug APK to target device(s)...
set "PACKAGE_NAME=com.orbit.music"
set "ACTIVITY_NAME=.ui.MainActivity"
set "SUCCESS_COUNT=0"
set "FAIL_COUNT=0"

for %%D in (!TARGET_DEVICES!) do (
    echo.
    echo ------------------------------------------------------
    echo [*] Installing to: %%D ...
    adb -s %%D install -r -d -t "%APK_PATH%"
    if !ERRORLEVEL! neq 0 (
        echo [INFO] Direct install failed, attempting push and pm install fallback...
        adb -s %%D push "%APK_PATH%" /data/local/tmp/orbit_debug.apk >nul 2>&1
        adb -s %%D shell pm install -r -d -t /data/local/tmp/orbit_debug.apk
        adb -s %%D shell rm /data/local/tmp/orbit_debug.apk >nul 2>&1
    )
    if !ERRORLEVEL! equ 0 (
        echo [SUCCESS] %%D: Installation succeeded.
        echo Launching application on %%D...
        adb -s %%D shell am start -n "%PACKAGE_NAME%/%ACTIVITY_NAME%" >nul 2>&1
        set /a SUCCESS_COUNT+=1
    ) else (
        echo [ERROR] %%D: Installation failed.
        set /a FAIL_COUNT+=1
    )
)

echo.
echo ======================================================
echo [RESULT] Installation Finished - Success: !SUCCESS_COUNT!, Failed: !FAIL_COUNT!
echo ======================================================

:: 5. Start logcat on primary device
echo.
echo [4/4] Streaming DSP logs from !PRIMARY_LOG_DEV! (Press Ctrl+C to stop)...
echo ======================================================
echo.

adb -s !PRIMARY_LOG_DEV! logcat -v color -s NativeDSP EqualizerService AudioEffectManager AudioSessionManager DeviceManager AndroidRuntime:E
