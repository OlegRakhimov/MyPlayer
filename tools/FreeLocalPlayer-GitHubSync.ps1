#requires -Version 5.1
<#
.SYNOPSIS
    Safe GitHub synchronization for the EXISTING FreeLocalPlayer Windows project.
.DESCRIPTION
    Setup: imports the actual local project to an EMPTY GitHub repository, installs
      this script in the same project folder and registers a five-minute task.
    Sync: snapshots eligible local changes and pushes; fast-forward pulls clean branches.
    Status: prints local Git state and the scheduled-task state.
    Archive: archives the committed project sources for ChatGPT, excluding .git.
    RemoveSchedule: removes only the scheduled task (leaves files and Git untouched).
    Public repository: inspect the source for sensitive content before publishing.
.NOTES
    Run on Windows under the account that owns the project. Requires Git for Windows.
    No administrator rights should be necessary for an ordinary user task, but
    your Windows policy may restrict scheduling or network access.
#>
[CmdletBinding()]
param(
    [ValidateSet('Setup', 'Sync', 'Status', 'Archive', 'RemoveSchedule')]
    [string]$Mode = 'Status',
    [string]$ProjectPath = (Join-Path $env:USERPROFILE 'Documents\AndroidProjects\FreeLocalPlayer'),
    [string]$RepositoryUrl = 'https://github.com/OlegRakhimov/MyPlayer.git',
    [int]$MaximumStagedFileMegabytes = 25,
    [switch]$PublishExistingGitHistory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:ProjectPath = [System.IO.Path]::GetFullPath($ProjectPath)
$script:GitExecutable = $null
$script:TaskName = 'FreeLocalPlayer-GitHub-AutoSync'
$script:ScriptRelativePath = 'tools\FreeLocalPlayer-GitHubSync.ps1'
$script:InstalledScript = Join-Path $script:ProjectPath $script:ScriptRelativePath
$script:SyncLogPath = Join-Path $script:ProjectPath '.git\freelocalplayer-sync.log'

function Write-Event {
    param([Parameter(Mandatory = $true)][string]$Message)
    $line = ('{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Message)
    Write-Host $line
    if (Test-Path -LiteralPath (Join-Path $script:ProjectPath '.git') -PathType Container) {
        try {
            Add-Content -LiteralPath $script:SyncLogPath -Value $line -Encoding UTF8 -ErrorAction Stop
        }
        catch {
            Write-Warning ('LOG_WRITE_FAILED: ' + $_.Exception.Message)
        }
    }
}

function Invoke-ProjectGit {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [switch]$AllowFailure
    )
    $priorPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $lines = @(& $script:GitExecutable -C $script:ProjectPath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $priorPreference
    }
    $output = (($lines | ForEach-Object { [string]$_ }) -join "`n").Trim()
    if (($exitCode -ne 0) -and (-not $AllowFailure)) {
        throw ('GIT_COMMAND_FAILED code={0} command={1} details={2}' -f $exitCode, ($Arguments -join ' '), $output)
    }
    return [pscustomobject]@{ Code = [int]$exitCode; Output = [string]$output }
}

function Assert-ProjectPresent {
    if (-not (Test-Path -LiteralPath $script:ProjectPath -PathType Container)) {
        throw ('PROJECT_NOT_FOUND: ' + $script:ProjectPath)
    }
    $settingsGroovy = Join-Path $script:ProjectPath 'settings.gradle'
    $settingsKotlin = Join-Path $script:ProjectPath 'settings.gradle.kts'
    $appDirectory = Join-Path $script:ProjectPath 'app'
    if ((-not (Test-Path -LiteralPath $settingsGroovy -PathType Leaf)) -and
        (-not (Test-Path -LiteralPath $settingsKotlin -PathType Leaf))) {
        throw 'ANDROID_SETTINGS_NOT_FOUND: refusing to import an unexpected directory.'
    }
    if (-not (Test-Path -LiteralPath $appDirectory -PathType Container)) {
        throw 'ANDROID_APP_DIRECTORY_NOT_FOUND: refusing to import an unexpected directory.'
    }
    $gitCommand = Get-Command 'git.exe' -ErrorAction SilentlyContinue
    if ($null -eq $gitCommand) {
        throw 'GIT_NOT_INSTALLED: install Git for Windows; reopen PowerShell and rerun.'
    }
    $script:GitExecutable = $gitCommand.Source
}

function Ensure-SafeGitignore {
    $file = Join-Path $script:ProjectPath '.gitignore'
    $marker = '# >>> FreeLocalPlayer safe-sync rules >>>'
    $existingText = ''
    if (Test-Path -LiteralPath $file -PathType Leaf) {
        $existingText = [System.IO.File]::ReadAllText($file)
    }
    if ($existingText.Contains($marker)) {
        return
    }
    $block = @'
# >>> FreeLocalPlayer safe-sync rules >>>
# Android Studio, Gradle and transient outputs
.gradle/
.idea/
.kotlin/
.cxx/
.externalNativeBuild/
**/build/
/captures/
/out/
*.iml
*.log
# Machine-specific or confidential configuration
local.properties
**/local.properties
**/secrets.properties
**/google-services.json
**/GoogleService-Info.plist
**/.env
**/.env.*
**/id_rsa
**/id_ed25519
**/*.jks
**/*.keystore
**/*.p12
**/*.pfx
**/*.pem
**/*.key
**/*.asc
**/*.mobileprovision
# Binary builds, caches and source-transfer packages
*.apk
*.aab
*.apks
*.hprof
*.db
*.sqlite
*.zip
*.7z
*.rar
/.patch-backups/
/.backups/
/ready-to-install/
/backup/
/backups/
# <<< FreeLocalPlayer safe-sync rules <<<
'@
    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    $separator = if (($existingText.Length -gt 0) -and (-not $existingText.EndsWith("`n"))) { "`r`n" } else { '' }
    [System.IO.File]::WriteAllText($file, $existingText + $separator + $block + "`r`n", $utf8WithoutBom)
    Write-Event 'SAFE_GITIGNORE_RULES_INSTALLED=YES'
}

function Install-ProjectTools {
    $toolsDirectory = Split-Path -Path $script:InstalledScript -Parent
    if (-not (Test-Path -LiteralPath $toolsDirectory -PathType Container)) {
        New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
    }
    if (-not [string]::Equals([System.IO.Path]::GetFullPath($PSCommandPath),
            [System.IO.Path]::GetFullPath($script:InstalledScript),
            [System.StringComparison]::OrdinalIgnoreCase)) {
        Copy-Item -LiteralPath $PSCommandPath -Destination $script:InstalledScript -Force
    }
    $designDoc = Join-Path $PSScriptRoot 'FreeLocalPlayer-Modern-Fusion.md'
    if (Test-Path -LiteralPath $designDoc -PathType Leaf) {
        $docsDirectory = Join-Path $script:ProjectPath 'docs'
        if (-not (Test-Path -LiteralPath $docsDirectory -PathType Container)) {
            New-Item -ItemType Directory -Path $docsDirectory -Force | Out-Null
        }
        $targetDoc = Join-Path $docsDirectory 'MODERN_FUSION_DESIGN.md'
        if (-not (Test-Path -LiteralPath $targetDoc -PathType Leaf)) {
            Copy-Item -LiteralPath $designDoc -Destination $targetDoc
        }
    }
}

function Get-CurrentBranch {
    return (Invoke-ProjectGit -Arguments @('symbolic-ref', '--quiet', '--short', 'HEAD') -AllowFailure).Output
}

function Assert-MainBranch {
    $branch = Get-CurrentBranch
    if ($branch -ne 'main') {
        throw ('UNEXPECTED_BRANCH: ' + $branch + '. Use main; no branch will be rewritten automatically.')
    }
}

function Get-StagedPaths {
    $result = Invoke-ProjectGit -Arguments @('-c', 'core.quotepath=false', 'diff', '--cached', '--name-only', '--diff-filter=ACMR')
    if ([string]::IsNullOrWhiteSpace($result.Output)) { return @() }
    return @($result.Output -split "`n" | ForEach-Object { $_.TrimEnd("`r") } | Where-Object { $_.Length -gt 0 })
}

function Assert-StagedFilesSafe {
    $paths = @(Get-StagedPaths)
    $badPattern = '(?i)(^|/)(\.env(?:\..*)?|local\.properties|secrets\.properties|google-services\.json|GoogleService-Info\.plist|id_rsa|id_ed25519|[^/]*\.(?:jks|keystore|p12|pfx|pem|key|asc|apk|aab|apks|zip|7z|rar|db|sqlite))$'
    $secretPattern = '(?i)-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY-----|github_pat_[A-Za-z0-9_]{15,}|gh[pousr]_[A-Za-z0-9]{24,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{28,}'
    foreach ($path in $paths) {
        $gitPath = $path.Replace('\', '/')
        if ($gitPath -match $badPattern) {
            throw ('BLOCKED_SENSITIVE_OR_BINARY_PATH: ' + $gitPath)
        }
        $file = Join-Path $script:ProjectPath ($gitPath.Replace('/', '\'))
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { continue }
        $info = Get-Item -LiteralPath $file
        if ($info.Length -gt ($MaximumStagedFileMegabytes * 1MB)) {
            throw ('BLOCKED_LARGE_FILE: ' + $gitPath + ' exceeds configured max file size.')
        }
        if ($gitPath -match '(?i)\.(?:kt|java|kts|gradle|properties|xml|json|ya?ml|ps1|sh|md|txt|toml|ini|cfg)$') {
            if ($info.Length -le (2MB)) {
                $content = [System.IO.File]::ReadAllText($file)
                if ($content -match $secretPattern) {
                    throw ('BLOCKED_POSSIBLE_SECRET_IN_FILE: ' + $gitPath + ' (secret value not printed).')
                }
            }
        }
    }
    return $paths
}

function Test-RemoteIsCompatible {
    $remoteHeads = (Invoke-ProjectGit -Arguments @('ls-remote', '--heads', 'origin') ).Output
    $remoteMain = (Invoke-ProjectGit -Arguments @('ls-remote', '--heads', 'origin', 'main')).Output
    $localCommit = Invoke-ProjectGit -Arguments @('rev-parse', '--verify', 'HEAD') -AllowFailure
    if ([string]::IsNullOrWhiteSpace($remoteHeads)) {
        return
    }
    if ([string]::IsNullOrWhiteSpace($remoteMain)) {
        throw 'REMOTE_HAS_BRANCHES_BUT_NO_MAIN: refusing to overwrite or create an unrelated main.'
    }
    if ($localCommit.Code -ne 0) {
        throw 'REMOTE_HAS_HISTORY_LOCAL_EMPTY: refusing unrelated history.'
    }
    Invoke-ProjectGit -Arguments @('fetch', '--no-tags', 'origin', 'main') | Out-Null
    $comparison = Invoke-ProjectGit -Arguments @('merge-base', '--is-ancestor', 'refs/remotes/origin/main', 'HEAD') -AllowFailure
    if ($comparison.Code -eq 0) {
        return
    }
    $localIsBehind = Invoke-ProjectGit -Arguments @('merge-base', '--is-ancestor', 'HEAD', 'refs/remotes/origin/main') -AllowFailure
    if ($localIsBehind.Code -ne 0) {
        throw 'LOCAL_AND_REMOTE_DIVERGED: no rebase, reset or force-push was attempted.'
    }
    $workingChanges = (Invoke-ProjectGit -Arguments @('status', '--porcelain', '--untracked-files=normal')).Output
    if (-not [string]::IsNullOrWhiteSpace($workingChanges)) {
        throw 'REMOTE_AHEAD_WITH_LOCAL_EDITS: clean or commit local changes and resolve explicitly before pulling.'
    }
    Invoke-ProjectGit -Arguments @('merge', '--ff-only', 'refs/remotes/origin/main') | Out-Null
    Write-Event 'REMOTE_FAST_FORWARD=PASS: GitHub changes applied to local working directory.'
}

function Invoke-OneWaySync {
    param([switch]$Initial)
    Assert-MainBranch
    Test-RemoteIsCompatible

    if (-not $Initial) {
        # Avoid snapshots of files that are being saved right now. Deleted files have
        # no modification time and are deliberately not blocked by this check.
        $changedText = (Invoke-ProjectGit -Arguments @('-c', 'core.quotepath=false', 'ls-files', '--modified', '--others', '--exclude-standard')).Output
        if (-not [string]::IsNullOrWhiteSpace($changedText)) {
            $now = Get-Date
            foreach ($entry in ($changedText -split "`n")) {
                $path = $entry.TrimEnd("`r")
                if ([string]::IsNullOrWhiteSpace($path)) { continue }
                $file = Join-Path $script:ProjectPath ($path.Replace('/', '\'))
                if (Test-Path -LiteralPath $file -PathType Leaf) {
                    if ((($now - (Get-Item -LiteralPath $file).LastWriteTime).TotalSeconds) -lt 120) {
                        Write-Event 'SKIPPED_RECENT_WRITE: source is still changing; will retry on next scheduled run.'
                        return
                    }
                }
            }
        }
    }

    Invoke-ProjectGit -Arguments @('add', '-A') | Out-Null
    $staged = @(Assert-StagedFilesSafe)
    if ($staged.Count -gt 0) {
        $message = if ($Initial) {
            'chore: import existing FreeLocalPlayer Android sources'
        } else {
            'chore(sync): snapshot local FreeLocalPlayer changes'
        }
        Invoke-ProjectGit -Arguments @('commit', '-m', $message) | Out-Null
        Write-Event ('LOCAL_COMMIT_CREATED=YES FILES_CHANGED=' + $staged.Count)
    }
    else {
        Write-Event 'LOCAL_CHANGES=NONE'
    }

    # git push defaults to rejecting non-fast-forward updates; NEVER use --force.
    Invoke-ProjectGit -Arguments @('push', '--set-upstream', 'origin', 'main') | Out-Null
    $hash = (Invoke-ProjectGit -Arguments @('rev-parse', 'HEAD')).Output
    $remoteConfirmed = (Invoke-ProjectGit -Arguments @('ls-remote', '--heads', 'origin', 'main')).Output
    if (-not $remoteConfirmed.StartsWith($hash)) {
        throw 'PUSH_HASH_VERIFICATION_FAILED: remote main does not match local HEAD.'
    }
    Write-Event ('GITHUB_PUSH=PASS REMOTE_HASH_VERIFIED=YES HEAD=' + $hash)
}

function Install-SyncSchedule {
    $powershellExecutable = Join-Path $PSHOME 'powershell.exe'
    if (-not (Test-Path -LiteralPath $powershellExecutable -PathType Leaf)) {
        throw 'WINDOWS_POWERSHELL_NOT_FOUND: cannot create a reliable scheduled task.'
    }
    $command = ('"{0}" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "{1}" -Mode Sync' -f $powershellExecutable, $script:InstalledScript)
    $args = @('/Create', '/SC', 'MINUTE', '/MO', '5', '/TN', $script:TaskName,
              '/TR', $command, '/F', '/IT')
    $result = & schtasks.exe @args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw ('SCHEDULE_CREATION_FAILED: ' + (($result | Out-String).Trim()))
    }
    Write-Event 'AUTOSYNC_TASK=INSTALLED FREQUENCY=5_MINUTES WHILE_LOGGED_IN=YES'
}

function Install-ExistingProject {
    Assert-ProjectPresent
    Install-ProjectTools
    Ensure-SafeGitignore

    $gitPath = Join-Path $script:ProjectPath '.git'
    $existingRepository = Test-Path -LiteralPath $gitPath
    if (-not $existingRepository) {
        Invoke-ProjectGit -Arguments @('init', '--initial-branch=main') | Out-Null
        Write-Event 'GIT_REPOSITORY_INITIALIZED=YES'
    }
    $branch = Get-CurrentBranch
    if ($branch -ne 'main') {
        $commit = Invoke-ProjectGit -Arguments @('rev-parse', '--verify', 'HEAD') -AllowFailure
        if ($commit.Code -eq 0) {
            throw ('BRANCH_NOT_MAIN: ' + $branch + '; will not rename an existing branch with history.')
        }
        Invoke-ProjectGit -Arguments @('symbolic-ref', 'HEAD', 'refs/heads/main') | Out-Null
    }
    $origin = Invoke-ProjectGit -Arguments @('remote', 'get-url', 'origin') -AllowFailure
    if ($origin.Code -ne 0) {
        Invoke-ProjectGit -Arguments @('remote', 'add', 'origin', $RepositoryUrl) | Out-Null
        Write-Event ('REMOTE_CONFIGURED=' + $RepositoryUrl)
    }
    elseif (($origin.Output -ne $RepositoryUrl) -and ($origin.Output -ne 'git@github.com:OlegRakhimov/MyPlayer.git')) {
        throw ('ORIGIN_POINTS_ELSEWHERE: ' + $origin.Output + '. Will not replace existing origin.')
    }
    $remoteBeforePush = (Invoke-ProjectGit -Arguments @('ls-remote', '--heads', 'origin')).Output
    $commitBeforePush = Invoke-ProjectGit -Arguments @('rev-parse', '--verify', 'HEAD') -AllowFailure
    if ($existingRepository -and $commitBeforePush.Code -eq 0 -and
        [string]::IsNullOrWhiteSpace($remoteBeforePush) -and (-not $PublishExistingGitHistory)) {
        throw 'EXISTING_GIT_HISTORY_REVIEW_REQUIRED: the local repository has earlier commits. Review old history for secrets, then rerun Setup -PublishExistingGitHistory.'
    }
    $name = Invoke-ProjectGit -Arguments @('config', 'user.name') -AllowFailure
    $email = Invoke-ProjectGit -Arguments @('config', 'user.email') -AllowFailure
    if ([string]::IsNullOrWhiteSpace($name.Output) -or [string]::IsNullOrWhiteSpace($email.Output)) {
        throw 'GIT_AUTHOR_NOT_SET: configure your own git user.name and user.email, then rerun Setup.'
    }
    Write-Event 'PUBLIC_REPOSITORY_WARNING=CHECK_PUBLIC_SOURCE_FOR_PRIVATE_INFORMATION'
    Invoke-OneWaySync -Initial
    try {
        Install-SyncSchedule
    }
    catch {
        Write-Warning $_.Exception.Message
        Write-Event 'INITIAL_PUSH=PASS BUT_SCHEDULER=FAILED: run Sync manually until Task Scheduler is fixed.'
    }
    Write-Event 'SETUP_COMPLETED=YES'
}

function Invoke-AutomaticSync {
    Assert-ProjectPresent
    if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectPath '.git') -PathType Container)) {
        throw 'GIT_NOT_INITIALIZED: run Setup once first.'
    }
    # Atomic lock prevents overlapping Task Scheduler runs and manual Sync commands.
    $lockFile = Join-Path $script:ProjectPath '.git\freelocalplayer-sync.lock'
    $stream = $null
    try {
        $stream = [System.IO.File]::Open($lockFile, [System.IO.FileMode]::OpenOrCreate,
            [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    }
    catch [System.IO.IOException] {
        Write-Event 'SYNC_ALREADY_RUNNING=YES'
        return
    }
    try {
        Invoke-OneWaySync
    }
    finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Show-Status {
    Assert-ProjectPresent
    if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectPath '.git') -PathType Container)) {
        Write-Event 'GIT=NOT_INITIALIZED'
        return
    }
    Write-Event ('PROJECT=' + $script:ProjectPath)
    Write-Event ('BRANCH=' + (Get-CurrentBranch))
    $head = Invoke-ProjectGit -Arguments @('rev-parse', '--short', 'HEAD') -AllowFailure
    Write-Event ('LOCAL_HEAD=' + $head.Output)
    $status = Invoke-ProjectGit -Arguments @('status', '--short')
    if ([string]::IsNullOrEmpty($status.Output)) {
        Write-Event 'WORKTREE=CLEAN'
    }
    else {
        Write-Event ('WORKTREE=MODIFIED' + "`n" + $status.Output)
    }
    $remote = Invoke-ProjectGit -Arguments @('remote', 'get-url', 'origin') -AllowFailure
    Write-Event ('ORIGIN=' + $remote.Output)
    $taskInfo = & schtasks.exe /Query /TN $script:TaskName 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Event ('TASK=EXISTS' + "`n" + (($taskInfo | Out-String).Trim()))
    }
    else {
        Write-Event 'TASK=NOT_FOUND'
    }
    if (Test-Path -LiteralPath $script:SyncLogPath -PathType Leaf) {
        Write-Host '--- LAST 10 SYNC LOG LINES ---'
        Get-Content -LiteralPath $script:SyncLogPath -Tail 10
    }
}

function Export-SourceArchive {
    Assert-ProjectPresent
    if (-not (Test-Path -LiteralPath (Join-Path $script:ProjectPath '.git') -PathType Container)) {
        throw 'GIT_NOT_INITIALIZED: run Setup first.'
    }
    $commit = Invoke-ProjectGit -Arguments @('rev-parse', '--verify', 'HEAD') -AllowFailure
    if ($commit.Code -ne 0) { throw 'NO_GIT_COMMIT_TO_ARCHIVE' }
    $downloads = Join-Path $env:USERPROFILE 'Downloads'
    if (-not (Test-Path -LiteralPath $downloads -PathType Container)) {
        throw ('DOWNLOADS_DIRECTORY_NOT_FOUND: ' + $downloads)
    }
    $output = Join-Path $downloads ('FreeLocalPlayer-Source-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
    Invoke-ProjectGit -Arguments @('archive', '--format=zip', ('--output=' + $output), 'HEAD') | Out-Null
    Write-Event ('SOURCE_ARCHIVE_CREATED=' + $output)
    Write-Event ('SOURCE_ARCHIVE_SHA256=' + (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash)
}

try {
    switch ($Mode) {
        'Setup'          { Install-ExistingProject }
        'Sync'           { Invoke-AutomaticSync }
        'Status'         { Show-Status }
        'Archive'        { Export-SourceArchive }
        'RemoveSchedule' {
            $taskResult = & schtasks.exe /Delete /TN $script:TaskName /F 2>&1
            if ($LASTEXITCODE -ne 0) { throw ('TASK_REMOVAL_FAILED: ' + ($taskResult | Out-String)) }
            Write-Event 'AUTOSYNC_TASK=REMOVED'
        }
    }
}
catch {
    Write-Event ('RESULT=BLOCKED ERROR=' + $_.Exception.Message)
    exit 1
}
