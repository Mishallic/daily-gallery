<#
.SYNOPSIS
    Builds, tests and runs Daily Gallery on the Android emulator. The VS Code tasks
    (.vscode/tasks.json) call this script; it also runs from any PowerShell prompt.

.DESCRIPTION
    setup       Once: download the emulator and an Android 15 image, and create a Pixel 7 device.
    start       Boot the emulator, if it isn't running, and wait until Android is ready.
    run         Build the app, install it on the emulator and open it. Starts the emulator first.
    test        Run the unit and Robolectric UI tests on the PC (no emulator needed).
    screens     Render every screen to a PNG in app\build\screenshots, on the PC (no emulator needed).
    screenshot  Save the emulator's screen to build\emulator.png.

    The build SDK (and adb) comes from local.properties. The emulator has an SDK root of its own,
    so its ~5 GB can sit on another drive. Paths default to this PC's; set JAVA_HOME,
    EMULATOR_SDK_ROOT or ANDROID_AVD_HOME to override them.

.EXAMPLE
    .\scripts\dev.ps1 run
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('setup', 'start', 'run', 'test', 'screens', 'screenshot')]
    [string]$Command
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

function Get-Setting([string]$Name, [string]$Default) {
    $value = [Environment]::GetEnvironmentVariable($Name)
    if ($value) { return $value }
    return $Default
}

$JavaHome = Get-Setting 'JAVA_HOME' "$env:USERPROFILE\android-tools\jdk17"
$EmulatorSdk = Get-Setting 'EMULATOR_SDK_ROOT' 'D:\android\sdk'
$AvdHome = Get-Setting 'ANDROID_AVD_HOME' 'D:\android\avd'
$AvdName = 'pixel7-api35'
$Image = 'system-images;android-35;google_apis;x86_64'
$AppId = 'com.dailygallery.app'

# local.properties is a Java properties file, so ':' and '\' are escaped.
$sdkLine = Get-Content (Join-Path $Root 'local.properties') -ErrorAction SilentlyContinue |
    Where-Object { $_ -like 'sdk.dir=*' } | Select-Object -First 1
if (-not $sdkLine) { throw 'local.properties needs sdk.dir=<path to the Android SDK>; see the README.' }
$BuildSdk = $sdkLine.Substring(8).Replace('\:', ':').Replace('\\', '\')
$Adb = Join-Path $BuildSdk 'platform-tools\adb.exe'
$EmulatorExe = Join-Path $EmulatorSdk 'emulator\emulator.exe'
$env:JAVA_HOME = $JavaHome

function Invoke-Gradle([string[]]$Tasks) {
    & (Join-Path $Root 'gradlew.bat') -p $Root --console=plain @Tasks
    if ($LASTEXITCODE -ne 0) { throw "Gradle failed: $($Tasks -join ' ')" }
}

function Test-Acceleration {
    $report = & (Join-Path $EmulatorSdk 'emulator\emulator-check.exe') accel 2>&1 | Out-String
    return ($report -match 'usable|is installed and')
}

function Write-AccelerationHelp {
    Write-Host ''
    Write-Host 'The emulator needs hardware acceleration, which is off on this PC. Turn it on once:'
    Write-Host '  1. Open PowerShell as Administrator and run:'
    Write-Host '       Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All'
    Write-Host '  2. Restart Windows.'
    Write-Host ''
}

function Install-Emulator {
    $systemImage = Join-Path $EmulatorSdk 'system-images\android-35\google_apis\x86_64\system.img'
    if ((Test-Path $EmulatorExe) -and (Test-Path $systemImage)) {
        Write-Host "Emulator and Android 15 image: already in $EmulatorSdk"
    } else {
        New-Item -ItemType Directory -Force $EmulatorSdk | Out-Null
        # The licences were already accepted for the build SDK.
        Copy-Item -Recurse -Force (Join-Path $BuildSdk 'licenses') $EmulatorSdk
        & (Join-Path $BuildSdk 'cmdline-tools\latest\bin\sdkmanager.bat') "--sdk_root=$EmulatorSdk" 'emulator' $Image
        if ($LASTEXITCODE -ne 0) { throw 'sdkmanager could not install the emulator.' }
    }

    # avdmanager only looks in its own SDK, so write the device's two files directly.
    $avdDir = Join-Path $AvdHome "$AvdName.avd"
    if (Test-Path $avdDir) {
        Write-Host "Device $AvdName`: already in $AvdHome"
    } else {
        New-Item -ItemType Directory -Force $avdDir | Out-Null
        [IO.File]::WriteAllLines((Join-Path $AvdHome "$AvdName.ini"), [string[]]@(
            'avd.ini.encoding=UTF-8',
            "path=$avdDir",
            'target=android-35'
        ))
        [IO.File]::WriteAllLines((Join-Path $avdDir 'config.ini'), [string[]]@(
            "AvdId=$AvdName",
            'avd.ini.displayname=Pixel 7 (Android 15)',
            'avd.ini.encoding=UTF-8',
            'abi.type=x86_64',
            'hw.cpu.arch=x86_64',
            'hw.cpu.ncore=4',
            'hw.ramSize=2048',
            'vm.heapSize=256',
            'disk.dataPartition.size=4G',
            'hw.device.manufacturer=Google',
            'hw.device.name=pixel_7',
            'hw.lcd.width=1080',
            'hw.lcd.height=2400',
            'hw.lcd.density=420',
            'hw.keyboard=yes',
            'hw.mainKeys=no',
            'hw.gpu.enabled=yes',
            'hw.gpu.mode=auto',
            'hw.camera.back=none',
            'hw.camera.front=none',
            'hw.sdCard=no',
            'showDeviceFrame=no',
            'fastboot.forceFastBoot=yes',
            'PlayStore.enabled=false',
            'tag.id=google_apis',
            'tag.display=Google APIs',
            'image.sysdir.1=system-images\android-35\google_apis\x86_64\'
        ))
        Write-Host "Device $AvdName`: created in $AvdHome"
    }

    if (Test-Acceleration) { Write-Host 'Hardware acceleration: on' } else { Write-AccelerationHelp }
}

function Test-EmulatorRunning {
    $devices = & $Adb devices
    return [bool]($devices | Where-Object { $_ -match '^emulator-\d+\s+device' })
}

function Start-Emulator {
    if (Test-EmulatorRunning) { return }
    if (-not (Test-Path $EmulatorExe)) { throw 'The emulator is not set up yet: run the "Set up emulator" task first.' }
    if (-not (Test-Acceleration)) { Write-AccelerationHelp; throw 'Hardware acceleration is off.' }

    # Only the emulator gets these, so Gradle keeps using the build SDK from local.properties.
    $env:ANDROID_SDK_ROOT = $EmulatorSdk
    $env:ANDROID_AVD_HOME = $AvdHome
    Start-Process -FilePath $EmulatorExe -ArgumentList @('-avd', $AvdName, '-no-boot-anim')
    Remove-Item Env:ANDROID_SDK_ROOT, Env:ANDROID_AVD_HOME

    Write-Host 'Waiting for Android to boot...'
    & $Adb wait-for-device
    $deadline = (Get-Date).AddMinutes(5)
    while (((& $Adb -e shell getprop sys.boot_completed 2>$null) | Out-String).Trim() -ne '1') {
        if ((Get-Date) -gt $deadline) { throw 'The emulator did not finish booting within 5 minutes.' }
        Start-Sleep -Seconds 2
    }
    Write-Host 'Emulator ready.'
}

switch ($Command) {
    'setup' { Install-Emulator }
    'start' { Start-Emulator }
    'run' {
        Start-Emulator
        Invoke-Gradle @('installDebug')
        & $Adb -e shell am start -n "$AppId/.MainActivity" | Out-Null
        Write-Host 'Daily Gallery is open on the emulator.'
    }
    'test' { Invoke-Gradle @('testDebugUnitTest') }
    'screens' {
        # The UI tests draw each screen on saved responses and real images; --rerun redraws them all.
        Invoke-Gradle @('testDebugUnitTest', '--tests', 'com.dailygallery.app.ui.*', '--rerun')
        Write-Host "Screens drawn in $(Join-Path $Root 'app\build\screenshots')"
    }
    'screenshot' {
        if (-not (Test-EmulatorRunning)) { throw 'The emulator is not running.' }
        $out = Join-Path $Root 'build\emulator.png'
        New-Item -ItemType Directory -Force (Split-Path $out) | Out-Null
        & $Adb -e shell screencap -p /sdcard/screen.png
        & $Adb -e pull /sdcard/screen.png $out | Out-Null
        Write-Host "Saved $out"
    }
}
