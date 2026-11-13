#requires -Version 5.1
<#
.SYNOPSIS
    Non-destructive Android emulator QA for FreeLocalPlayer Room Shadow 0-C.2.
.DESCRIPTION
    Compiles the test APK and runs instrumented SQLite tests only on one
    verified Android emulator. It never installs or runs tests on a phone.
    Source files and application preferences are never modified by this script.
    Reports are retained inside the canonical project at .patch-backups.
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [string]$AvdName = 'sdk_gphone16k_x86_64',
    [switch]$DoNotStartEmulator,
    [switch]$SkipGradleBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$RequiredCommit = 'f2c641a04ba4f4f4f73d928cb41fd0976e3348f2'
$ExpectedPackage = 'com.example.freelocalplayer'
$ProjectPath = [System.IO.Path]::GetFullPath($ProjectPath)
$Gradle = Join-Path $ProjectPath 'gradlew.bat'
$AppApk = Join-Path $ProjectPath 'app\build\outputs\apk\debug\app-debug.apk'
$TestApk = Join-Path $ProjectPath 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT)) {
    $SdkRoot = $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME)) {
    $SdkRoot = $env:ANDROID_HOME
}
$Adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$EmulatorExe = Join-Path $SdkRoot 'emulator\emulator.exe'
$ReportDirectory = Join-Path $ProjectPath ('.patch-backups\room-qa-0c2-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$LogFile = Join-Path $ReportDirectory 'report.txt'

function Record([string]$Line) {
    $Printed = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Line
    Write-Host $Printed
    Add-Content -LiteralPath $LogFile -Value $Printed -Encoding UTF8
}
function AssertFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw ('FILE_MISSING=' + $Path) }
}
function EmulatorSerials {
    $Rows = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_DEVICES_FAILED' }
    return @($Rows | Where-Object { $_ -match '^(emulator-[0-9]+)\s+device(?:\s|$)' } |
        ForEach-Object { [regex]::Match($_,'^(emulator-[0-9]+)').Groups[1].Value })
}

$OriginalLocation = Get-Location
try {
    AssertFile $Gradle
    AssertFile $Adb
    if ($null -eq (Get-Command git.exe -ErrorAction SilentlyContinue)) { throw 'GIT_NOT_FOUND' }
    New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
    Record 'ROOM_EMULATOR_QA=START'
    $Branch = (& git.exe -C $ProjectPath branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $Branch -ne 'main') { throw 'LOCAL_BRANCH_NOT_MAIN' }
    & git.exe -C $ProjectPath merge-base --is-ancestor $RequiredCommit HEAD
    if ($LASTEXITCODE -ne 0) { throw 'ROOM_0C2_SOURCE_NOT_FOUND' }
    $MirrorSource = Join-Path $ProjectPath 'app\src\main\java\com\example\freelocalplayer\data\room\ShadowRoomMirror.kt'
    AssertFile $MirrorSource
    $Contents = [System.IO.File]::ReadAllText($MirrorSource)
    if (-not $Contents.Contains('internal fun mirrorInto(')) {
        throw 'PRODUCTION_MIRROR_TEST_HOOK_MISSING: install 0-C.2 QA patch first'
    }
    $Current = (& git.exe -C $ProjectPath rev-parse HEAD).Trim()
    Record ('GIT_HEAD=' + $Current)
    $DeviceList = @(EmulatorSerials)
    if ($DeviceList.Count -gt 1) { throw 'MULTIPLE_EMULATORS: close extras before testing' }
    if ($DeviceList.Count -eq 0) {
        if ($DoNotStartEmulator) {
            throw 'EMULATOR_NOT_RUNNING: start a dedicated Android emulator and retry'
        }
        AssertFile $EmulatorExe
        $Available = @(& $EmulatorExe -list-avds)
        if ($LASTEXITCODE -ne 0 -or $AvdName -notin $Available) {
            throw ('AVD_NOT_FOUND=' + $AvdName + '; available=' + ($Available -join ','))
        }
        Record ('STARTING_AVD=' + $AvdName)
        Start-Process -FilePath $EmulatorExe -ArgumentList @('-avd', $AvdName, '-no-snapshot-save') | Out-Null
        for ($Try = 0; $Try -lt 80; $Try++) {
            Start-Sleep -Seconds 3
            $DeviceList = @(EmulatorSerials)
            if ($DeviceList.Count -eq 1) {
                $Boot = ((& $Adb -s $DeviceList[0] shell getprop sys.boot_completed) -join '').Trim()
                if ($Boot -eq '1') { break }
            }
            if ($DeviceList.Count -gt 1) { throw 'MULTIPLE_EMULATORS_DURING_START' }
        }
    }
    $DeviceList = @(EmulatorSerials)
    if ($DeviceList.Count -ne 1) { throw 'EMULATOR_BOOT_FAILED_OR_NOT_UNIQUE' }
    $Serial = $DeviceList[0]
    $IsEmulated = ((& $Adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $IsEmulated -ne '1') {
        throw ('DEVICE_NOT_VERIFIED_EMULATOR=' + $Serial)
    }
    $BootComplete = ((& $Adb -s $Serial shell getprop sys.boot_completed) -join '').Trim()
    if ($BootComplete -ne '1') { throw 'EMULATOR_NOT_BOOTED' }
    Record ('ROOM_TEST_DEVICE=' + $Serial)
    Record 'ROOM_DEVICE_IS_EMULATOR=PASS'
    if (-not $SkipGradleBuild) {
        Record 'GRADLE_QA_BUILD=START'
        Push-Location $ProjectPath
        try {
            & $Gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon
            $GradleExit = $LASTEXITCODE
        } finally { Pop-Location }
        if ($GradleExit -ne 0) { throw ('GRADLE_QA_BUILD_FAILED=' + $GradleExit) }
        Record 'GRADLE_QA_BUILD=PASS'
    }
    AssertFile $AppApk
    AssertFile $TestApk
    Record ('APP_APK_SHA256=' + (Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash)
    Record ('TEST_APK_SHA256=' + (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash)

    # Never select a phone here; all commands carry the validated emulator serial.
    & $Adb -s $Serial install -r $AppApk
    if ($LASTEXITCODE -ne 0) { throw 'EMULATOR_APP_INSTALL_FAILED' }
    & $Adb -s $Serial install -r $TestApk
    if ($LASTEXITCODE -ne 0) { throw 'EMULATOR_TEST_INSTALL_FAILED' }
    Record 'EMULATOR_APKS_INSTALLED=PASS'

    $TestOutput = @(& $Adb -s $Serial shell am instrument -w -r `
        -e class 'com.example.freelocalplayer.data.room.ShadowRoomMigrationDeviceTest' `
        'com.example.freelocalplayer.test/androidx.test.runner.AndroidJUnitRunner' 2>&1)
    $TestExit = $LASTEXITCODE
    $TestText = $TestOutput -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDirectory 'instrumentation.txt') `
        -Value $TestText -Encoding UTF8
    $CountMatch = [regex]::Match($TestText, '(?m)\bOK \(([0-9]+) tests?\)')
    if ($TestExit -ne 0 -or -not $CountMatch.Success -or
        [int]$CountMatch.Groups[1].Value -lt 4 -or
        $TestText -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or
        $TestText -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT:\s*shortMsg=') {
        Record 'ROOM_ANDROID_INSTRUMENTATION=FAIL'
        throw 'ROOM_ANDROID_INSTRUMENTATION_FAILED: see instrumentation.txt'
    }
    Record ('ROOM_ANDROID_INSTRUMENTATION=PASS tests=' + $CountMatch.Groups[1].Value)

    # Verify the actual app opens its persistent shadow database on this emulator.
    # Force-stop affects only the emulator app process, not app data or the phone.
    & $Adb -s $Serial logcat -c
    & $Adb -s $Serial shell am force-stop $ExpectedPackage
    & $Adb -s $Serial shell am start -n ($ExpectedPackage + '/.MainActivity')
    if ($LASTEXITCODE -ne 0) { throw 'EMULATOR_APP_LAUNCH_FAILED' }
    Start-Sleep -Seconds 5
    $RoomLog = @(& $Adb -s $Serial logcat -d -s 'FLP_ROOM_MIRROR:I' '*:S') -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDirectory 'room-logcat.txt') `
        -Value $RoomLog -Encoding UTF8
    if ($RoomLog -match 'ROOM_MIRROR_FAILED') {
        Record 'ROOM_APP_SHADOW_OPEN=FAIL'
        throw 'EMULATOR_ROOM_SHADOW_REPORTED_FAILURE'
    }
    if ($RoomLog -notmatch 'ROOM_MIRROR_VERIFIED') {
        Record 'ROOM_APP_SHADOW_OPEN=NOT_CONFIRMED'
        throw 'NO_ROOM_MIRROR_VERIFIED_MESSAGE: inspect room-logcat.txt'
    }
    Record 'ROOM_APP_SHADOW_OPEN=PASS'
    Record 'PHYSICAL_PHONE_CHANGED=NO'
    Record 'RESULT=SUCCESS ROOM_EMULATOR_QA_PASS'
} catch {
    if (Test-Path -LiteralPath $ReportDirectory) {
        Record ('RESULT=FAILED ' + $_.Exception.Message)
    }
    throw
} finally {
    Set-Location $OriginalLocation
}
