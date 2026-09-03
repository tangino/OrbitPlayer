# Orbit Player - Windows PowerShell Build Script
param (
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "     Orbit Player - Windows Build (PowerShell)        " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# 1. Detect Android SDK
if (-not $env:ANDROID_HOME) {
    $candidateSdk = @(
        "E:\softwares\Android\sdk",
        "C:\Program Files (x86)\Android\android-sdk",
        "$env:LOCALAPPDATA\Android\Sdk"
    )
    foreach ($sdk in $candidateSdk) {
        if (Test-Path $sdk) {
            $env:ANDROID_HOME = $sdk
            break
        }
    }
}

if (-not $env:ANDROID_HOME -or -not (Test-Path $env:ANDROID_HOME)) {
    Write-Host "[ERROR] Android SDK not found. Please set ANDROID_HOME." -ForegroundColor Red
    exit 1
}
Write-Host "[1/3] Android SDK: $env:ANDROID_HOME" -ForegroundColor Green

# 2. Detect Java JDK
Write-Host "[2/3] Configuring Java environment..." -ForegroundColor Yellow
if (-not $env:JAVA_HOME) {
    $candidateJdks = @(
        "C:\Program Files\Android\openjdk\jdk-21.0.8",
        "C:\Program Files\Java\jdk-17",
        "E:\softwares\Android_Studio\jbr"
    )
    foreach ($jdk in $candidateJdks) {
        if (Test-Path $jdk) {
            $env:JAVA_HOME = $jdk
            break
        }
    }
}

if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Write-Host "      Using JDK: $env:JAVA_HOME" -ForegroundColor Green
} else {
    Write-Host "      Using default system Java" -ForegroundColor Gray
}

# 3. Run Gradle Wrapper
Write-Host "`n[3/3] Running Gradle build..." -ForegroundColor Yellow
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradlewBat = Join-Path $scriptDir "gradlew.bat"

if (-not (Test-Path $gradlewBat)) {
    Write-Host "[ERROR] gradlew.bat not found in project root." -ForegroundColor Red
    exit 1
}

if ($Clean) {
    Write-Host "Cleaning build outputs..." -ForegroundColor Yellow
    & $gradlewBat clean
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Gradle clean failed." -ForegroundColor Red
        exit $LASTEXITCODE
    }
}

& $gradlewBat assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Gradle build failed." -ForegroundColor Red
    exit $LASTEXITCODE
}

$apkPath = Join-Path $scriptDir "app\build\outputs\apk\debug\app-debug.apk"

if (Test-Path $apkPath) {
    $apkItem = Get-Item $apkPath
    $sizeMb = [math]::Round($apkItem.Length / 1MB, 2)
    Write-Host "`n======================================================" -ForegroundColor Cyan
    Write-Host "[SUCCESS] Build completed!" -ForegroundColor Green
    Write-Host "   APK Path: $apkPath" -ForegroundColor Green
    Write-Host "   APK Size: $sizeMb MB ($($apkItem.Length) bytes)" -ForegroundColor Green
    Write-Host "======================================================" -ForegroundColor Cyan
    Write-Host "`nHint: Run .\install_and_debug.ps1 to install and start debugging." -ForegroundColor Yellow
} else {
    Write-Host "[ERROR] Output APK file not found." -ForegroundColor Red
    exit 1
}
