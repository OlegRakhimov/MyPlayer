#requires -Version 5.1
<#
.SYNOPSIS
    FreeLocalPlayer 0-C.3-D strict canonical JSON / Room-shadow safety QA.
.DESCRIPTION
    Audit = read-only checking, no APK install.
    Run   = audit + Gradle + signature recheck + signed in-place APK update,
            9 non-destructive instrumentation tests on Motorola Edge 60 Pro.
    No emulator, pm clear, uninstall, Room-primary activation, or user-data edit.
    BackupFile may point at a fresh SAF ZIP exported from the phone to the PC.
#>
[CmdletBinding()]
param(
    [ValidateSet('Audit','Run')][string]$Mode = 'Audit',
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [string]$BackupFile = '',
    [switch]$SkipGradleBuild
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectPath = [IO.Path]::GetFullPath($ProjectPath)
$Package = 'com.example.freelocalplayer'
$TestClass = 'com.example.freelocalplayer.data.CanonicalLibraryJsonDecoderDeviceTest'
$Runner = 'com.example.freelocalplayer.test/androidx.test.runner.AndroidJUnitRunner'
$Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT -PathType Container)) {
    $Sdk = $env:ANDROID_SDK_ROOT
} elseif ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME -PathType Container)) {
    $Sdk = $env:ANDROID_HOME
}
$Adb = Join-Path $Sdk 'platform-tools\adb.exe'
$Gradle = Join-Path $ProjectPath 'gradlew.bat'
$Apk = Join-Path $ProjectPath 'app\build\outputs\apk\debug\app-debug.apk'
$TestApk = Join-Path $ProjectPath 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
$TestSource = Join-Path $ProjectPath 'app\src\androidTest\java\com\example\freelocalplayer\data\CanonicalLibraryJsonDecoderDeviceTest.kt'
$ReportDir = Join-Path $ProjectPath ('.patch-backups\json-guard-phone-qa-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$ReportFile = Join-Path $ReportDir 'report.txt'
$Serial = $null

function Log([string]$Text) {
    $Line = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + ' ' + $Text
    Write-Host $Line
    Add-Content -LiteralPath $ReportFile -Value $Line -Encoding UTF8
}
function MustFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw ('MISSING_FILE=' + $Path) }
}
function InvokeAdb([string[]]$Arguments) {
    $Lines = @(& $Adb -s $Serial @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw ('ADB_COMMAND_FAILED=' + ($Arguments -join ' ') + ' output=' + ($Lines -join '; '))
    }
    return ($Lines -join "`n")
}
function VerifyMotorola {
    $lines = @(& $Adb devices -l)
    if ($LASTEXITCODE -ne 0) { throw 'ADB_DEVICES_FAILED' }
    $phones = @(
        $lines | Where-Object {
            $_ -match '^\S+\s+device(?:\s|$)' -and $_ -notmatch '^emulator-[0-9]+\s+'
        } | ForEach-Object { ($_ -split '\s+')[0] }
    )
    if ($phones.Length -ne 1) { throw ('EXPECTED_ONE_AUTHORIZED_PHYSICAL_PHONE actual=' + $phones.Length) }
    $script:Serial = [string]$phones[0]
    if ((InvokeAdb @('shell','getprop','ro.kernel.qemu')).Trim() -eq '1') { throw 'EMULATOR_NOT_ALLOWED' }
    $manufacturer = (InvokeAdb @('shell','getprop','ro.product.manufacturer')).Trim()
    $model = (InvokeAdb @('shell','getprop','ro.product.model')).Trim()
    $marketName = (InvokeAdb @('shell','getprop','ro.product.marketname')).Trim()
    if ($manufacturer -notmatch '(?i)motorola' -or ($model+' '+$marketName) -notmatch '(?i)edge\s*60\s*pro') {
        throw ('UNEXPECTED_DEVICE manufacturer=' + $manufacturer + ' model=' + $model)
    }
    if ((InvokeAdb @('shell','getprop','sys.boot_completed')).Trim() -ne '1') { throw 'PHONE_NOT_BOOTED' }
    Log ('VERIFIED_PHYSICAL_PHONE=PASS manufacturer=' + $manufacturer + ' model=' + $model)
    Log ('DEVICE_SERIAL=' + $Serial)
}
function InstalledPackageApk {
    $entries = @(& $Adb -s $Serial shell pm path $Package)
    if ($LASTEXITCODE -ne 0) { throw 'INSTALLED_APP_NOT_FOUND' }
    $paths = @($entries | Where-Object { $_ -match '^package:.*/base\.apk\s*$' } |
        ForEach-Object { ($_ -replace '^package:', '').Trim() })
    if ($paths.Length -ne 1) { throw 'INSTALLED_BASE_APK_NOT_IDENTIFIED' }
    $info = InvokeAdb @('shell','dumpsys','package',$Package)
    if ($info -notmatch '\bversionCode=20\b' -or $info -notmatch '\bversionName=1\.3\.12\b') {
        throw 'INSTALLED_APP_VERSION_NOT_1_3_12_CODE_20'
    }
    Log 'INSTALLED_APP_VERSION=1.3.12(20)'
    return $paths[0]
}
function Signer([string]$Tool, [string]$Path) {
    $raw = @(& $Tool verify --print-certs --verbose $Path 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('APKSIGNER_FAILED=' + $Path) }
    $certificates = @($raw | ForEach-Object {
        $match = [regex]::Match([string]$_,
            'Signer #[0-9]+ certificate SHA-256 digest:\s*([0-9a-fA-F: ]+)')
        if ($match.Success) { ($match.Groups[1].Value -replace '[^a-fA-F0-9]','').ToUpperInvariant() }
    } | Where-Object { $_.Length -eq 64 } | Sort-Object -Unique)
    if ($certificates.Length -ne 1) { throw ('UNEXPECTED_SIGNER_COUNT=' + $certificates.Length) }
    return $certificates[0]
}
function VerifyBackup([string]$Path) {
    MustFile $Path
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $members = @($zip.Entries)
        if ($members.Count -lt 2 -or $members.Count -gt 1000) { throw 'BACKUP_ZIP_MEMBER_COUNT_INVALID' }
        $map = @{}
        foreach ($member in $members) {
            $name = $member.FullName
            if ($name -ne 'prefs.json' -and $name -ne 'manifest.json' -and $name -notmatch '^music/[A-Za-z0-9_.-]+$') {
                throw ('BACKUP_ZIP_UNSAFE_PATH=' + $name)
            }
            if ($map.ContainsKey($name)) { throw ('BACKUP_DUPLICATE_MEMBER=' + $name) }
            if ($member.Length -gt 50MB) { throw ('BACKUP_MEMBER_TOO_LARGE=' + $name) }
            $map[$name] = $member
        }
        if (-not $map.ContainsKey('manifest.json') -or -not $map.ContainsKey('prefs.json')) {
            throw 'BACKUP_REQUIRED_FILES_MISSING'
        }
        $reader = New-Object IO.StreamReader($map['manifest.json'].Open(), [Text.Encoding]::UTF8)
        try { $manifest = ConvertFrom-Json ($reader.ReadToEnd()) }
        finally { $reader.Dispose() }
        if ([string]$manifest.format -ne 'FreeLocalPlayer-offline-backup' -or
            [int]$manifest.version -ne 1) {
            throw 'BACKUP_FORMAT_OR_VERSION_UNSUPPORTED'
        }
        # PS5.1: keep a collection object with a guaranteed Count property.
        # Do not rely on @($manifest.entries).Length / pipeline unrolling.
        $declared = New-Object 'System.Collections.Generic.List[object]'
        if ($null -ne $manifest.entries) {
            foreach ($manifestEntry in $manifest.entries) {
                [void]$declared.Add($manifestEntry)
            }
        }
        if ($declared.Count -ne ($members.Count - 1)) { throw 'BACKUP_MANIFEST_ENTRY_COUNT_MISMATCH' }
        $declaredNames = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::Ordinal)
        foreach ($entry in $declared) {
            $pathInZip = [string]$entry.path
            if (-not $declaredNames.Add($pathInZip)) {
                throw ('BACKUP_DUPLICATE_MANIFEST_ENTRY=' + $pathInZip)
            }
            if ($pathInZip -eq 'manifest.json' -or -not $map.ContainsKey($pathInZip)) {
                throw ('BACKUP_UNDECLARED_FILE=' + $pathInZip)
            }
            $target = $map[$pathInZip]
            if ([long]$entry.size -ne $target.Length) { throw ('BACKUP_SIZE_MISMATCH=' + $pathInZip) }
            $stream = $target.Open()
            $hasher = [Security.Cryptography.SHA256]::Create()
            try { $sha = [BitConverter]::ToString($hasher.ComputeHash($stream)).Replace('-','').ToLowerInvariant() }
            finally { $stream.Dispose(); $hasher.Dispose() }
            if ($sha -ne ([string]$entry.sha256).ToLowerInvariant()) {
                throw ('BACKUP_SHA256_MISMATCH=' + $pathInZip)
            }
        }
        $m = [string]$manifest.sourcePackage
        if ($m -ne $Package) { throw ('BACKUP_WRONG_SOURCE_PACKAGE=' + $m) }
        # An intact ZIP with an empty or missing library is NOT an acceptable
        # safety copy before updating a phone that has a real music catalog.
        $prefsReader = New-Object IO.StreamReader($map['prefs.json'].Open(), [Text.Encoding]::UTF8)
        try { $prefsRoot = ConvertFrom-Json ($prefsReader.ReadToEnd()) }
        finally { $prefsReader.Dispose() }
        $typed = $prefsRoot.local_library_v1.library
        if ($null -eq $typed -or [string]$typed.type -ne 'string') {
            throw 'BACKUP_NO_CANONICAL_LIBRARY_STRING'
        }
        $catalog = ConvertFrom-Json ([string]$typed.value)
        if ($null -eq $catalog.media -or $null -eq $catalog.playlists) {
            throw 'BACKUP_LIBRARY_ARRAYS_MISSING'
        }
        $tracks = 0
        foreach ($track in $catalog.media) {
            if ($null -eq $track -or
                [string]::IsNullOrWhiteSpace([string]$track.id) -or
                [string]::IsNullOrWhiteSpace([string]$track.uri)) {
                throw 'BACKUP_MEDIA_ENTRY_INVALID'
            }
            $tracks++
        }
        $playlists = 0
        foreach ($playlist in $catalog.playlists) {
            if ($null -eq $playlist -or
                [string]::IsNullOrWhiteSpace([string]$playlist.id) -or
                $null -eq $playlist.mediaIds) {
                throw 'BACKUP_PLAYLIST_ENTRY_INVALID'
            }
            $playlists++
        }
        if ($tracks -lt 1) { throw 'BACKUP_LIBRARY_EMPTY_NOT_SAFE_FOR_PHONE' }
        Log ('BACKUP_CATALOG_COUNTS=PASS tracks=' + $tracks + ' playlists=' + $playlists)
        Log ('BACKUP_MANIFEST_AND_ENTRY_SHA256=PASS entries=' + $declared.Count)
        Log ('BACKUP_CREATED_AT_MS=' + [string]$manifest.createdAtMs)
    } finally { $zip.Dispose() }
}
function GetBackupFile {
    if ($BackupFile -ne '') { return [IO.Path]::GetFullPath($BackupFile) }
    $root = Join-Path $ProjectPath '.patch-backups\real-zip-qa-20261114-171242-4833b926'
    $folders = @((Join-Path $env:USERPROFILE 'Downloads'), $root)
    $knownSha = '4833B926802E164C05661774EED449083128F64C8707025F6AC9B1576DCAD95B'
    foreach ($folder in $folders) {
        if (-not (Test-Path -LiteralPath $folder -PathType Container)) { continue }
        $candidates = @(Get-ChildItem -LiteralPath $folder -File -Recurse -Filter '*.zip' |
            Where-Object { $_.Length -eq 41843 })
        foreach ($candidate in $candidates) {
            if ((Get-FileHash -LiteralPath $candidate.FullName -Algorithm SHA256).Hash -eq $knownSha) {
                Log 'BACKUP_FRESHNESS=HISTORICAL_2026_11_14_NOT_CURRENT'
                return $candidate.FullName
            }
        }
    }
    throw 'BACKUP_REQUIRED_EXPORT_FRESH_SAF_ZIP_TO_PC_OR_SUPPLY_BACKUPFILE'
}
function TestSourceIntegrity {
    MustFile $TestSource
    $branch = (@(& git.exe -C $ProjectPath branch --show-current) -join '').Trim()
    if ($LASTEXITCODE -ne 0 -or $branch -ne 'main') { throw 'GIT_BRANCH_NOT_MAIN' }
    $paths = @(
        'app/src/main/java/com/example/freelocalplayer/data/CanonicalLibraryJsonDecoder.kt',
        'app/src/main/java/com/example/freelocalplayer/data/CanonicalLibraryStateGuard.kt',
        'app/src/main/java/com/example/freelocalplayer/data/LibraryStore.kt',
        'app/src/androidTest/java/com/example/freelocalplayer/data/CanonicalLibraryJsonDecoderDeviceTest.kt'
    )
    foreach ($relative in $paths) {
        $file = Join-Path $ProjectPath ($relative.Replace('/',[IO.Path]::DirectorySeparatorChar))
        MustFile $file
        $blob = (@(& git.exe -C $ProjectPath rev-parse ('HEAD:' + $relative)) -join '').Trim()
        if ($LASTEXITCODE -ne 0 -or $blob -notmatch '^[a-f0-9]{40}$') {
            throw ('SOURCE_NOT_IN_COMMITTED_HEAD=' + $relative)
        }
        & git.exe -C $ProjectPath diff --quiet HEAD -- $relative
        if ($LASTEXITCODE -ne 0) { throw ('SOURCE_HAS_LOCAL_CHANGES=' + $relative) }
    }
    Log 'COMMITTED_GUARDED_SOURCE=PASS'
}

$Original = Get-Location
try {
    New-Item -ItemType Directory -Path $ReportDir -Force | Out-Null
    Log ('JSON_GUARD_PHONE_QA_MODE=' + $Mode)
    foreach ($required in @($Adb, $Gradle, $Apk, $TestApk)) { MustFile $required }
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) { throw 'GIT_MISSING' }
    TestSourceIntegrity
    VerifyMotorola
    $base = InstalledPackageApk
    $backup = GetBackupFile
    VerifyBackup $backup
    $stored = Join-Path $ReportDir 'verified-saf-backup.zip'
    Copy-Item -LiteralPath $backup -Destination $stored
    Log ('PRESERVED_BACKUP_SHA256=' + (Get-FileHash -LiteralPath $stored -Algorithm SHA256).Hash)
    $candidates = @(Get-ChildItem -LiteralPath (Join-Path $Sdk 'build-tools') -Recurse -File -Filter apksigner.bat |
        Sort-Object FullName -Descending)
    if ($candidates.Length -lt 1) { throw 'APKSIGNER_NOT_FOUND' }
    $apksigner = $candidates[0].FullName
    $installedApk = Join-Path $ReportDir 'previous-installed-base.apk'
    $pulled = @(& $Adb -s $Serial pull $base $installedApk 2>&1)
    if ($LASTEXITCODE -ne 0) { throw 'PULL_INSTALLED_APK_FAILED' }
    $installedCertificate = Signer $apksigner $installedApk
    $candidateCertificate = Signer $apksigner $Apk
    $testCertificate = Signer $apksigner $TestApk
    if ($installedCertificate -ne $candidateCertificate -or $installedCertificate -ne $testCertificate) {
        throw 'SIGNING_CERT_MISMATCH_NO_INSTALL'
    }
    Log 'APK_SIGNING_CERT_MATCH=PASS'

    if ($Mode -eq 'Audit') {
        Log 'PHONE_CHANGED=NO'
        Log 'RESULT=PASS JSON_GUARD_PHONE_AUDIT'
        return
    }
    if (-not $SkipGradleBuild) {
        Push-Location $ProjectPath
        try {
            & $Gradle ':app:assembleDebug' ':app:assembleDebugAndroidTest' ':app:testDebugUnitTest' '--no-daemon'
            $buildCode = $LASTEXITCODE
        } finally { Pop-Location }
        if ($buildCode -ne 0) { throw 'GRADLE_BUILD_FAILED' }
        Log 'GRADLE_BUILD=PASS'
        if ((Signer $apksigner $Apk) -ne $installedCertificate -or
            (Signer $apksigner $TestApk) -ne $installedCertificate) {
            throw 'POST_BUILD_SIGNING_CERT_MISMATCH_NO_INSTALL'
        }
        Log 'POST_BUILD_APK_SIGNING_CERT_MATCH=PASS'
    }
    # Reject a device swap even after the APK and backup preflight passed.
    $beforeSerial = $Serial
    VerifyMotorola
    if ($Serial -ne $beforeSerial) { throw 'DEVICE_CHANGED_ABORT' }
    [void](InstalledPackageApk)
    $installOut = @(& $Adb -s $Serial install -r $Apk 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('APP_INPLACE_UPDATE_FAILED=' + ($installOut -join '; ')) }
    Log 'APP_UPDATE_IN_PLACE=PASS'
    $installTest = @(& $Adb -s $Serial install -r $TestApk 2>&1)
    if ($LASTEXITCODE -ne 0) { throw ('TEST_APK_INSTALL_FAILED=' + ($installTest -join '; ')) }
    Log 'TEST_APK_INSTALL=PASS'
    $raw = @(& $Adb -s $Serial shell am instrument -w -r -e class $TestClass $Runner 2>&1)
    $body = $raw -join "`n"
    Set-Content -LiteralPath (Join-Path $ReportDir 'instrumentation.txt') -Value $body -Encoding UTF8
    if ($LASTEXITCODE -ne 0 -or $body -notmatch '\bOK \(10 tests\)' -or
        $body -notmatch 'INSTRUMENTATION_CODE:\s*-1' -or
        $body -match 'Process crashed|FAILURES!!!|shortMsg=|INSTRUMENTATION_FAILED') {
        $raw | Select-Object -Last 100 | ForEach-Object { Write-Host $_ }
        throw 'ANDROID_JSON_GUARD_TESTS_FAILED'
    }
    Log 'ANDROID_CANONICAL_JSON_GUARD=PASS tests=10'
    Log 'REAL_CATALOG_CHECK=READ_ONLY'
    Log 'ROOM_PRIMARY_STORAGE=DISABLED_JSON_CANONICAL'
    Log ('RESULT=SUCCESS JSON_GUARD_MOTOROLA_QA_PASS report=' + $ReportDir)
} catch {
    Log ('RESULT=FAILED ' + $_.Exception.Message)
    throw
} finally {
    Set-Location $Original
}
