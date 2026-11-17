#requires -Version 5.1
<#
.SYNOPSIS
  0-C.3-E1: 10 isolated failure-injection Android tests on physical Motorola only.
.DESCRIPTION
  Installs ONLY androidTest APK. Does not install, clear, uninstall or restart the
  main app. Does not change production JSON, Room, media, package, version or flags.
  Reads the SHA-256 of the real SharedPreferences XML before and after QA using
  debug-only run-as; no private data is printed or exported.
#>
[CmdletBinding()]
param(
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [Parameter(Mandatory = $true)][ValidatePattern('^[a-fA-F0-9]{64}$')][string]$ExpectedBackupSha256
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectPath = [IO.Path]::GetFullPath($ProjectPath)
$BackupFile = [IO.Path]::GetFullPath($BackupFile)
$Serial = 'ZY22LHSVD7'
$Package = 'com.example.freelocalplayer'
$TestPackage = 'com.example.freelocalplayer.test'
$TestClass = 'com.example.freelocalplayer.data.room.MotorolaRoomFailureIsolationDeviceTest'
$Runner = $TestPackage + '/androidx.test.runner.AndroidJUnitRunner'
$OldAudit = Join-Path $ProjectPath 'tools\Verify-FreeLocalPlayer-PhoneJsonGuard-0C3D.ps1'
$TestSource = Join-Path $ProjectPath 'app\src\androidTest\java\com\example\freelocalplayer\data\room\MotorolaRoomFailureIsolationDeviceTest.kt'
$TestApk = Join-Path $ProjectPath 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$MainApk = Join-Path $ProjectPath 'app\build\outputs\apk\debug\app-debug.apk'
$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT -PathType Container)) { $Sdk = $env:ANDROID_SDK_ROOT }
elseif ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME -PathType Container)) { $Sdk = $env:ANDROID_HOME }
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$ReportDir = Join-Path $ProjectPath ('.patch-backups\phone-failure-qa-0c3e1-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$ReportFile = Join-Path $ReportDir 'report.txt'

function Log([string]$Message) {
    $line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $Message
    Write-Host $line
    Add-Content -LiteralPath $ReportFile -Value $line -Encoding UTF8
}
function MustFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw ('MISSING_FILE=' + $Path) }
}
function InvokeAdb([string[]]$Arguments) {
    # PS 5.1 treats successful native stderr as an exception under Stop.
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = @(& $Adb -s $Serial @Arguments 2>&1)
        $exit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $old }
    if ($exit -ne 0) { throw ('ADB_FAILED command=' + ($Arguments -join ' ') + ' exit=' + $exit + ' output=' + ($output -join '; ')) }
    return (($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}
function CheckPhone {
    $devices = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_DEVICES_FAILED' }
    $phones = @($devices | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' -and $_ -notmatch '^emulator-[0-9]+\s+' } |
        ForEach-Object { ($_ -split '\s+')[0] })
    if ($phones.Count -ne 1 -or $phones[0] -ne $Serial) { throw 'ONLY_EXPECTED_MOTOROLA_PHYSICAL_PHONE_ALLOWED' }
    if ((InvokeAdb @('shell','getprop','ro.kernel.qemu')).Trim() -eq '1') { throw 'DEVICE_IS_NOT_PHYSICAL' }
    $manufacturer = InvokeAdb @('shell','getprop','ro.product.manufacturer')
    $model = InvokeAdb @('shell','getprop','ro.product.model')
    $market = InvokeAdb @('shell','getprop','ro.product.marketname')
    if ($manufacturer -notmatch '(?i)motorola' -or ($model+' '+$market) -notmatch '(?i)edge\s*60\s*pro') {
        throw 'MOTOROLA_MODEL_MISMATCH'
    }
    if ((InvokeAdb @('shell','getprop','sys.boot_completed')).Trim() -ne '1') { throw 'PHONE_NOT_BOOTED' }
    $info = InvokeAdb @('shell','dumpsys','package',$Package)
    if ($info -notmatch '\bversionCode=20\b' -or $info -notmatch '\bversionName=1\.3\.12\b') {
        throw 'PHONE_INSTALLED_VERSION_NOT_1_3_12_20'
    }
    Log ('VERIFIED_PHYSICAL_PHONE=PASS manufacturer=' + $manufacturer.Trim() + ' model=' + $model.Trim())
    Log ('DEVICE_SERIAL=' + $Serial)
}
function ApkCertificate([string]$Tool, [string]$Path) {
    $old = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $raw = @(& $Tool verify --print-certs --verbose $Path 2>&1)
        $exit = $LASTEXITCODE
    } finally { $ErrorActionPreference = $old }
    if ($exit -ne 0) { throw ('APKSIGNER_FAILED=' + $Path) }
    $certs = @($raw | ForEach-Object {
        $match = [regex]::Match([string]$_, 'Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)')
        if ($match.Success) { ($match.Groups[1].Value -replace '[^a-fA-F0-9]', '').ToUpperInvariant() }
    } | Where-Object { $_.Length -eq 64 } | Sort-Object -Unique)
    if ($certs.Count -ne 1) { throw ('APK_SIGNER_COUNT_INVALID=' + $certs.Count) }
    return $certs[0]
}
function PrivateJsonXmlHash {
    # Read-only; debug APK allows run-as. No private XML or user media leaves the device.
    $line = InvokeAdb @('shell','run-as',$Package,'sha256sum','shared_prefs/local_library_v1.xml')
    $match = [regex]::Match($line, '(?m)^\s*([a-fA-F0-9]{64})\s+.*local_library_v1\.xml\s*$')
    if (-not $match.Success) { throw 'CANONICAL_XML_SHA256_UNAVAILABLE_NO_TEST_INSTALL' }
    return $match.Groups[1].Value.ToUpperInvariant()
}
function InstallTestOnly([string]$ApkPath) {
    # Start-Process avoids the PowerShell 5.1 native stderr pitfall.
    $process = Start-Process -FilePath $Adb -ArgumentList @(
        '-s', $Serial, 'install', '-r', ('"' + $ApkPath + '"')
    ) -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw ('ANDROID_TEST_APK_INSTALL_FAILED exit=' + $process.ExitCode) }
}

try {
    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    Log 'ROOM_FAILURE_PHONE_E1=START'
    foreach ($file in @($Adb, $OldAudit, $TestSource, $TestApk, $MainApk, $BackupFile)) { MustFile $file }
    $backupHash = (Get-FileHash -LiteralPath $BackupFile -Algorithm SHA256).Hash
    if ($backupHash -ne $ExpectedBackupSha256.ToUpperInvariant()) { throw 'SAF_BACKUP_SHA256_MISMATCH_NO_INSTALL' }
    Log ('BACKUP_SHA256=PASS sha256=' + $backupHash)
    CheckPhone
    # Reuse the established strict ZIP, signing, and real-catalog safety audit.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $OldAudit -Mode Audit -ProjectPath $ProjectPath -BackupFile $BackupFile
    if ($LASTEXITCODE -ne 0) { throw 'STRICT_0C3D_AUDIT_FAILED_NO_TEST_INSTALL' }
    Log 'STRICT_0C3D_AUDIT=PASS'
    $basePathLine = InvokeAdb @('shell','pm','path',$Package)
    $baseMatch = [regex]::Match($basePathLine, '(?m)^package:(/\S*/base\.apk)\s*$')
    if (-not $baseMatch.Success) { throw 'PHONE_INSTALLED_BASE_APK_NOT_FOUND' }
    $basePath = $baseMatch.Groups[1].Value
    $installedPath = Join-Path $ReportDir 'installed-base-before-qa.apk'
    $process = Start-Process -FilePath $Adb -ArgumentList @(
        '-s', $Serial, 'pull', ('"' + $basePath + '"'), ('"' + $installedPath + '"')
    ) -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) { throw 'PHONE_APK_PULL_FAILED' }
    MustFile $installedPath
    $buildTools = @(Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Recurse -File -Filter 'apksigner.bat' |
        Sort-Object FullName -Descending)
    if ($buildTools.Count -eq 0) { throw 'APKSIGNER_NOT_FOUND' }
    $apksigner = $buildTools[0].FullName
    $installedCert = ApkCertificate $apksigner $installedPath
    if ($installedCert -ne (ApkCertificate $apksigner $TestApk) -or
        $installedCert -ne (ApkCertificate $apksigner $MainApk)) {
        throw 'APK_SIGNING_CERT_MISMATCH_NO_INSTALL'
    }
    Log 'APK_CERTIFICATES_MATCH=PASS'
    $sourceHash = (Get-FileHash -LiteralPath $TestSource -Algorithm SHA256).Hash
    Log ('QA_TEST_SOURCE_SHA256=' + $sourceHash)
    $beforeJsonHash = PrivateJsonXmlHash
    Log ('CANONICAL_XML_SHA256_BEFORE=' + $beforeJsonHash)
    CheckPhone
    InstallTestOnly $TestApk
    Log 'TEST_APK_ONLY_INSTALL=PASS'
    Log 'MAIN_APP_APK_REINSTALLED=NO'
    $raw = InvokeAdb @('shell','am','instrument','-w','-r','-e','class',$TestClass,$Runner)
    Set-Content -LiteralPath (Join-Path $ReportDir 'instrumentation.txt') -Value $raw -Encoding UTF8
    if ($raw -notmatch '\bOK \(10 tests\)' -or $raw -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or
        $raw -match 'FAILURES!!!|Process crashed|INSTRUMENTATION_FAILED|INSTRUMENTATION_RESULT:\s*shortMsg=') {
        Write-Host (($raw -split "`n" | Select-Object -Last 90) -join "`n")
        throw 'ROOM_FAILURE_E1_INSTRUMENTATION_FAILED'
    }
    Log 'ROOM_FAILURE_E1_INSTRUMENTATION=PASS tests=10'
    $afterJsonHash = PrivateJsonXmlHash
    Log ('CANONICAL_XML_SHA256_AFTER=' + $afterJsonHash)
    if ($beforeJsonHash -ne $afterJsonHash) { throw 'CANONICAL_XML_CHANGED_DURING_QA_INVESTIGATE' }
    Log 'CANONICAL_XML_UNCHANGED=PASS'
    Log 'ISOLATION=QA_ROOM_ONLY_NO_PRODUCTION_ROOM_WRITES'
    Log 'ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL'
    Log 'RESULT=SUCCESS MOTOROLA_ROOM_FAILURE_E1_PASS'
} catch {
    if (Test-Path -LiteralPath $ReportDir -PathType Container) { Log ('RESULT=FAILED ' + $_.Exception.Message) }
    throw
}
