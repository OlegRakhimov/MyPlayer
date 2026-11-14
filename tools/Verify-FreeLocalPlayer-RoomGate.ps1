#requires -Version 5.1
<#
Stage 0-C.3-A: real JSON ↔ Room read-only preflight + isolated in-memory stress.
Never installs/operates on a phone; never clears Android application data.
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [ValidateSet('Pixel_7')][string]$AvdName = 'Pixel_7',
    [switch]$DoNotStartEmulator,
    [switch]$SkipGradleBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectPath = [IO.Path]::GetFullPath($ProjectPath)
$SdkRoot = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT)) { $SdkRoot = $env:ANDROID_SDK_ROOT }
elseif ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME)) { $SdkRoot = $env:ANDROID_HOME }
$Adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$Emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$Gradle = Join-Path $ProjectPath 'gradlew.bat'
$TestApk = Join-Path $ProjectPath 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$AppApk = Join-Path $ProjectPath 'app\build\outputs\apk\debug\app-debug.apk'
$TestFile = Join-Path $ProjectPath 'app\src\androidTest\java\com\example\freelocalplayer\data\room\RoomPrimaryPreflightDeviceTest.kt'
$ReportDir = Join-Path $ProjectPath ('.patch-backups\room-primary-gate-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$Log = Join-Path $ReportDir 'report.txt'
$TestClass = 'com.example.freelocalplayer.data.room.RoomPrimaryPreflightDeviceTest'
$Runner = 'com.example.freelocalplayer.test/androidx.test.runner.AndroidJUnitRunner'
function Report([string]$Line) {
    $Entry = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Line
    Write-Host $Entry
    Add-Content -LiteralPath $Log -Value $Entry -Encoding UTF8
}
function Require([string]$File) {
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { throw ('MISSING_FILE=' + $File) }
}
function RunningEmulators {
    $Result = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_DEVICES_FAILED' }
    return @($Result | Where-Object { $_ -match '^(emulator-[0-9]+)\s+device(?:\s|$)' } |
        ForEach-Object { [regex]::Match($_,'^(emulator-[0-9]+)').Groups[1].Value })
}
function VerifyAvd([string]$Serial) {
    if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'INVALID_EMULATOR_SERIAL' }
    $Qemu = ((& $Adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $Qemu -ne '1') { throw 'NOT_ANDROID_EMULATOR' }
    $Actual = @(& $Adb -s $Serial emu avd name | ForEach-Object { $_.Trim() })
    if ($LASTEXITCODE -ne 0 -or 'Pixel_7' -notin $Actual) { throw ('WRONG_AVD=' + ($Actual -join ',')) }
    $Boot = ((& $Adb -s $Serial shell getprop sys.boot_completed) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $Boot -ne '1') { throw 'EMULATOR_NOT_BOOTED' }
}
function RunCase([string]$Serial, [string]$MethodName, [string]$Label, [string]$RequiredLog) {
    Report ($Label + '=START')
    & $Adb -s $Serial logcat -c
    $Output = @(& $Adb -s $Serial shell am instrument -w -r `
        -e class ($TestClass + '#' + $MethodName) $Runner 2>&1)
    $ExitCode = $LASTEXITCODE
    $Body = $Output -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDir ($Label + '-instrumentation.txt')) -Value $Body -Encoding UTF8
    $Logcat = @(& $Adb -s $Serial logcat -d -v threadtime -b main -b system -b crash)
    $LogText = $Logcat -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDir ($Label + '-logcat.txt')) -Value $LogText -Encoding UTF8
    $Ok = $ExitCode -eq 0 -and $Body -match '(?m)\bOK \(1 test\)' -and `
        $Body -match 'INSTRUMENTATION_CODE:\s*-1' -and `
        $Body -notmatch 'Process crashed|FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg='
    if ($Ok -and $LogText -notmatch [regex]::Escape($RequiredLog)) {
        $Ok = $false
        Report ($Label + '_REQUIRED_DIAGNOSTIC_MISSING=' + $RequiredLog)
    }
    if (-not $Ok) {
        Report ($Label + '=FAIL')
        $Output | Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
        $Filtered = @($Logcat | Select-String 'FATAL EXCEPTION|AndroidRuntime|FLP_ROOM_GATE|Caused by|OutOfMemoryError|SIGSEGV' -Context 2,9)
        foreach ($Record in ($Filtered | Select-Object -Last 20)) { Write-Host $Record }
        throw ('ROOM_PRIMARY_GATE_CASE_FAILED=' + $Label)
    }
    Report ($Label + '=PASS')
    $DebugLines = @($Logcat | Select-String 'FLP_ROOM_GATE' | Select-Object -Last 8)
    foreach ($Record in $DebugLines) { Report ('DETAIL=' + $Record.ToString().Trim()) }
}
$InitialLocation = Get-Location
try {
    Require $Adb
    Require $Gradle
    Require $TestFile
    if (-not (Test-Path -LiteralPath $ProjectPath -PathType Container)) { throw 'PROJECT_PATH_MISSING' }
    New-Item -Path $ReportDir -ItemType Directory -Force | Out-Null
    Report 'ROOM_PRIMARY_GATE=START'
    $Devices = @(RunningEmulators)
    if ($Devices.Length -gt 1) { throw 'MULTIPLE_EMULATORS_CLOSE_EXTRAS' }
    if ($Devices.Length -eq 0) {
        if ($DoNotStartEmulator) { throw 'PIXEL_7_NOT_RUNNING' }
        Require $Emulator
        $Available = @(& $Emulator -list-avds)
        if ($LASTEXITCODE -ne 0 -or 'Pixel_7' -notin $Available) { throw 'PIXEL_7_AVD_MISSING' }
        Report 'STARTING_PIXEL_7=YES'
        Start-Process -FilePath $Emulator -ArgumentList @('-avd','Pixel_7','-no-snapshot-save') | Out-Null
        for ($Attempt=0; $Attempt -lt 90; $Attempt++) {
            Start-Sleep -Seconds 2
            $Devices = @(RunningEmulators)
            if ($Devices.Length -gt 1) { throw 'MULTIPLE_EMULATORS_AFTER_START' }
            if ($Devices.Length -eq 1) {
                $Boot = ((& $Adb -s $Devices[0] shell getprop sys.boot_completed) -join '').Trim()
                if ($Boot -eq '1') { break }
            }
        }
    }
    $Devices = @(RunningEmulators)
    if ($Devices.Length -ne 1) { throw 'PIXEL_7_NOT_READY' }
    $Serial = $Devices[0]
    VerifyAvd $Serial
    Report ('VERIFIED_AVD=Pixel_7 serial=' + $Serial)
    $Info = @(& $Adb -s $Serial shell dumpsys package com.example.freelocalplayer)
    if ($LASTEXITCODE -ne 0) { throw 'APP_PACKAGE_UNAVAILABLE' }
    $InfoString = $Info -join "`n"
    if ($InfoString -notmatch 'versionCode=20\b' -or $InfoString -notmatch 'versionName=1\.3\.12\b') {
        throw 'EXPECTED_ANDROID_VERSION_1_3_12_CODE_20_REQUIRED'
    }
    Report 'ANDROID_APP_VERSION=1.3.12(20)'
    if (-not $SkipGradleBuild) {
        Report 'GRADLE_BUILD=START'
        Push-Location $ProjectPath
        try {
            & $Gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon
            $Result = $LASTEXITCODE
        } finally { Pop-Location }
        if ($Result -ne 0) { throw ('GRADLE_BUILD_FAILED=' + $Result) }
        Report 'GRADLE_BUILD=PASS'
    }
    Require $TestApk
    Require $AppApk
    Report ('APP_APK_SHA256=' + (Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash)
    Report ('TEST_APK_SHA256=' + (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash)
    VerifyAvd $Serial
    # The new gate class is in the target APK, so test APK alone is insufficient.
    # ADB install -r keeps Pixel_7 app data; signature mismatch blocks, never uninstall.
    & $Adb -s $Serial install -r $AppApk
    if ($LASTEXITCODE -ne 0) { throw 'PIXEL_7_APP_UPDATE_FAILED_KEEP_DATA_NO_UNINSTALL' }
    Report 'PIXEL_7_APP_UPDATE_IN_PLACE=PASS'
    & $Adb -s $Serial install -r $TestApk
    if ($LASTEXITCODE -ne 0) { throw 'TEST_APK_INSTALL_FAILED' }
    Report 'EMULATOR_TEST_APK_INSTALLED=PASS'
    RunCase $Serial 'importedCanonicalJsonMatchesExistingRoomShadowReadOnly' 'REAL_LIBRARY_GATE' 'ROOM_PRIMARY_PREFLIGHT=PASS REAL_JSON_ROOM_MATCH=YES'
    RunCase $Serial 'synthetic5000RecordsMirrorRoundTrip' 'LOAD_5000_GATE' 'PERF_5000_TRACKS=PASS'
    RunCase $Serial 'synthetic20000RecordsMirrorRoundTrip' 'LOAD_20000_GATE' 'PERF_20000_TRACKS=PASS'
    Report 'PHYSICAL_PHONE_CHANGED=NO'
    Report 'ROOM_PRIMARY_CUTOVER=DISABLED'
    Report 'RESULT=SUCCESS ROOM_PRIMARY_PREFLIGHT_3_TESTS_PASS'
} catch {
    if (Test-Path -LiteralPath $ReportDir -PathType Container) {
        Report ('RESULT=FAILED ' + $_.Exception.Message)
    }
    throw
} finally { Set-Location $InitialLocation }
