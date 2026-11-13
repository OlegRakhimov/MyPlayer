#requires -Version 5.1
<#
.SYNOPSIS
    Stage 0-C.2-R: isolated real Android backup/restore tests (Pixel_7 emulator only).
.DESCRIPTION
    No test restores into the app's production SharedPreferences, private music
    folder, or on-disk Room database. The instrumentation's ContextWrapper redirects
    all paths and preference names. Never selects a connected physical phone.
    No pm clear, uninstall, or mutation of other AVDs.
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [string]$AvdName = 'Pixel_7',
    [switch]$DoNotStartEmulator,
    [switch]$SkipGradleBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($AvdName -ne 'Pixel_7') { throw 'ONLY_PIXEL_7_ALLOWED: never test on WorkTime or other AVDs' }
$RequiredCommit = 'd5e8abf841c36e441707a065a51422e8d888305b'
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
$ReportDirectory = Join-Path $ProjectPath ('.patch-backups\backup-restore-qa-0c2r-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$LogFile = Join-Path $ReportDirectory 'report.txt'

function Record([string]$Message) {
    $Line = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message
    Write-Host $Line
    Add-Content -LiteralPath $LogFile -Value $Line -Encoding UTF8
}
function RequireFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw ('FILE_NOT_FOUND=' + $Path)
    }
}
function RunningEmulators {
    $Rows = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_DEVICES_FAILED' }
    return @($Rows | Where-Object { $_ -match '^(emulator-[0-9]+)\s+device(?:\s|$)' } |
        ForEach-Object { [regex]::Match($_, '^(emulator-[0-9]+)').Groups[1].Value })
}
function VerifyEmulator([string]$Serial, [string]$ExpectedAvd) {
    if ($Serial -notmatch '^emulator-[0-9]+$') { throw ('NOT_EMULATOR_SERIAL=' + $Serial) }
    $Qemu = ((& $Adb -s $Serial shell getprop ro.kernel.qemu) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $Qemu -ne '1') { throw 'NOT_AN_ANDROID_EMULATOR' }
    $Reported = @(& $Adb -s $Serial emu avd name | ForEach-Object { $_.Trim() })
    if ($LASTEXITCODE -ne 0 -or $ExpectedAvd -notin $Reported) {
        throw ('RUNNING_AVD_NOT_EXPECTED=' + $Serial + '; expected=' + $ExpectedAvd + '; got=' + ($Reported -join ','))
    }
    $Boot = ((& $Adb -s $Serial shell getprop sys.boot_completed) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $Boot -ne '1') { throw 'EMULATOR_NOT_BOOTED' }
}

$OriginalLocation = Get-Location
try {
    RequireFile $Gradle
    RequireFile $Adb
    RequireFile (Join-Path $ProjectPath 'app\src\androidTest\java\com\example\freelocalplayer\backup\BackupRestoreDeviceTest.kt')
    if ($null -eq (Get-Command git.exe -ErrorAction SilentlyContinue)) { throw 'GIT_NOT_FOUND' }
    $Branch = (& git.exe -C $ProjectPath branch --show-current).Trim()
    if ($LASTEXITCODE -ne 0 -or $Branch -ne 'main') { throw 'LOCAL_BRANCH_NOT_MAIN' }
    & git.exe -C $ProjectPath merge-base --is-ancestor $RequiredCommit HEAD
    if ($LASTEXITCODE -ne 0) { throw 'ROOM_STAGE_SOURCE_NOT_FOUND' }

    New-Item -ItemType Directory -Force -Path $ReportDirectory | Out-Null
    Record 'BACKUP_RESTORE_EMULATOR_QA=START'
    Record ('GIT_HEAD=' + (& git.exe -C $ProjectPath rev-parse HEAD).Trim())
    Record ('EXPECTED_AVD=' + $AvdName)

    $Emulators = @(RunningEmulators)
    if ($Emulators.Count -gt 1) { throw 'MULTIPLE_EMULATORS: leave only Pixel_7 running' }
    if ($Emulators.Count -eq 0) {
        if ($DoNotStartEmulator) { throw 'PIXEL_7_NOT_RUNNING' }
        RequireFile $EmulatorExe
        $Available = @(& $EmulatorExe -list-avds)
        if ($LASTEXITCODE -ne 0 -or $AvdName -notin $Available) {
            throw ('AVD_NOT_FOUND=' + $AvdName + '; available=' + ($Available -join ','))
        }
        Record ('STARTING_AVD=' + $AvdName)
        Start-Process -FilePath $EmulatorExe -ArgumentList @('-avd', $AvdName, '-no-snapshot-save') | Out-Null
        for ($Try = 0; $Try -lt 80; $Try++) {
            Start-Sleep -Seconds 3
            $Emulators = @(RunningEmulators)
            if ($Emulators.Count -gt 1) { throw 'MULTIPLE_EMULATORS_DURING_START' }
            if ($Emulators.Count -eq 1) {
                $Boot = ((& $Adb -s $Emulators[0] shell getprop sys.boot_completed) -join '').Trim()
                if ($Boot -eq '1') { break }
            }
        }
    }
    $Emulators = @(RunningEmulators)
    if ($Emulators.Count -ne 1) { throw 'EMULATOR_BOOT_FAILED_OR_NOT_UNIQUE' }
    $Serial = $Emulators[0]
    VerifyEmulator -Serial $Serial -ExpectedAvd $AvdName
    Record ('BACKUP_TEST_DEVICE=' + $Serial)
    Record 'BACKUP_DEVICE_IS_PIXEL_7_EMULATOR=PASS'

    if (-not $SkipGradleBuild) {
        Record 'GRADLE_QA_BUILD=START'
        Push-Location $ProjectPath
        try {
            & $Gradle :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --no-daemon
            $BuildExit = $LASTEXITCODE
        } finally { Pop-Location }
        if ($BuildExit -ne 0) { throw ('GRADLE_QA_BUILD_FAILED=' + $BuildExit) }
        Record 'GRADLE_QA_BUILD=PASS'
    }
    RequireFile $AppApk
    RequireFile $TestApk
    Record ('APP_APK_SHA256=' + (Get-FileHash -LiteralPath $AppApk -Algorithm SHA256).Hash)
    Record ('TEST_APK_SHA256=' + (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash)

    # Explicit serial: never use an implicitly selected physical Android phone.
    VerifyEmulator -Serial $Serial -ExpectedAvd $AvdName
    & $Adb -s $Serial install -r $AppApk
    if ($LASTEXITCODE -ne 0) { throw 'EMULATOR_APP_INSTALL_FAILED' }
    & $Adb -s $Serial install -r $TestApk
    if ($LASTEXITCODE -ne 0) { throw 'EMULATOR_TEST_INSTALL_FAILED' }
    Record 'EMULATOR_APKS_INSTALLED=PASS'

    # All test data is generated on-device and redirected to a random sandbox.
    $Output = @(& $Adb -s $Serial shell am instrument -w -r `
        -e class 'com.example.freelocalplayer.backup.BackupRestoreDeviceTest' `
        'com.example.freelocalplayer.test/androidx.test.runner.AndroidJUnitRunner' 2>&1)
    $TestExit = $LASTEXITCODE
    $Text = $Output -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDirectory 'instrumentation.txt') -Value $Text -Encoding UTF8
    $Count = [regex]::Match($Text, '(?m)\bOK \(([0-9]+) tests?\)')
    if ($TestExit -ne 0 -or -not $Count.Success -or
        [int]$Count.Groups[1].Value -lt 7 -or
        $Text -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or
        $Text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT:\s*shortMsg=') {
        Record 'BACKUP_RESTORE_INSTRUMENTATION=FAIL'
        throw 'BACKUP_RESTORE_INSTRUMENTATION_FAILED: see instrumentation.txt'
    }
    Record ('BACKUP_RESTORE_INSTRUMENTATION=PASS tests=' + $Count.Groups[1].Value)
    Record 'TEST_STORAGE_ISOLATED=PASS'
    Record 'PHYSICAL_PHONE_CHANGED=NO'
    Record 'RESULT=SUCCESS BACKUP_RESTORE_EMULATOR_QA_PASS'
} catch {
    if (Test-Path -LiteralPath $ReportDirectory) { Record ('RESULT=FAILED ' + $_.Exception.Message) }
    throw
} finally {
    Set-Location $OriginalLocation
}
