param([ValidateSet('Debug','Release')][string]$Configuration = 'Debug', [string]$Task = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
# ndk-build cannot handle spaces, so use a reversible drive mapping to this checkout.
$drive = 'B:'
if (Test-Path "$drive\") {
    $mapped = (& subst.exe | Where-Object { $_ -like 'B:*' })
    if (!$mapped -or !$mapped.EndsWith($root)) { throw 'B: is already used. Choose an unused build drive in this script.' }
} else { & subst.exe $drive $root; if ($LASTEXITCODE -ne 0) { throw 'Could not map build drive.' } }
$env:JAVA_HOME = (Get-ChildItem "$drive\.tools\java" -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = "$drive\.tools\android"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "$drive\.tools\gradle-cache"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
if (!$Task) { $Task = ":app:assembleNonRoot$Configuration" }
Push-Location "$drive\src\android"
try {
    & .\gradlew.bat $Task --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Android $Task failed." }
} finally { Pop-Location }
