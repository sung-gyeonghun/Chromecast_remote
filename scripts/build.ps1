$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskBuild = Join-Path $env:LOCALAPPDATA 'RemoconBuild'
Push-Location -LiteralPath $taskRoot
try {
    & .\gradlew.bat assembleDebug testDebugUnitTest lintDebug "-PremoconBuildDir=$($taskBuild.Replace('\','/'))" --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Android build or verification failed.' }
    New-Item -ItemType Directory -Path (Join-Path $taskRoot 'artifacts') -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $taskBuild 'app\outputs\apk\debug\app-debug.apk') -Destination (Join-Path $taskRoot 'artifacts\remocon-0.5.0.apk') -Force
    Get-FileHash -LiteralPath (Join-Path $taskRoot 'artifacts\remocon-0.5.0.apk') -Algorithm SHA256
} finally { Pop-Location }
