<#
Offline-friendly Windows build helper (downloads Gradle from the official Gradle distribution
server only if an installed Gradle 8.14 is not found). Validates downloaded zip against the
official published SHA-256 sidecar. Android dependencies are fetched through Gradle HTTPS.
Usage: powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
Usage: powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Install
#>
param(
    [switch]$Install,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$ProjectDir = Split-Path -Parent $MyInvocation.MyCommand.Path

$JavaCandidates = @(
    $env:JAVA_HOME,
    'C:\Program Files\Android\Android Studio\jbr',
    'C:\Program Files\Java\jdk-21'
)
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    foreach ($candidate in $JavaCandidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'bin\java.exe'))) {
            $env:JAVA_HOME = $candidate
            $env:PATH = (Join-Path $candidate 'bin') + ';' + $env:PATH
            break
        }
    }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw 'Java 17+ не найдена. Установите Android Studio с JBR (Java 21).'
}

$SdkCandidates = @(
    $env:ANDROID_HOME,
    $env:ANDROID_SDK_ROOT,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
)
$SdkPath = $SdkCandidates | Where-Object {
    $_ -and (Test-Path (Join-Path $_ 'platforms'))
} | Select-Object -First 1
if (-not $SdkPath) {
    throw 'Android SDK не найден. Откройте Android Studio -> SDK Manager, установите SDK Platform 36 и Build-Tools.'
}
$env:ANDROID_HOME = $SdkPath
$env:ANDROID_SDK_ROOT = $SdkPath
$PropertiesFile = Join-Path $ProjectDir 'local.properties'
if (-not (Test-Path $PropertiesFile)) {
    $EscapedSdkPath = $SdkPath.Replace('\', '/')
    [System.IO.File]::WriteAllText($PropertiesFile, "sdk.dir=$EscapedSdkPath`n")
}

$GradleBat = $null
$HomeGradle = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.14-bin'
if (Test-Path $HomeGradle) {
    $GradleBat = Get-ChildItem -Path $HomeGradle -Filter 'gradle.bat' -Recurse -File -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match '[\\/]gradle-8[.]14[\\/]bin[\\/]gradle[.]bat$' } |
        Select-Object -First 1 -ExpandProperty FullName
}
if (-not $GradleBat) {
    $ToolDir = Join-Path $env:LOCALAPPDATA 'FreeLocalPlayer\gradle'
    $GradleBat = Join-Path $ToolDir 'gradle-8.14\bin\gradle.bat'
    if (-not (Test-Path $GradleBat)) {
        New-Item -Path $ToolDir -ItemType Directory -Force | Out-Null
        $ZipPath = Join-Path $ToolDir 'gradle-8.14-bin.zip'
        $SumPath = Join-Path $ToolDir 'gradle-8.14-bin.zip.sha256'
        $BaseUrl = 'https://services.gradle.org/distributions/gradle-8.14-bin.zip'
        Write-Host 'Скачивание официального Gradle 8.14 с проверкой SHA256...'
        Invoke-WebRequest -Uri ($BaseUrl + '.sha256') -OutFile $SumPath
        Invoke-WebRequest -Uri $BaseUrl -OutFile $ZipPath
        $ExpectedHash = ([System.IO.File]::ReadAllText($SumPath).Trim() -split '\s+')[0].ToUpperInvariant()
        $ActualHash = (Get-FileHash -Path $ZipPath -Algorithm SHA256).Hash.ToUpperInvariant()
        if ($ExpectedHash -notmatch '^[0-9A-F]{64}$' -or $ExpectedHash -ne $ActualHash) {
            throw 'SHA256 Gradle не совпадает. Архив не будет распакован.'
        }
        Expand-Archive -LiteralPath $ZipPath -DestinationPath $ToolDir -Force
        if (-not (Test-Path $GradleBat)) { throw 'Не найден gradle.bat после распаковки.' }
    }
}

Push-Location $ProjectDir
try {
    if (-not (Test-Path (Join-Path $ProjectDir 'gradle\wrapper\gradle-wrapper.jar'))) {
        Write-Host '=== Создаю стандартный Gradle Wrapper 8.14 ==='
        & $GradleBat --no-daemon wrapper --gradle-version 8.14 --distribution-type bin
        if ($LASTEXITCODE -ne 0) { throw 'Не удалось создать Gradle Wrapper.' }
    }
    Write-Host '=== Сборка APK и запуск unit-тестов ==='
    & $GradleBat --no-daemon :app:assembleDebug :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "Gradle завершился с ошибкой: $LASTEXITCODE" }

    $ApkPath = Join-Path $ProjectDir 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $ApkPath)) { throw 'Gradle завершился, но APK не найден.' }
    $Hash = (Get-FileHash -Path $ApkPath -Algorithm SHA256).Hash
    Write-Host "APK=$ApkPath"
    Write-Host "SHA256=$Hash"
    Write-Host 'TESTS=PASSED'

    if ($Install) {
        $AdbPath = Join-Path $SdkPath 'platform-tools\adb.exe'
        if (-not (Test-Path $AdbPath)) { throw 'adb.exe не найден. Установите Android SDK Platform-Tools.' }
        $Args = @()
        if (-not [string]::IsNullOrWhiteSpace($Serial)) {
            $Args = @('-s', $Serial)
        }
        & $AdbPath @Args install -r $ApkPath
        if ($LASTEXITCODE -ne 0) { throw "Установка через adb не удалась: $LASTEXITCODE" }
        Write-Host 'INSTALL=SUCCESS'
    }
} finally {
    Pop-Location
}
