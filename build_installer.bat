@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ======================================================
echo      Orbit Installer (车机安装助手) - 编译构建
echo ======================================================

set BUILD_TYPE=debug
set CLEAN_FLAG=0

:parse_args
if "%~1"=="" goto after_args
if /i "%~1"=="release" set BUILD_TYPE=release
if /i "%~1"=="-r" set BUILD_TYPE=release
if /i "%~1"=="--release" set BUILD_TYPE=release
if /i "%~1"=="debug" set BUILD_TYPE=debug
if /i "%~1"=="-d" set BUILD_TYPE=debug
if /i "%~1"=="--debug" set BUILD_TYPE=debug
if /i "%~1"=="clean" set CLEAN_FLAG=1
if /i "%~1"=="-c" set CLEAN_FLAG=1
if /i "%~1"=="--clean" set CLEAN_FLAG=1
shift
goto parse_args
:after_args

echo [目标模式] %BUILD_TYPE%

if "%CLEAN_FLAG%"=="1" (
    echo [1/2] 正在清理工程...
    call gradlew.bat :installer:clean
)

echo [2/2] 正在构建 %BUILD_TYPE% 版本...
if /i "%BUILD_TYPE%"=="release" (
    call gradlew.bat :installer:assembleRelease
    set OUTPUT_DIR=installer\build\outputs\apk\release
) else (
    call gradlew.bat :installer:assembleDebug
    set OUTPUT_DIR=installer\build\outputs\apk\debug
)

if %ERRORLEVEL% NEQ 0 (
    echo [错误] 编译失败，请检查上方日志。
    exit /b %ERRORLEVEL%
)

echo.
echo ======================================================
echo 编译成功！
echo 输出目录: %OUTPUT_DIR%
echo ======================================================
pause
