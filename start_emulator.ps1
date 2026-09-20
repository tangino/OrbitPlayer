# Orbit Player - Windows PowerShell Start Emulator Script
param (
    [string]$Target
)

$ErrorActionPreference = "Continue"

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   Orbit Player - Android Start Emulator              " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# 1. Detect Android SDK
if (-not $env:ANDROID_HOME) {
    if (Test-Path "local.properties") {
        $props = Get-Content "local.properties"
        foreach ($line in $props) {
            if ($line -match "^sdk\.dir=(.+)$") {
                $raw = $matches[1].Replace("\\", "\").Replace("\:", ":")
                if (Test-Path $raw) {
                    $env:ANDROID_HOME = $raw
                    break
                }
            }
        }
    }
}

if (-not $env:ANDROID_HOME) {
    $candidateSdk = @(
        "E:\softwares\Android\sdk",
        "$env:LOCALAPPDATA\Android\Sdk",
        "C:\Program Files (x86)\Android\android-sdk"
    )
    foreach ($sdk in $candidateSdk) {
        if (Test-Path $sdk) {
            $env:ANDROID_HOME = $sdk
            break
        }
    }
}

if (-not $env:ANDROID_HOME) {
    Write-Host "[ERROR] Android SDK not found. Please set ANDROID_HOME or configure local.properties." -ForegroundColor Red
    exit 1
}

$emulatorBin = Join-Path $env:ANDROID_HOME "emulator\emulator.exe"
$adbBin = Join-Path $env:ANDROID_HOME "platform-tools\adb.exe"

if (-not (Test-Path $emulatorBin)) {
    Write-Host "[ERROR] emulator.exe not found at: $emulatorBin" -ForegroundColor Red
    exit 1
}

# 2. Get available AVD list
$rawAvdList = (& $emulatorBin -list-avds 2>$null) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
$avdList = @($rawAvdList)

if ($avdList.Count -eq 0) {
    Write-Host "[ERROR] No Android Virtual Device (AVD) found." -ForegroundColor Red
    Write-Host "Please create one using Android Studio Device Manager." -ForegroundColor Yellow
    exit 1
}

# 3. Parse user target AVD
$selectedAvd = $null

if ($Target) {
    if ($Target -match "^\d+$") {
        $index = [int]$Target
        if ($index -ge 1 -and $index -le $avdList.Count) {
            $selectedAvd = $avdList[$index - 1]
        }
    }
    if (-not $selectedAvd) {
        $matched = $avdList | Where-Object { $_ -eq $Target -or $_ -like "*$Target*" }
        if ($matched) {
            $selectedAvd = $matched[0]
        }
    }
    if (-not $selectedAvd) {
        Write-Host "[HINT] Target '$Target' did not match any AVD. Entering interactive selection." -ForegroundColor Yellow
    }
}

# 4. Interactive selection
if (-not $selectedAvd) {
    if ($avdList.Count -eq 1) {
        $selectedAvd = $avdList[0]
        Write-Host "Auto-selecting the only available AVD: $selectedAvd" -ForegroundColor Green
    } else {
        Write-Host "Available Android Virtual Devices (AVDs):" -ForegroundColor Cyan
        Write-Host "------------------------------------------------------" -ForegroundColor DarkGray
        for ($i = 0; $i -lt $avdList.Count; $i++) {
            $num = $i + 1
            Write-Host "  [$num] $($avdList[$i])" -ForegroundColor Yellow
        }
        Write-Host "------------------------------------------------------" -ForegroundColor DarkGray

        $choice = Read-Host "Enter AVD index [1-$($avdList.Count)] (Default: 1)"
        if ([string]::IsNullOrWhiteSpace($choice)) {
            $choice = "1"
        }
        if ($choice -match "^\d+$") {
            $cIdx = [int]$choice
            if ($cIdx -ge 1 -and $cIdx -le $avdList.Count) {
                $selectedAvd = $avdList[$cIdx - 1]
            }
        }
        if (-not $selectedAvd) {
            Write-Host "[HINT] Invalid input. Defaulting to [1] $($avdList[0])" -ForegroundColor DarkYellow
            $selectedAvd = $avdList[0]
        }
    }
}

Write-Host ""
Write-Host "[1/3] Preparing to start emulator: $selectedAvd ..." -ForegroundColor Cyan

# 5. Check if emulator already running
$running = (& $adbBin devices 2>$null) | Where-Object { $_ -match "emulator-\d+\s+device" }
if ($running) {
    Write-Host "[HINT] Emulator is already running: $running" -ForegroundColor Yellow
    & $adbBin devices
    Write-Host "You can run .\install_and_debug.bat directly." -ForegroundColor Cyan
    exit 0
}

# 6. Start emulator in background
Write-Host "[2/3] Starting emulator $selectedAvd ..." -ForegroundColor Yellow
Start-Process -FilePath $emulatorBin -ArgumentList "-avd", $selectedAvd, "-netdelay", "none", "-netspeed", "full"

# 7. Wait for device
Write-Host "[3/3] Waiting for ADB device to connect..." -ForegroundColor Yellow
& $adbBin wait-for-device

$model = (& $adbBin shell getprop ro.product.model 2>$null)
$ver = (& $adbBin shell getprop ro.build.version.release 2>$null)

Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "[SUCCESS] Emulator is ready!" -ForegroundColor Green
Write-Host "  - AVD Name:   $selectedAvd" -ForegroundColor Green
if ($model) { Write-Host "  - Model:      $model" -ForegroundColor Green }
if ($ver) { Write-Host "  - Android:    Android $ver" -ForegroundColor Green }
Write-Host "======================================================" -ForegroundColor Green
& $adbBin devices
