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
echo.
echo [Step 1/3] Checking connected Android devices...
set "DEVICE_COUNT=0"
for /f "tokens=1,2" %%A in ('adb devices') do (
    if "%%B"=="device" (
        set /a DEVICE_COUNT+=1
        set "DEV_!DEVICE_COUNT!=%%A"
    )
)

if !DEVICE_COUNT! equ 0 (
    echo [ERROR] No authorized Android device detected.
    echo Please make sure USB debugging is enabled on your phone or emulator.
    exit /b 1
)

set "SELECTED_DEVICES="
if !DEVICE_COUNT! equ 1 (
    set "SELECTED_DEVICES=!DEV_1!"
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
    echo   [A] Capture ALL devices
    echo   [Q] Quit
    echo ------------------------------------------------------
    
    :CHOOSE_SCREENSHOT_DEVICE
    set "USER_CHOICE="
    set /p "USER_CHOICE=Please select device [1-!DEVICE_COUNT! / A / Q] (Default: 1): "
    if not defined USER_CHOICE set "USER_CHOICE=1"
    
    if /i "!USER_CHOICE!"=="Q" (
        echo [INFO] Screenshot cancelled by user.
        exit /b 0
    )
    
    if /i "!USER_CHOICE!"=="A" (
        for /l %%i in (1,1,!DEVICE_COUNT!) do (
            if not defined SELECTED_DEVICES (
                set "SELECTED_DEVICES=!DEV_%%i!"
            ) else (
                set "SELECTED_DEVICES=!SELECTED_DEVICES! !DEV_%%i!"
            )
        )
    ) else (
        set "VALID_NUM=0"
        for /l %%i in (1,1,!DEVICE_COUNT!) do (
            if "!USER_CHOICE!"=="%%i" (
                set "VALID_NUM=1"
                set "SELECTED_DEVICES=!DEV_%%i!"
            )
        )
        if !VALID_NUM! equ 0 (
            echo [WARNING] Invalid selection. Please choose again.
            goto CHOOSE_SCREENSHOT_DEVICE
        )
    )
)

:: 3. Generate timestamp
set "TIMESTAMP="
for /f "usebackq delims=" %%A in (`powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"`) do (
    set "TIMESTAMP=%%A"
)
if not defined TIMESTAMP (
    set "TIMESTAMP=%RANDOM%"
)

echo.
echo [Step 2/3] Capturing screen and pulling screenshots...

set "DEVICE_TEMP=/sdcard/screenshot_temp_%RANDOM%.png"
set "SUCCESS_COUNT=0"

for %%D in (!SELECTED_DEVICES!) do (
    set "DEV_ID=%%D"
    set "SAFE_NAME=!DEV_ID::=_!"
    set "SAFE_NAME=!SAFE_NAME:.=_!"
    
    if !DEVICE_COUNT! equ 1 (
        set "OUTPUT_FILE=%~dp0screenshot_!TIMESTAMP!.png"
    ) else (
        set "OUTPUT_FILE=%~dp0screenshot_!SAFE_NAME!_!TIMESTAMP!.png"
    )
    
    echo   - Capturing screen on [!DEV_ID!]...
    adb -s !DEV_ID! shell screencap -p "!DEVICE_TEMP!" >nul 2>&1
    if !ERRORLEVEL! equ 0 (
        adb -s !DEV_ID! pull "!DEVICE_TEMP!" "!OUTPUT_FILE!" >nul 2>&1
        if !ERRORLEVEL! equ 0 (
            if exist "!OUTPUT_FILE!" (
                echo     [OK] Saved to: !OUTPUT_FILE!
                set /a SUCCESS_COUNT+=1
            )
        ) else (
            echo     [ERROR] Failed to pull screenshot from !DEV_ID!
        )
        adb -s !DEV_ID! shell rm -f "!DEVICE_TEMP!" >nul 2>&1
    ) else (
        echo     [ERROR] Failed to execute screencap on !DEV_ID!
    )
)

echo.
echo [Step 3/3] Done!
if !SUCCESS_COUNT! gtr 0 (
    echo ======================================================
    echo [SUCCESS] !SUCCESS_COUNT! screenshot saved successfully!
    echo ======================================================
) else (
    echo [ERROR] No screenshots were saved.
    exit /b 1
)

endlocal
