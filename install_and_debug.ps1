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

$deviceList = @()
foreach ($line in $deviceLines) {
    $devId = ($line -split "\s+")[0]
    $devModel = (adb -s $devId shell getprop ro.product.model 2>$null)
    if (-not $devModel) { $devModel = "Android Device" }
    $devVer = (adb -s $devId shell getprop ro.build.version.release 2>$null)
    if (-not $devVer) { $devVer = "Unknown" }
    $deviceList += [PSCustomObject]@{ Id = $devId; Model = $devModel; Version = $devVer }
}

$targetDevices = @()
$primaryLogDev = $deviceList[0].Id

if ($deviceList.Count -eq 1) {
    $d = $deviceList[0]
    Write-Host "[SUCCESS] Target device: $($d.Id) ($($d.Model), Android $($d.Version))" -ForegroundColor Green
    $targetDevices = @($d.Id)
} else {
    Write-Host "`nDetected $($deviceList.Count) connected Android devices:" -ForegroundColor Cyan
    Write-Host "------------------------------------------------------" -ForegroundColor Cyan
    for ($i = 0; $i -lt $deviceList.Count; $i++) {
        $d = $deviceList[$i]
        Write-Host ("  [{0}] {1} ({2}, Android {3})" -f ($i + 1), $d.Id, $d.Model, $d.Version)
    }
    Write-Host "  [A] Install to ALL devices"
    Write-Host "  [Q] Quit"
    Write-Host "------------------------------------------------------" -ForegroundColor Cyan

    while ($true) {
        $choice = Read-Host "Please select target [1-$($deviceList.Count) / A / Q] (Default: A)"
        if ([string]::IsNullOrWhiteSpace($choice)) { $choice = "A" }

        if ($choice -match "^[Qq]$") {
            Write-Host "[INFO] Aborted by user." -ForegroundColor Yellow
            exit 0
        } elseif ($choice -match "^[Aa]$") {
            $targetDevices = $deviceList | ForEach-Object { $_.Id }
            break
        } elseif ($choice -match "^\d+$" -and [int]$choice -ge 1 -and [int]$choice -le $deviceList.Count) {
            $idx = [int]$choice - 1
            $targetDevices = @($deviceList[$idx].Id)
            $primaryLogDev = $deviceList[$idx].Id
            break
        } else {
            Write-Host "[WARNING] Invalid selection. Please try again." -ForegroundColor Yellow
        }
    }
}

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
$packageName = "com.orbit.music"
$activityName = ".ui.MainActivity"
$successCount = 0
$failCount = 0

foreach ($dev in $targetDevices) {
    Write-Host "`n------------------------------------------------------" -ForegroundColor Cyan
    Write-Host "[*] Installing to: $dev ..." -ForegroundColor Yellow
    adb -s $dev install -r -t "$apkPath"
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[INFO] Direct install failed, attempting push and pm install fallback..." -ForegroundColor Yellow
        adb -s $dev push "$apkPath" /data/local/tmp/orbit_debug.apk
        adb -s $dev shell pm install -r -d -t /data/local/tmp/orbit_debug.apk
        adb -s $dev shell rm /data/local/tmp/orbit_debug.apk
    }

    if ($LASTEXITCODE -eq 0) {
        Write-Host "[SUCCESS] $dev : Installation succeeded." -ForegroundColor Green
        Write-Host "Launching application on $dev ..." -ForegroundColor Cyan
        adb -s $dev shell am start -n "$packageName/$activityName" | Out-Null
        $successCount++
    } else {
        Write-Host "[ERROR] $dev : Installation failed." -ForegroundColor Red
        $failCount++
    }
}

Write-Host "`n======================================================" -ForegroundColor Cyan
Write-Host "[RESULT] Installation Finished - Success: $successCount, Failed: $failCount" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Cyan

# 5. Launch application and stream logs
Write-Host "`n[4/4] Streaming DSP logs from $primaryLogDev (Press Ctrl+C to terminate)..." -ForegroundColor Yellow
Write-Host "======================================================`n" -ForegroundColor Cyan

adb -s $primaryLogDev logcat -v color -s NativeDSP EqualizerService AudioEffectManager AudioSessionManager DeviceManager AndroidRuntime:E
