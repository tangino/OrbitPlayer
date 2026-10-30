param(
    [ValidateSet("phone", "reset", "status", "menu")]
    [string]$Mode = "menu",

    # 目标模拟器序列号；只有一台设备时可省略
    [string]$Serial = "",

    # 手机显示参数，默认 1080x2340 / 420dpi（约 19.5:9）
    [string]$Size = "1080x2340",
    [int]$Density = 420,

    # 设置后是否重启 App；默认重启，方便立刻看到布局变化
    [switch]$NoRestart,

    # 指定包名；默认自动在 com.rawsmusic.debug / com.rawsmusic 中选择
    [string]$Package = ""
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function Write-Ok([string]$Message) {
    Write-Host "    $Message" -ForegroundColor Green
}

function Write-Warn([string]$Message) {
    Write-Host "    $Message" -ForegroundColor Yellow
}

function Resolve-Adb {
    $candidates = New-Object System.Collections.Generic.List[string]

    if ($env:ANDROID_HOME) { $candidates.Add((Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")) }
    if ($env:ANDROID_SDK_ROOT) { $candidates.Add((Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")) }

    $localProperties = Join-Path $PSScriptRoot "local.properties"
    if (Test-Path $localProperties) {
        foreach ($line in Get-Content $localProperties) {
            if ($line -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
                $sdk = $Matches[1].Trim().Replace("\\", "\").Replace("\:", ":")
                $candidates.Add((Join-Path $sdk "platform-tools\adb.exe"))
            }
        }
    }

    $candidates.Add("E:\softwares\Android\sdk\platform-tools\adb.exe")
    $candidates.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"))

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }

    $fromPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($fromPath) { return $fromPath.Source }

    throw "找不到 adb。请设置 ANDROID_HOME，或在脚本同目录的 local.properties 中配置 sdk.dir。"
}

function Get-OnlineDevices([string]$Adb) {
    $lines = & $Adb devices 2>$null
    return @(
        $lines |
            Select-Object -Skip 1 |
            Where-Object { $_ -match '^\S+\s+device\s*$' } |
            ForEach-Object { ($_ -split '\s+')[0] }
    )
}

function Resolve-Serial([string]$Adb, [string]$Requested) {
    $devices = @(Get-OnlineDevices $Adb)

    if ($devices.Count -eq 0) {
        throw "没有已连接的模拟器/设备。请先启动模拟器，再运行脚本（adb devices 查看状态）。"
    }

    if ($Requested) {
        if ($devices -contains $Requested) { return $Requested }
        throw "设备 '$Requested' 不在线。当前在线设备：$($devices -join ', ')"
    }

    if ($devices.Count -eq 1) { return $devices[0] }

    Write-Host ""
    Write-Host "检测到多台设备：" -ForegroundColor Yellow
    for ($i = 0; $i -lt $devices.Count; $i++) {
        Write-Host ("  [{0}] {1}" -f ($i + 1), $devices[$i])
    }
    $choice = Read-Host "请选择设备编号"
    $index = 0
    if (-not [int]::TryParse($choice, [ref]$index) -or $index -lt 1 -or $index -gt $devices.Count) {
        throw "无效的设备编号。"
    }
    return $devices[$index - 1]
}

function Invoke-Adb([string]$Adb, [string]$Device, [string[]]$Arguments) {
    & $Adb -s $Device @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "adb 命令失败：adb -s $Device $($Arguments -join ' ')"
    }
}

function Show-DisplayState([string]$Adb, [string]$Device) {
    Write-Step "当前显示状态（$Device）"

    $size = & $Adb -s $Device shell wm size
    $density = & $Adb -s $Device shell wm density
    $rotation = & $Adb -s $Device shell settings get system user_rotation
    $autoRotate = & $Adb -s $Device shell settings get system accelerometer_rotation

    $size | ForEach-Object { Write-Host "    $_" }
    $density | ForEach-Object { Write-Host "    $_" }
    Write-Host "    user_rotation=$rotation  accelerometer_rotation=$autoRotate"
}

function Resolve-Package([string]$Adb, [string]$Device, [string]$Requested) {
    if ($Requested) { return $Requested }

    $installed = & $Adb -s $Device shell pm list packages 2>$null
    foreach ($candidate in @("com.rawsmusic.debug", "com.rawsmusic")) {
        if ($installed -contains "package:$candidate") { return $candidate }
    }
    return ""
}

function Restart-App([string]$Adb, [string]$Device, [string]$PackageName) {
    if (-not $PackageName) {
        Write-Warn "未找到 com.rawsmusic / com.rawsmusic.debug，跳过 App 重启。"
        return
    }

    Write-Step "重启 App（$PackageName）"
    & $Adb -s $Device shell am force-stop $PackageName | Out-Null
    Start-Sleep -Milliseconds 500
    & $Adb -s $Device shell monkey -p $PackageName -c android.intent.category.LAUNCHER 1 2>$null | Out-Null
    Start-Sleep -Seconds 2
    Write-Ok "已重启 $PackageName"
}

if ($Mode -eq "menu") {
    $selected = $null
    while (-not $selected) {
        Write-Host ""
        Write-Host "==========================================" -ForegroundColor Cyan
        Write-Host "  模拟器显示模式一键切换" -ForegroundColor Cyan
        Write-Host "==========================================" -ForegroundColor Cyan
        Write-Host "  [1] 设置为手机显示（1080x2340 / 420dpi）"
        Write-Host "  [2] 重置为平板默认显示"
        Write-Host "  [3] 查看当前显示状态"
        Write-Host "  [0] 退出"
        Write-Host ""
        $choice = Read-Host "请输入编号后回车"
        switch ($choice.Trim()) {
            "1" { $selected = "phone" }
            "2" { $selected = "reset" }
            "3" { $selected = "status" }
            "0" { return }
            default { Write-Host "无效输入，请重新选择。" -ForegroundColor Yellow }
        }
    }
    $Mode = $selected
    Write-Host ""
}

$adb = Resolve-Adb
$device = Resolve-Serial $adb $Serial

Write-Step "目标设备：$device"

switch ($Mode) {
    "phone" {
        Write-Step "设置为手机显示：$Size / $($Density)dpi"

        Invoke-Adb $adb $device @("shell", "wm", "size", $Size)
        Invoke-Adb $adb $device @("shell", "wm", "density", "$Density")
        Invoke-Adb $adb $device @("shell", "settings", "put", "system", "accelerometer_rotation", "0")
        Invoke-Adb $adb $device @("shell", "settings", "put", "system", "user_rotation", "0")

        Write-Ok "显示尺寸与密度已设置，并锁定竖屏"

        if (-not $NoRestart) {
            $package = Resolve-Package $adb $device $Package
            Restart-App $adb $device $package
        }

        Show-DisplayState $adb $device
        Write-Host ""
        Write-Host "提示：这只是覆盖显示参数，模拟器硬件仍是平板；系统栏可能出现轻微留白，属正常现象。" -ForegroundColor DarkGray
    }

    "reset" {
        Write-Step "重置为平板默认显示"

        Invoke-Adb $adb $device @("shell", "wm", "size", "reset")
        Invoke-Adb $adb $device @("shell", "wm", "density", "reset")
        Invoke-Adb $adb $device @("shell", "settings", "put", "system", "accelerometer_rotation", "1")

        Write-Ok "尺寸、密度已恢复，自动旋转已打开"

        if (-not $NoRestart) {
            $package = Resolve-Package $adb $device $Package
            Restart-App $adb $device $package
        }

        Show-DisplayState $adb $device
    }

    "status" {
        Show-DisplayState $adb $device
    }
}

Write-Host ""
Write-Host "完成。" -ForegroundColor Green
