# Orbit Player - Windows PowerShell Start Emulator Script
param (
    [string]$AvdName
)

$ErrorActionPreference = "Stop"

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   Orbit Player - Windows Start Emulator              " -ForegroundColor Cyan
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

if (-not $env:ANDROID_HOME) {
    Write-Host "[ERROR] Android SDK not found. Please configure ANDROID_HOME." -ForegroundColor Red
    exit 1
}

$emulatorBin = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
$adbBin = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"

if (-not (Test-Path $emulatorBin)) {
    Write-Host "[ERROR] emulator.exe not found at: $emulatorBin" -ForegroundColor Red
    exit 1
}

# 2. Check running emulator
$running = (& $adbBin devices 2>$null) | Where-Object { $_ -match "emulator-\d+\s+device" }
if ($running) {
    Write-Host "[HINT] Emulator is already running." -ForegroundColor Green
    & $adbBin devices
    exit 0
}

# 3. Select AVD
if (-not $AvdName) {
    $avdList = (& $emulatorBin -list-avds 2>$null)
    if ($avdList) {
        $AvdName = $avdList[0]
    }
}

if (-not $AvdName) {
    Write-Host "[ERROR] No Android Virtual Device (AVD) detected." -ForegroundColor Red
    Write-Host "Please create one using Android Studio Device Manager." -ForegroundColor Yellow
    exit 1
}

Write-Host "Starting emulator: $AvdName ..." -ForegroundColor Yellow
Start-Process -FilePath $emulatorBin -ArgumentList "-avd", $AvdName, "-netdelay", "none", "-netspeed", "full"

Write-Host "Waiting for device to connect..." -ForegroundColor Yellow
& $adbBin wait-for-device

$model = (& $adbBin shell getprop ro.product.model 2>$null)
$ver = (& $adbBin shell getprop ro.build.version.release 2>$null)

Write-Host "Emulator is ready!" -ForegroundColor Green
Write-Host "   - Model: $model" -ForegroundColor Green
Write-Host "   - Android version: $ver" -ForegroundColor Green
& $adbBin devices
