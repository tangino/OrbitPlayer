@echo off
setlocal enabledelayedexpansion

echo ======================================================
echo      Orbit Player - Windows Release Build (v0.1.2)
echo ======================================================

if not defined ANDROID_HOME if exist "E:\softwares\Android\sdk" set "ANDROID_HOME=E:\softwares\Android\sdk"
if not defined ANDROID_HOME if exist "C:\Program Files (x86)\Android\android-sdk" set "ANDROID_HOME=C:\Program Files (x86)\Android\android-sdk"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"

if not defined ANDROID_HOME (
    echo [ERROR] Android SDK not found. Please set ANDROID_HOME or configure local.properties.
    exit /b 1
)
echo [1/3] Android SDK: %ANDROID_HOME%

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

echo.
echo [3/3] Running Gradle assembleRelease...
if not exist "%~dp0gradlew.bat" (
    echo [ERROR] gradlew.bat not found in project root.
    exit /b 1
)

call "%~dp0gradlew.bat" assembleRelease
if !ERRORLEVEL! neq 0 (
    echo [ERROR] Gradle release build failed!
    exit /b !ERRORLEVEL!
)

set "APK_PATH=%~dp0app\build\outputs\apk\release\OrbitPlayer.apk"
if not exist "%APK_PATH%" set "APK_PATH=%~dp0app\build\outputs\apk\release\app-release.apk"

if exist "%APK_PATH%" (
    copy /y "%APK_PATH%" "%~dp0app\build\outputs\apk\release\OrbitPlayer.apk" >nul 2>&1
    copy /y "%APK_PATH%" "%~dp0OrbitPlayer.apk" >nul 2>&1
    echo.
    echo ======================================================
    echo [SUCCESS] Release Build completed!
    echo    APK Output: %~dp0app\build\outputs\apk\release\OrbitPlayer.apk
    echo    Root Copy:  %~dp0OrbitPlayer.apk
    echo ======================================================
    echo Hint: Run install_release.bat to install on device/emulator.
) else (
    echo [ERROR] Output release APK file not found.
    exit /b 1
)