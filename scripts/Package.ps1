param([switch]$StageOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
function Assert-WorkspacePath([string]$Path) { $absolute=[IO.Path]::GetFullPath($Path);if(!$absolute.StartsWith([IO.Path]::GetFullPath($root)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Package path must remain inside the workspace.'};return $absolute }
$buildId = Get-Date -Format 'yyyyMMdd-HHmmss'
$candidate = Join-Path $root "artifacts/releases/$buildId"
$candidate=Assert-WorkspacePath $candidate
New-Item -ItemType Directory -Force $candidate | Out-Null
. "$PSScriptRoot/Signing.ps1"
try {
    dotnet publish "$root/src/windows/BedMirror.Windows.csproj" -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -o $candidate --nologo
    if ($LASTEXITCODE -ne 0) { throw 'Windows release publication failed.' }
    & "$PSScriptRoot/Android-Build.ps1" -Configuration Release
    $apk = Join-Path $root 'src/android/app/build/outputs/apk/nonRoot/release/app-nonRoot-release.apk'
    if (!(Test-Path $apk)) { throw 'Gradle did not produce the signed release APK.' }
    & "$root/.tools/android/build-tools/36.0.0/apksigner.bat" verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
    Copy-Item -LiteralPath $apk -Destination "$candidate/NyxLink-Android.apk"
    Copy-Item -LiteralPath "$root/src/windows/Assets/NyxLink.ico" -Destination "$candidate/NyxLink.ico"
    Copy-Item -LiteralPath "$root/vendor/sunshine/Sunshine" -Destination "$candidate/sunshine" -Recurse
    Copy-Item -LiteralPath "$root/README.md","$root/TEST-REPORT.md","$root/dependencies.lock.json" -Destination $candidate
    Copy-Item -LiteralPath "$PSScriptRoot/Configure-Firewall.ps1" -Destination $candidate
    New-Item -ItemType Directory "$candidate/licenses" | Out-Null
    Copy-Item -LiteralPath "$root/src/android/LICENSE.txt" -Destination "$candidate/licenses/Moonlight-LICENSE.txt"
    Copy-Item -LiteralPath "$root/vendor/sunshine-source/LICENSE" -Destination "$candidate/licenses/Sunshine-LICENSE"
    Copy-Item -LiteralPath "$root/THIRD-PARTY-NOTICES.md" -Destination "$candidate/licenses/THIRD-PARTY-NOTICES.md"
    if(Test-Path 'C:/Program Files/dotnet/LICENSE.txt'){Copy-Item -LiteralPath 'C:/Program Files/dotnet/LICENSE.txt' -Destination "$candidate/licenses/DotNet-LICENSE.txt"}
    if(Test-Path 'C:/Program Files/dotnet/ThirdPartyNotices.txt'){Copy-Item -LiteralPath 'C:/Program Files/dotnet/ThirdPartyNotices.txt' -Destination "$candidate/licenses/DotNet-ThirdPartyNotices.txt"}
    # The source archive excludes all local keys, credentials, SDKs, caches, and test-device data.
    $sourceFiles = @('README.md','TEST-REPORT.md','THIRD-PARTY-NOTICES.md','dependencies.lock.json','.gitignore')
    $sourceFiles += Get-ChildItem -LiteralPath "$root/src","$root/tests","$root/scripts","$root/vendor/sunshine-source" -Recurse -File -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\(build|bin|obj|\.git|\.gradle|\.cxx)\\' -and $_.Name -notin @('.git','local.properties') } |
        ForEach-Object { $_.FullName.Substring($root.Length+1) }
    $list = Join-Path $root 'artifacts/source-files.txt'
    $sourceFiles | Set-Content -LiteralPath $list
    Push-Location $root
    try { tar.exe -a -cf "$candidate/NyxLink-Source.zip" -T $list; if ($LASTEXITCODE -ne 0) { throw 'Source archive failed.' } }
    finally { Pop-Location }
    Push-Location $candidate
    try { tar.exe -a -cf NyxLink-Windows.zip NyxLink-Windows.exe NyxLink.ico sunshine licenses README.md TEST-REPORT.md dependencies.lock.json Configure-Firewall.ps1;if($LASTEXITCODE -ne 0){throw 'Windows ZIP creation failed.'} }
    finally { Pop-Location }
    @{windowsPublishSucceeded=$true;gradleReleaseSucceeded=$true;apkSignatureVerified=$true;builtAtUtc=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content "$candidate/RELEASE-VERIFICATION.json"
    $files=Get-ChildItem -LiteralPath $candidate -Recurse -File
    $files | ForEach-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLower()+'  '+$_.FullName.Substring($candidate.Length+1).Replace('\','/') } | Set-Content "$candidate/SHA256SUMS.txt"
    if ($StageOnly) { Write-Output "Verified package staged at $candidate"; return }
    # Promote only a complete candidate. Keep any previous distribution recoverable.
    $distribution=Assert-WorkspacePath "$root/dist"
    $previous=Assert-WorkspacePath "$root/artifacts/releases/previous-$buildId"
    if (Test-Path $distribution) { Move-Item -LiteralPath $distribution -Destination $previous }
    Move-Item -LiteralPath $candidate -Destination $distribution
    Write-Output 'Verified release candidate promoted to dist.'
} finally { Remove-Item Env:BEDMIRROR_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
