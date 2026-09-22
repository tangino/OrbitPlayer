@echo off
chcp 65001 >nul 2>&1
setlocal enabledelayedexpansion

echo ======================================================
echo    Orbit Player - Android 虚拟机启动器
echo ======================================================

:: 1. 检测 Android SDK 路径
if not defined ANDROID_HOME (
    if exist "local.properties" (
        for /f "usebackq tokens=1,* delims==" %%A in ("local.properties") do (
            if "%%A"=="sdk.dir" (
                set "RAW_SDK=%%B"
                set "RAW_SDK=!RAW_SDK:\:=:!"
                set "RAW_SDK=!RAW_SDK:\\=\!"
                set "ANDROID_HOME=!RAW_SDK!"
            )
        )
    )
)
if not defined ANDROID_HOME if exist "E:\softwares\Android\sdk" set "ANDROID_HOME=E:\softwares\Android\sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME if exist "C:\Program Files (x86)\Android\android-sdk" set "ANDROID_HOME=C:\Program Files (x86)\Android\android-sdk"

if not defined ANDROID_HOME (
    echo [错误] 未检测到 Android SDK 路径，请在 local.properties 中配置 sdk.dir 或设置 ANDROID_HOME 环境变量。
    exit /b 1
)

set "EMULATOR_BIN=%ANDROID_HOME%\emulator\emulator.exe"
set "ADB_BIN=%ANDROID_HOME%\platform-tools\adb.exe"

if not exist "%EMULATOR_BIN%" (
    echo [错误] 未在以下路径找到 emulator.exe: %EMULATOR_BIN%
    exit /b 1
)

:: 2. 获取所有可用的 AVD 列表
set "AVD_COUNT=0"
for /f "usebackq tokens=*" %%A in (`"%EMULATOR_BIN%" -list-avds 2^>nul`) do (
    if not "%%A"=="" (
        set /a AVD_COUNT+=1
        set "AVD_!AVD_COUNT!=%%A"
    )
)

if %AVD_COUNT% equ 0 (
    echo [错误] 未检测到任何 Android 虚拟机 AVD。
    echo 请先在 Android Studio 中打开 Device Manager 创建虚拟机。
    exit /b 1
)

:: 3. 判断用户是否通过命令行参数指定了虚拟机 (名称或数字序号)
set "USER_ARG=%~1"
set "SELECTED_AVD="

if defined USER_ARG (
    :: 检查是否输入的是数字序号
    set "IS_NUM=1"
    for /f "delims=0123456789" %%I in ("%USER_ARG%") do set "IS_NUM=0"
    if "!IS_NUM!"=="1" (
        for /l %%I in (1,1,%AVD_COUNT%) do (
            if "%USER_ARG%"=="%%I" (
                set "SELECTED_AVD=!AVD_%%I!"
            )
        )
    )
    :: 如果不是有效序号，检查是否是完整 AVD 名称
    if not defined SELECTED_AVD (
        for /l %%I in (1,1,%AVD_COUNT%) do (
            if /i "!AVD_%%I!"=="%USER_ARG%" (
                set "SELECTED_AVD=!AVD_%%I!"
            )
        )
    )
    if not defined SELECTED_AVD (
        echo [提示] 传入的参数 "%USER_ARG%" 未匹配到有效虚拟机，将进入交互选择。
    )
)

:: 4. 如果未通过参数指定，提供交互式选择
if not defined SELECTED_AVD (
    if %AVD_COUNT% equ 1 (
        set "SELECTED_AVD=!AVD_1!"
        echo 检测到唯一虚拟机: !SELECTED_AVD!，自动选择启动。
    ) else (
        echo 检测到以下可用的 Android 虚拟机:
        echo ------------------------------------------------------
        for /l %%I in (1,1,%AVD_COUNT%) do (
            echo   [%%I] !AVD_%%I!
        )
        echo ------------------------------------------------------
        set /p "CHOICE=请输入虚拟机序号 [1-%AVD_COUNT%] (默认回车: 1): "
        if "!CHOICE!"=="" set "CHOICE=1"
        for /l %%I in (1,1,%AVD_COUNT%) do (
            if "!CHOICE!"=="%%I" (
                set "SELECTED_AVD=!AVD_%%I!"
            )
        )
        if not defined SELECTED_AVD (
            echo [提示] 输入无效，默认选择 [1] !AVD_1!
            set "SELECTED_AVD=!AVD_1!"
        )
    )
)

echo.
echo [1/3] 正在准备启动虚拟机: !SELECTED_AVD! ...

:: 5. 检查是否已有运行中的模拟器
set "RUNNING_EMU_COUNT=0"
for /f "tokens=1,2" %%A in ('"%ADB_BIN%" devices 2^>nul') do (
    if "%%B"=="device" (
        echo %%A | findstr /R "^emulator-" >nul 2>&1
        if not errorlevel 1 (
            set /a RUNNING_EMU_COUNT+=1
            set "RUNNING_EMU_!RUNNING_EMU_COUNT!=%%A"
        )
    )
)

if %RUNNING_EMU_COUNT% gtr 0 (
    echo [INFO] Currently running emulators: %RUNNING_EMU_COUNT%
    for /l %%i in (1,1,%RUNNING_EMU_COUNT%) do (
        echo   - !RUNNING_EMU_%%i!
    )
    echo Starting additional emulator [!SELECTED_AVD!] ...
)

:: 6. 后台启动虚拟机
echo [2/3] Launching emulator: !SELECTED_AVD! ...
start "Android Emulator - !SELECTED_AVD!" "%EMULATOR_BIN%" -avd "!SELECTED_AVD!" -netdelay none -netspeed full

:: 7. 等待启动
echo [3/3] Emulator process launched in background.
echo Loading emulator system, please wait...

echo.
echo ======================================================
echo [SUCCESS] Launched emulator: !SELECTED_AVD!
echo Hint: Ready to use once emulator finishes booting.
echo ======================================================
echo Connected devices:
"%ADB_BIN%" devices
echo.
echo Hint: Run .\install_and_debug.bat to install and debug.
echo Hint: Run .\install_release.bat to install release APK.

