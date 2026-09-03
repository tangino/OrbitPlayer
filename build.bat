@echo off
setlocal enabledelayedexpansion

echo ======================================================
echo      Orbit Player - Windows Build
echo ======================================================

:: 1. Detect Android SDK
if not defined ANDROID_HOME if exist "E:\softwares\Android\sdk" set "ANDROID_HOME=E:\softwares\Android\sdk"
if not defined ANDROID_HOME if exist "C:\Program Files (x86)\Android\android-sdk" set "ANDROID_HOME=C:\Program Files (x86)\Android\android-sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

if not defined ANDROID_HOME (
    echo [ERROR] Android SDK not found. Please set ANDROID_HOME or configure local.properties.
    exit /b 1
)
echo [1/3] Android SDK: %ANDROID_HOME%

:: 2. Detect and configure Java JDK
echo [2/3] Configuring Java environment...
if not defined JAVA_HOME if exist "C:\Program Files\Android\openjdk\jdk-21.0.8" set "JAVA_HOME=C:\Program Files\Android\openjdk\jdk-21.0.8"
if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-17" set "JAVA_HOME=C:\Program Files\Java\jdk-17"
if not defined JAVA_HOME if exist "E:\softwares\Android_Studio\jbr" set "JAVA_HOME=E:\softwares\Android_Studio\jbr"

if defined JAVA_HOME (
    echo       Using JDK: %JAVA_HOME%
    set "PATH=%JAVA_HOME%\bin;%PATH%"
) else (
    echo       Using system default Java
)

:: 3. Execute Gradle build
echo.
echo [3/3] Running Gradle build...
if not exist "%~dp0gradlew.bat" (
    echo [ERROR] gradlew.bat not found in project root.
    exit /b 1
)

:: Support args like --clean
set "DO_CLEAN=0"
for %%a in (%*) do (
    if "%%a"=="--clean" set "DO_CLEAN=1"
)

if "%DO_CLEAN%"=="1" (
    echo Cleaning project...
    call "%~dp0gradlew.bat" clean
    if !ERRORLEVEL! neq 0 (
        echo [ERROR] Gradle clean failed!
        exit /b !ERRORLEVEL!
    )
)

call "%~dp0gradlew.bat" assembleDebug
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Gradle build failed!
    exit /b !ERRORLEVEL!
)

set "APK_PATH=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if exist "%APK_PATH%" (
    echo.
    echo ======================================================
    echo [SUCCESS] Build completed!
    echo    APK Path: %APK_PATH%
    echo ======================================================
    echo Hint: Run install_and_debug.bat to install and debug on device/emulator.
) else (
    echo [ERROR] Output APK file not found.
    exit /b 1
)
