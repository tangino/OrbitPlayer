# Orbit Player - Windows PowerShell Install and Debug Script
$ErrorActionPreference = "Stop"

Write-Host "======================================================" -ForegroundColor Cyan
Write-Host "   Orbit Player - Windows Install and Debug           " -ForegroundColor Cyan
Write-Host "======================================================" -ForegroundColor Cyan

# 1. Detect Android SDK and adb
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

if ($env:ANDROID_HOME) {
    $env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
}

$adb = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adb) {
    Write-Host "[ERROR] adb command not found. Please verify Android SDK installation." -ForegroundColor Red
    exit 1
}

# 2. Check connected devices
Write-Host "`n[1/4] Checking connected Android devices..." -ForegroundColor Yellow
$deviceLines = (adb devices) | Where-Object { $_ -match "\s+device$" }

if (-not $deviceLines) {
    Write-Host "[ERROR] No authorized Android device or emulator detected." -ForegroundColor Red
    Write-Host "Please check:"
    Write-Host "  1. Device is connected via USB with USB debugging enabled."
    Write-Host "  2. Confirm 'Allow USB Debugging' on device screen."
    Write-Host "  3. Or run .\start_emulator.ps1 to start emulator."
    exit 1
}

$targetDevice = ($deviceLines[0] -split "\s+")[0]
$model = (adb -s $targetDevice shell getprop ro.product.model 2>$null)
$androidVer = (adb -s $targetDevice shell getprop ro.build.version.release 2>$null)
Write-Host "[SUCCESS] Target device: $targetDevice ($model, Android $androidVer)" -ForegroundColor Green

# 3. Check or build APK
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$apkPath = Join-Path $scriptDir "app\build\outputs\apk\debug\app-debug.apk"
Write-Host "`n[2/4] Checking APK status..." -ForegroundColor Yellow

if (-not (Test-Path $apkPath)) {
    Write-Host "Debug APK not found. Triggering build..." -ForegroundColor Yellow
    $buildScript = Join-Path $scriptDir "build.ps1"
    & $buildScript
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Build failed." -ForegroundColor Red
        exit 1
    }
} else {
    Write-Host "Found APK: $apkPath" -ForegroundColor Green
}

# 4. Install APK
Write-Host "`n[3/4] Installing APK via ADB..." -ForegroundColor Yellow
Write-Host "Prompt: If device shows permission dialog, please allow USB installation." -ForegroundColor Cyan

adb -s $targetDevice install -r -t "$apkPath"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Installation failed." -ForegroundColor Red
    exit 1
}
Write-Host "[SUCCESS] Installation finished!" -ForegroundColor Green

# 5. Launch application and stream logs
Write-Host "`n[4/4] Starting app and streaming logs..." -ForegroundColor Yellow
$packageName = "com.antigravity.equalizer"
$activityName = ".ui.MainActivity"

adb -s $targetDevice shell am start -n "$packageName/$activityName"

Write-Host "`n======================================================" -ForegroundColor Cyan
Write-Host "Application launched on device!" -ForegroundColor Green
Write-Host "Streaming DSP logs (Press Ctrl+C to terminate)..." -ForegroundColor Cyan
Write-Host "======================================================`n" -ForegroundColor Cyan

adb -s $targetDevice logcat -v color -s NativeDSP EqualizerService AudioEffectManager AudioSessionManager DeviceManager AndroidRuntime:E
