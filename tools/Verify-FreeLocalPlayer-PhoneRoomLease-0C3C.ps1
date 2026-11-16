#requires -Version 5.1
<#
.SYNOPSIS
  FreeLocalPlayer 0-C.3-B: guarded Room reading QA on Motorola.
.DESCRIPTION
  Does not start an emulator. Runs a time-limited real Room-read cold start,
  then revokes the lease and verifies a JSON cold start. User catalog unchanged.
  Never uninstalls, clears app data, wipes storage, or changes applicationId.
  First checks a previously verified, exact-SHA-256 SAF ZIP backup, target device,
  version, SHA-256 signing cert of the installed and replacement APK.
  -Mode Audit: checks only; does not install APKs or execute tests.
  -Mode Run: after checks and build, updates signed app/test APKs, asserts flag OFF,
  arms a three-minute lease, runs real cold start, then revokes the lease even
  on failure. -Mode Recover: attempts to revoke the flag without installation.
  Backups/reports remain under the ONE canonical project directory.
#>
[CmdletBinding()]
param(
    [ValidateSet('Audit','Run','Recover')][string]$Mode = 'Audit',
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [switch]$SkipGradleBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$ProjectPath = [IO.Path]::GetFullPath($ProjectPath)
$Package = 'com.example.freelocalplayer'
$ExpectedCommit = 'bb023e77f89e7ee2e2fad1e1575a7b3cb81b6b78'
$ExpectedVersionCode = 20
$ExpectedVersionName = '1.3.12'
$ExpectedBackupHash = '4833B926802E164C05661774EED449083128F64C8707025F6AC9B1576DCAD95B'
$ExpectedBackupBytes = 41843L
$TestClass = 'com.example.freelocalplayer.data.room.TemporaryRoomReadRolloutDeviceTest'
$ReadOnlyClass = 'com.example.freelocalplayer.data.room.VerifiedRoomReadDeviceTest'
$Runner = 'com.example.freelocalplayer.test/androidx.test.runner.AndroidJUnitRunner'

$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT -PathType Container)) {
    $Sdk = $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME -PathType Container)) {
    $Sdk = $env:ANDROID_HOME
}
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$Apk = Join-Path $ProjectPath 'app\build\outputs\apk\debug\app-debug.apk'
$TestApk = Join-Path $ProjectPath 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$Gradle = Join-Path $ProjectPath 'gradlew.bat'
$GateFile = Join-Path $ProjectPath 'app\src\androidTest\java\com\example\freelocalplayer\data\room\TemporaryRoomReadRolloutDeviceTest.kt'
$ReportDir = Join-Path $ProjectPath ('.patch-backups\room-rollout-lease-phone-qa-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$Report = Join-Path $ReportDir 'report.txt'
$DeviceSerial = $null

function Log([string]$Line) {
    $Message = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Line
    Write-Host $Message
    Add-Content -LiteralPath $Report -Value $Message -Encoding UTF8
}
function RequireFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw ('MISSING_FILE=' + $Path) }
}
function AssertEqual([string]$Actual, [string]$Expected, [string]$ErrorCode) {
    if ($Actual -ne $Expected) { throw ($ErrorCode + ' actual=' + $Actual) }
}
function RunAdb([string[]]$CommandArgs) {
    $Output = @(& $Adb -s $DeviceSerial @CommandArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw ('ADB_COMMAND_FAILED=' + ($CommandArgs -join ' ') + ' output=' + ($Output -join ' '))
    }
    return ($Output -join "`n")
}
function Instrument([string]$Class, [string]$Method) {
    Log ('ANDROID_TEST_START=' + $Method)
    $Output = @(& $Adb -s $DeviceSerial shell am instrument -w -r `
        -e class ($Class + '#' + $Method) $Runner 2>&1)
    $ExitCode = $LASTEXITCODE
    $Body = $Output -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDir ($Method + '-instrumentation.txt')) `
        -Value $Body -Encoding UTF8
    if ($ExitCode -ne 0 -or $Body -notmatch '(?m)\bOK \(1 test\)' -or `
        $Body -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or `
        $Body -match 'Process crashed|FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=') {
        Log ('ANDROID_TEST_FAIL=' + $Method)
        $Output | Select-Object -Last 80 | ForEach-Object { Write-Host $_ }
        throw ('ANDROID_TEST_FAILED=' + $Method)
    }
    Log ('ANDROID_TEST_PASS=' + $Method)
}
function GetRolloutLog {
    $Lines = @(& $Adb -s $DeviceSerial logcat -d -v brief `
        -s 'FLP_ROOM_READ:I' 'FLP_ROOM_C3C:I' '*:S' 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_LOGCAT_READ_FAILED' }
    return ($Lines -join "`n")
}
function LogMarker([string]$Label) {
    $Token = $Label + '_' + [guid]::NewGuid().ToString('N')
    $null = RunAdb @('shell','log','-p','i','-t','FLP_ROOM_C3C',$Token)
    return $Token
}
function LogSince([string]$Marker) {
    $Body = GetRolloutLog
    $Position = $Body.LastIndexOf($Marker, [System.StringComparison]::Ordinal)
    if ($Position -lt 0) { throw ('QA_LOG_MARKER_MISSING=' + $Marker) }
    return $Body.Substring($Position)
}
function ColdStartExpected([string]$ExpectedLine, [string]$Label) {
    $null = RunAdb @('shell','am','force-stop',$Package)
    $Marker = LogMarker ('BEGIN_' + $Label)
    $Started = RunAdb @('shell','am','start','-n',($Package + '/.MainActivity'))
    Log ('COLD_START=' + $Label)
    $Since = ''
    $Found = $false
    for ($Try = 0; $Try -lt 28; $Try++) {
        Start-Sleep -Seconds 1
        $Since = LogSince $Marker
        if ($Since -match ('FLP_ROOM_READ[^\r\n]*' + [regex]::Escape($ExpectedLine))) {
            $Found = $true
            break
        }
        if ($Since -match 'FLP_ROOM_READ[^\r\n]*ROOM_READ_JSON_FALLBACK' -or
            $Since -match 'FLP_ROOM_READ[^\r\n]*ROLLBACK_FLAG') {
            break
        }
    }
    Set-Content -LiteralPath (Join-Path $ReportDir ($Label + '-coldstart-logcat.txt')) `
        -Value $Since -Encoding UTF8
    if (-not $Found) {
        Log ('COLD_START_FAIL=' + $Label + ' expected=' + $ExpectedLine)
        throw ('COLD_START_EXPECTED_LOG_NOT_FOUND=' + $ExpectedLine)
    }
    Log ('COLD_START_PASS=' + $Label + ' expected=' + $ExpectedLine)
}
function ReadQaFingerprint([string]$AfterMarker, [string]$Key) {
    $Content = LogSince $AfterMarker
    $Match = [regex]::Match($Content, [regex]::Escape($Key) + '=([0-9a-f]{64})')
    if (-not $Match.Success) { throw ('C3C_FINGERPRINT_MISSING=' + $Key) }
    return $Match.Groups[1].Value
}
function GetPhoneSerial {
    $Lines = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_DEVICES_FAILED' }
    $Phones = @(
        $Lines | Where-Object {
            $_ -match '^\S+\s+device(?:\s|$)' -and $_ -notmatch '^emulator-[0-9]+\s+'
        } | ForEach-Object { ($_ -split '\s+')[0] }
    )
    if ($Phones.Length -ne 1) {
        Log ('CONNECTED_PHYSICAL_DEVICE_COUNT=' + $Phones.Length)
        throw 'EXACTLY_ONE_AUTHORIZED_PHYSICAL_PHONE_REQUIRED'
    }
    return [string]$Phones[0]
}
function VerifyPhone {
    if ([string]::IsNullOrWhiteSpace($DeviceSerial) -or $DeviceSerial -match '^emulator-') {
        throw 'PHYSICAL_SERIAL_REQUIRED'
    }
    $Qemu = (RunAdb @('shell','getprop','ro.kernel.qemu')).Trim()
    if ($Qemu -eq '1') { throw 'EMULATOR_DETECTED_ABORT' }
    $Manufacturer = (RunAdb @('shell','getprop','ro.product.manufacturer')).Trim()
    $Model = (RunAdb @('shell','getprop','ro.product.model')).Trim()
    $MarketName = (RunAdb @('shell','getprop','ro.product.marketname')).Trim()
    $Product = ($Model + ' ' + $MarketName)
    if ($Manufacturer -notmatch '(?i)motorola' -or $Product -notmatch '(?i)edge\s*60\s*pro') {
        throw ('UNEXPECTED_PHONE_MODEL manufacturer=' + $Manufacturer + ' model=' + $Model + ' market=' + $MarketName)
    }
    $Boot = (RunAdb @('shell','getprop','sys.boot_completed')).Trim()
    if ($Boot -ne '1') { throw 'PHONE_NOT_BOOTED' }
    Log ('VERIFIED_PHYSICAL_PHONE=PASS manufacturer=' + $Manufacturer + ' model=' + $Model)
    Log ('DEVICE_SERIAL=' + $DeviceSerial)
}
function GetInstalledPackageData {
    $Lines = @(& $Adb -s $DeviceSerial shell pm path $Package 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'APP_NOT_INSTALLED_ON_PHONE_NO_INSTALL_AUTHORIZED' }
    $BaseApkPaths = @(
        $Lines | Where-Object { $_ -match '^package:.*(?:/|^)base\.apk\s*$' } |
            ForEach-Object { ($_ -replace '^package:', '').Trim() }
    )
    if ($BaseApkPaths.Length -ne 1) { throw 'COULD_NOT_IDENTIFY_INSTALLED_BASE_APK' }
    $Dump = RunAdb @('shell','dumpsys','package',$Package)
    $Code = [regex]::Match($Dump, '\bversionCode=([0-9]+)\b')
    $Name = [regex]::Match($Dump, '\bversionName=([^\s]+)')
    if (-not $Code.Success -or -not $Name.Success) { throw 'INSTALLED_VERSION_UNKNOWN' }
    $Number = [int]$Code.Groups[1].Value
    $Version = $Name.Groups[1].Value.Trim()
    Log ('PHONE_INSTALLED_VERSION=' + $Version + '(' + $Number + ')')
    if ($Number -gt $ExpectedVersionCode -or ($Number -eq $ExpectedVersionCode -and $Version -ne $ExpectedVersionName)) {
        throw 'PHONE_HAS_DIFFERENT_OR_NEWER_VERSION_NO_DOWNGRADE'
    }
    return $BaseApkPaths[0]
}
function GetSignerSha256([string]$Apksigner, [string]$Path) {
    $Output = @(& $Apksigner verify --print-certs --verbose $Path 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('APK_SIGNER_VERIFY_FAILED=' + $Path + ': ' + ($Output -join ' ')) }
    $Values = @(
        $Output | ForEach-Object {
            $Match = [regex]::Match([string]$_, 'Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)')
            if ($Match.Success) {
                ($Match.Groups[1].Value -replace '[^0-9a-fA-F]', '').ToUpperInvariant()
            }
        } | Where-Object { $_.Length -eq 64 } | Sort-Object -Unique
    )
    if ($Values.Length -eq 0) { throw ('NO_SIGNING_CERT_SHA256=' + $Path) }
    return ($Values -join ';')
}
function GetBackup {
    $Dirs = @((Join-Path $env:USERPROFILE 'Downloads'), (Join-Path $ProjectPath '.patch-backups\real-zip-qa-20261114-171242-4833b926'))
    $FilesWithExpectedHash = New-Object 'System.Collections.Generic.List[string]'
    foreach ($Dir in $Dirs) {
        if (-not (Test-Path -LiteralPath $Dir -PathType Container)) { continue }
        $Candidates = @(Get-ChildItem -LiteralPath $Dir -File -Filter '*.zip' -Recurse -ErrorAction Stop |
            Where-Object { $_.Length -eq $ExpectedBackupBytes })
        foreach ($File in $Candidates) {
            $Hash = (Get-FileHash -LiteralPath $File.FullName -Algorithm SHA256).Hash.ToUpperInvariant()
            if ($Hash -eq $ExpectedBackupHash) { $FilesWithExpectedHash.Add($File.FullName) }
        }
    }
    if ($FilesWithExpectedHash.Count -eq 0) {
        throw 'VERIFIED_REAL_SAF_BACKUP_NOT_FOUND_ON_PC: do not update the phone without backup'
    }
    $Original = [string]$FilesWithExpectedHash[0]
    $Preserved = Join-Path $ReportDir 'FreeLocalPlayer-verified-before-phone-qa.zip'
    Copy-Item -LiteralPath $Original -Destination $Preserved -ErrorAction Stop
    $BackupCopyHash = (Get-FileHash -LiteralPath $Preserved -Algorithm SHA256).Hash.ToUpperInvariant()
    AssertEqual $BackupCopyHash $ExpectedBackupHash 'BACKUP_COPY_HASH_MISMATCH'
    Log ('VERIFIED_BACKUP_SHA256=PASS tracks=628 playlists=3 musicFiles=8')
    Log ('PRESERVED_BACKUP=' + $Preserved)
    return $Preserved
}

$OriginalDir = Get-Location
try {
    RequireFile $Adb
    New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
    Log ('ROOM_LEASE_QA_MODE=' + $Mode)
    if ($Mode -eq 'Recover') {
        $DeviceSerial = GetPhoneSerial
        VerifyPhone
        # No APK installation, no Gradle and no media operation. This cleanup
        # can be rerun if a prior QA run was interrupted or the USB dropped.
        Instrument $TestClass 'revokeTemporaryLeaseAndVerifyJsonPresent'
        Log 'RESULT=SUCCESS ROOM_LEASE_RECOVERED_JSON_DEFAULT'
        return
    }
    RequireFile $Gradle
    RequireFile $GateFile
    RequireFile $Apk
    RequireFile $TestApk
    if ($null -eq (Get-Command git.exe -ErrorAction SilentlyContinue)) { throw 'GIT_NOT_FOUND' }
    $Branch = @(& git.exe -C $ProjectPath branch --show-current) -join ''
    if ($LASTEXITCODE -ne 0 -or $Branch.Trim() -ne 'main') { throw 'LOCAL_GIT_BRANCH_NOT_MAIN' }
    & git.exe -C $ProjectPath merge-base --is-ancestor $ExpectedCommit HEAD
    if ($LASTEXITCODE -ne 0) { throw 'ROOM_GATE_CODE_COMMIT_NOT_INSTALLED' }
    # Refuse to execute unexpected locally modified test/gate code.
    $ExpectedBlobs = @{
        'app/src/main/java/com/example/freelocalplayer/data/LibraryStore.kt' = '1d2f77d5af5940c916ff08454e619fea5ed54605'
        'app/src/main/java/com/example/freelocalplayer/data/room/RoomReadRolloutWindow.kt' = 'c882c6510457b1e28d42180dc6a47cc162524c6c'
        'app/src/androidTest/java/com/example/freelocalplayer/data/room/TemporaryRoomReadRolloutDeviceTest.kt' = '648359cb0be33b49268de4d04cd9dfd264c8e0a3'
        'app/src/main/java/com/example/freelocalplayer/data/room/VerifiedRoomReadSelector.kt' = '65979cc7bb75513db4e7c8f789912bddf8631b0f'
        'app/build.gradle.kts' = '95f5e92bd6a2b5fa484ac123e18db402401a4c08'
    }
    foreach ($RelativePath in $ExpectedBlobs.Keys) {
        $AbsolutePath = Join-Path $ProjectPath ($RelativePath.Replace('/', [IO.Path]::DirectorySeparatorChar))
        RequireFile $AbsolutePath
        # HEAD blob is independent of Windows CRLF checkout transformations.
        $GitBlob = @(& git.exe -C $ProjectPath rev-parse ('HEAD:' + $RelativePath)) -join ''
        if ($LASTEXITCODE -ne 0 -or $GitBlob.Trim() -ne $ExpectedBlobs[$RelativePath]) {
            throw ('UNEXPECTED_COMMITTED_SOURCE_ABORT=' + $RelativePath)
        }
        & git.exe -C $ProjectPath diff --quiet HEAD -- $RelativePath
        if ($LASTEXITCODE -ne 0) {
            throw ('LOCALLY_MODIFIED_SOURCE_ABORT=' + $RelativePath)
        }
    }
    Log 'GITHUB_TEST_SOURCE_BLOBS=PASS'

    $DeviceSerial = GetPhoneSerial
    VerifyPhone
    $PhoneBasePath = GetInstalledPackageData
    $Backup = GetBackup

    $SigningTools = @(
        Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Filter 'apksigner.bat' -Recurse -File -ErrorAction Stop |
            Sort-Object -Property FullName -Descending
    )
    if ($SigningTools.Length -eq 0) { throw 'APKSIGNER_NOT_FOUND' }
    $Apksigner = $SigningTools[0].FullName
    $InstalledApk = Join-Path $ReportDir 'previously-installed-base.apk'
    Log 'DOWNLOADING_INSTALLED_APK_FOR_SIGNATURE_CHECK=START'
    & $Adb -s $DeviceSerial pull $PhoneBasePath $InstalledApk
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $InstalledApk -PathType Leaf)) {
        throw 'INSTALLED_APK_PULL_FAILED_NO_PHONE_CHANGE'
    }
    $InstalledSigner = GetSignerSha256 $Apksigner $InstalledApk
    $CandidateSigner = GetSignerSha256 $Apksigner $Apk
    $TestSigner = GetSignerSha256 $Apksigner $TestApk
    if ($InstalledSigner -ne $CandidateSigner -or $CandidateSigner -ne $TestSigner) {
        Log 'APK_SIGNING_CERT_MATCH=FAIL'
        throw 'SIGNING_CERT_MISMATCH_NO_INSTALL_NO_UNINSTALL'
    }
    Log 'APK_SIGNING_CERT_MATCH=PASS'
    Log ('CANDIDATE_APP_SHA256=' + (Get-FileHash -LiteralPath $Apk -Algorithm SHA256).Hash)
    Log ('CANDIDATE_TEST_SHA256=' + (Get-FileHash -LiteralPath $TestApk -Algorithm SHA256).Hash)

    if ($Mode -eq 'Audit') {
        Log 'PHONE_CHANGED=NO'
        Log 'RESULT=PASS SAFE_PHONE_AUDIT_ONLY'
        exit 0
    }

    if (-not $SkipGradleBuild) {
        Log 'GRADLE_BUILD=START'
        Push-Location $ProjectPath
        try {
            & $Gradle ':app:assembleDebug' ':app:assembleDebugAndroidTest' ':app:testDebugUnitTest' '--no-daemon'
            $BuildExit = $LASTEXITCODE
        } finally { Pop-Location }
        if ($BuildExit -ne 0) { throw ('GRADLE_BUILD_FAILED=' + $BuildExit) }
        Log 'GRADLE_BUILD=PASS'
        # A Gradle rebuild may change the APK, so validate the signatures AGAIN.
        $CandidateSigner = GetSignerSha256 $Apksigner $Apk
        $TestSigner = GetSignerSha256 $Apksigner $TestApk
        if ($InstalledSigner -ne $CandidateSigner -or $CandidateSigner -ne $TestSigner) {
            throw 'POST_BUILD_SIGNATURE_MISMATCH_NO_INSTALL'
        }
        Log 'POST_BUILD_APK_SIGNING_CERT_MATCH=PASS'
    }

    # Identity check again immediately before adb install -r, to avoid ADB device swaps.
    AssertEqual (GetPhoneSerial) $DeviceSerial 'PHYSICAL_SERIAL_CHANGED_ABORT'
    VerifyPhone
    [void](GetInstalledPackageData)
    Log 'INSTALL_UPDATE_IN_PLACE=START'
    & $Adb -s $DeviceSerial install -r $Apk
    if ($LASTEXITCODE -ne 0) { throw 'APP_INPLACE_INSTALL_FAILED_NO_UNINSTALL_NO_CLEAR' }
    Log 'APP_UPDATE_IN_PLACE=PASS'
    & $Adb -s $DeviceSerial install -r $TestApk
    if ($LASTEXITCODE -ne 0) { throw 'TEST_APK_INSTALL_FAILED_NO_UNINSTALL_NO_CLEAR' }
    Log 'TEST_APK_INSTALL=PASS'

    $Code = RunAdb @('shell','dumpsys','package',$Package)
    if ($Code -notmatch '\bversionCode=20\b' -or $Code -notmatch '\bversionName=1\.3\.12\b') {
        throw 'PHONE_APP_VERSION_AFTER_INSTALL_MISMATCH'
    }
    Log 'PHONE_APP_VERSION_AFTER_INSTALL=PASS 1.3.12(20)'

    # First verify default-disabled read path and unchanged persisted JSON.
    Instrument $ReadOnlyClass 'actualPhoneCatalogIsVerifiedWithoutRepairOrFlagChanges'
    Instrument $TestClass 'expiredLeasePolicyDoesNotPermitRoom'

    # Capture a baseline fingerprint in logcat, arm a bounded lease, start the
    # real MainActivity as a fresh process, and then compare its load source.
    # If a test crashes or the phone disconnects, the new production code
    # expires the lease after at most 180 seconds on the next load.
    $AttemptedArm = $false
    $RevokeConfirmed = $false
    $SessionError = $null
    $Baseline = $null
    try {
        $ArmMarker = LogMarker 'BEFORE_ARM'
        $AttemptedArm = $true
        Instrument $TestClass 'armThreeMinuteVerifiedRoomLease'
        $Baseline = ReadQaFingerprint $ArmMarker 'BASELINE_JSON_SHA256'
        Log ('BASELINE_CANONICAL_SHA256=' + $Baseline)
        ColdStartExpected 'ROOM_READ_VERIFIED' 'room_enabled'
        $ActiveMarker = LogMarker 'BEFORE_SESSION_VERIFY'
        Instrument $TestClass 'verifyLeaseAndRealCatalogAfterColdStart'
        $AfterCold = ReadQaFingerprint $ActiveMarker 'CURRENT_JSON_SHA256'
        if ($Baseline -ne $AfterCold) {
            throw 'CATALOG_CHANGED_DURING_ROOM_COLDSTART_ABORT_REVOKE'
        }
        Log 'REAL_ROOM_COLD_START=PASS JSON_SHA256_UNCHANGED=YES'
    } catch {
        $SessionError = $_.Exception.Message
        Log ('ROLLOUT_SESSION_FAILED=' + $SessionError)
    } finally {
        if ($AttemptedArm) {
            try {
                $RevokeMarker = LogMarker 'BEFORE_REVOKE'
                Instrument $TestClass 'revokeTemporaryLeaseAndVerifyJsonPresent'
                $RevokeConfirmed = $true
                $AfterRevoke = ReadQaFingerprint $RevokeMarker 'FINAL_JSON_SHA256'
                Log 'ROLLOUT_REVOKE=PASS'
                if ($null -ne $Baseline -and $Baseline -ne $AfterRevoke) {
                    $SessionError = 'CATALOG_SHA256_CHANGED_AFTER_ROLLOUT'
                }
                ColdStartExpected 'ROOM_READ_JSON_DEFAULT' 'json_after_revoke'
            } catch {
                Log ('ROLLOUT_REVOKE_WARNING=' + $_.Exception.Message)
                $SessionError = 'ROLLBACK_UNVERIFIED_RUN_RECOVER_OR_WAIT_3_MINUTES'
            }
        }
    }
    if (-not $RevokeConfirmed) { throw 'ROLLBACK_UNVERIFIED_WAIT_LEASE_EXPIRY' }
    if (-not [string]::IsNullOrWhiteSpace($SessionError)) { throw $SessionError }
    Log 'REAL_ROOM_READ_COLD_START=PASS'
    Log 'JSON_ROLLBACK_COLD_START=PASS'
    Log 'CANONICAL_CATALOG_SHA256_UNCHANGED=PASS'
    Log 'MOTOROLA_5000_20000_BENCHMARK=SKIPPED_SAFETY'
    Log 'ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL'
    Log ('RESULT=SUCCESS ROOM_ROLLOUT_COLD_START_AND_REVOKE_PASS report=' + $ReportDir)
} catch {
    if (Test-Path -LiteralPath $ReportDir -PathType Container) {
        Log ('RESULT=FAILED ' + $_.Exception.Message)
        Log 'NO_PM_CLEAR_NO_UNINSTALL_PERFORMED=YES'
        Log 'IF_REVOKE_UNCONFIRMED=RUN_MODE_RECOVER_OR_WAIT_3_MINUTES'
    }
    throw
} finally {
    Set-Location $OriginalDir
}
